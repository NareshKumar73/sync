package com.source.open.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.source.open.payload.FileListJson;
import com.source.open.payload.FileMeta;
import com.source.open.payload.FileRequest;
import com.source.open.util.FileService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
public class FileController {

	private final FileService fs;
	private final com.source.open.util.StorageService storageService;
	private final com.source.open.util.TransferHistoryRepository transferHistoryRepo;

	@GetMapping("/files")
	public ResponseEntity<FileListJson> fileList() {

		List<FileMeta> metaList = fs.listDirectory(null);

		return ResponseEntity.ok(new FileListJson(metaList, metaList.size()));
	}

	@GetMapping("/files/recursive")
	public ResponseEntity<FileListJson> recursiveFileList() {
		List<FileMeta> metaList = fs.listDirectoryRecursive();
		return ResponseEntity.ok(new FileListJson(metaList, metaList.size()));
	}

	@GetMapping("/files/refresh")
	public ResponseEntity<FileListJson> fetchFilesFromFileSystem() {

		List<FileMeta> list = fs.refreshFileList();

		return ResponseEntity.ok(new FileListJson(list, list.size()));
	}

	@PostMapping(value = "/zip-stream", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<StreamingResponseBody> getZippedStream(FileRequest fileRequest, HttpServletRequest request,
			HttpServletResponse response) throws FileNotFoundException {

		System.out.println(fileRequest);

		List<String> files = fileRequest.getFilecode();

		if (files.isEmpty()) {
			log.debug("Empty filecode list for Zip file download of multiple files");
			response.setStatus(204);
			return null;
		}

		LinkedHashMap<String, FileMeta> localFiles = fs.getLocalFiles();

		StreamingResponseBody stream = client -> {

			ZipOutputStream zipOut = new ZipOutputStream(client);

			try {
				for (String filecode : files) {
					FileMeta fm = localFiles.get(filecode);
					if (fm != null) {
						zipOut.putNextEntry(new ZipEntry(fm.getName()));
						Files.copy(fm.getPath(), zipOut);
					}
				}
				zipOut.finish();
			} catch (Exception e) {
				log.error(e);
				response.setStatus(204);
			}
		};

		String filename = "custom_" + LocalDateTime.now() + ".zip";

		HttpHeaders headers = new HttpHeaders();

		headers.set(HttpHeaders.ACCEPT_RANGES, "none");

		headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + filename);
		headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);

		log.debug("Client " + request.getRemoteAddr() + " is trying to download: " + filename);

		return ResponseEntity.ok().headers(headers).body(stream);
	}

	@GetMapping("/zip-folder")
	public void downloadFolderZip(@RequestParam String path, HttpServletResponse response) throws IOException {

		Path folder = fs.getAppDir().resolve(path).normalize();

		if (!folder.startsWith(fs.getAppDir())) {
			response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid path");
			return;
		}

		response.setContentType("application/zip");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + folder.getFileName() + ".zip\"");

		try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {

			Files.walk(folder).filter(Files::isRegularFile).forEach(file -> {
				ZipEntry entry = new ZipEntry(folder.relativize(file).toString());
				try {
					zos.putNextEntry(entry);
					Files.copy(file, zos);
					zos.closeEntry();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
		}
	}

	// TODO Add Zip Stream of Folder using filecode and generate filecode using
	// relative path from appDir

	// file upload endpoint - support both multiple and single file upload
	@PostMapping("/upload")
	public ResponseEntity<Map<String, Object>> uploadMultipleFiles(@RequestPart("file") List<MultipartFile> parts,
			HttpServletRequest request) {
		log.debug("File upload request arrived");

		List<String> uploaded = new ArrayList<>();
		List<String> failed = new ArrayList<>();

		for (MultipartFile file : parts) {
			String originalFilename = file.getOriginalFilename();
			if (originalFilename == null || originalFilename.contains("..") || originalFilename.contains("/")
					|| originalFilename.contains("\\")) {
				log.warn("Rejected upload with unsafe filename: {}", originalFilename);
				failed.add(originalFilename);
				continue;
			}
			try {
				Path newFile = fs.getAppDir().resolve(originalFilename);
				try (InputStream in = file.getInputStream()) {
					Files.copy(in, newFile, StandardCopyOption.REPLACE_EXISTING);
				}
				logTransfer("MANUAL_UPLOAD", originalFilename, request.getRemoteAddr(), file.getSize());
				uploaded.add(originalFilename);
			} catch (Exception e) {
				log.error("Failed to upload file: {}", originalFilename, e);
				failed.add(originalFilename);
			}
		}

		Map<String, Object> body = Map.of("uploaded", uploaded, "failed", failed);
		return failed.isEmpty() ? ResponseEntity.ok(body) : ResponseEntity.status(HttpStatus.MULTI_STATUS).body(body);
	}

	@PostMapping("/upload/chunk")
	public ResponseEntity<?> uploadChunk(
			@RequestParam("file") MultipartFile file,
			@RequestParam("filename") String filename,
			@RequestParam("relativePath") String relativePath,
			@RequestParam("chunkIndex") int chunkIndex,
			@RequestParam("totalChunks") int totalChunks,
			@RequestParam("uuid") String uuid,
			HttpServletRequest request) {

		try {
			// Resolve target directory
			Path targetDir = fs.getAppDir().resolve(relativePath).normalize();
			if (!targetDir.startsWith(fs.getAppDir())) {
				return ResponseEntity.badRequest().body("Invalid path");
			}
			if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
				return ResponseEntity.badRequest().body("Invalid filename");
			}
			Files.createDirectories(targetDir);

			// Use a dedicated temp directory for the chunks of this specific file
			Path tempDir = targetDir.resolve(".temp_" + uuid);
			Files.createDirectories(tempDir);
			Path chunkFile = tempDir.resolve(String.valueOf(chunkIndex));

			// Save chunk (overwrite if exists, enabling resuming)
			Files.copy(file.getInputStream(), chunkFile, StandardCopyOption.REPLACE_EXISTING);

			// Check if all chunks are received
			boolean allChunksPresent = true;
			for (int i = 0; i < totalChunks; i++) {
				if (!Files.exists(tempDir.resolve(String.valueOf(i)))) {
					allChunksPresent = false;
					break;
				}
			}

			if (allChunksPresent) {
				// Synchronize on the unique file UUID to prevent concurrent merges
				synchronized (uuid.intern()) {
					Path targetFile = targetDir.resolve(filename);
					if (!Files.exists(targetFile) && Files.exists(tempDir)) {
						try (OutputStream out = Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
							for (int i = 0; i < totalChunks; i++) {
								Path cFile = tempDir.resolve(String.valueOf(i));
								Files.copy(cFile, out);
							}
						}
						// Clean up temp directory
						try {
							for (int i = 0; i < totalChunks; i++) {
								Files.deleteIfExists(tempDir.resolve(String.valueOf(i)));
							}
							Files.deleteIfExists(tempDir);
						} catch (Exception ignored) {}
					}
				}
				storageService.releaseSpace(uuid);
				log.info("Upload completed for file: {}", filename);
				try {
					long size = Files.size(targetDir.resolve(filename));
					logTransfer("MANUAL_UPLOAD", filename, request.getRemoteAddr(), size);
				} catch (Exception e) {}
				return ResponseEntity.ok().body(Map.of("message", "Upload complete", "completed", true));
			}

			int percentage = (int) (((double) (chunkIndex + 1) / totalChunks) * 100);
			log.info("Upload in progress for file: {} - {}%", filename, percentage);
			return ResponseEntity.ok().body(Map.of("message", "Chunk received", "completed", false));
		} catch (IOException e) {
			log.error("Error uploading chunk", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error uploading chunk");
		}
	}

	/**
	 * Plain download: matches when there's no Range header. Zero-copy full-file
	 * transfer via Resource, with ETag/Last-Modified 304 caching handled by Spring.
	 */
	@GetMapping("/download")
	public ResponseEntity<Resource> download(@RequestParam(required = false) String filecode,
			ServletWebRequest request) {

		FileMeta fm = lookupFile(filecode);
		Path path = fm.getPath();

		String eTag = generateETag(fm);
		long lastModified = fm.getLastModifiedEpoch();

		if (request.checkNotModified(eTag, lastModified)) {
			return null;
		}

		MediaType mime = MediaTypeFactory.getMediaType(fm.getName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
		ContentDisposition contentDisposition = ContentDisposition.attachment().filename(fm.getName()).build();

		logTransfer("MANUAL_DOWNLOAD", fm.getName(), request.getRequest().getRemoteAddr(), fm.getSizeInBytes());

		return ResponseEntity.ok().eTag(eTag).lastModified(lastModified).contentType(mime)
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
				.header(HttpHeaders.ACCEPT_RANGES, "bytes")
				.body(new FileSystemResource(path));
	}

	/**
	 * Range download: matches whenever a Range header is sent. Backed by Spring's
	 * built-in ResourceRegion support (HttpRange.toResourceRegions), which handles
	 * single-range, multi-range (multipart/byteranges), and If-Range validation
	 * through the ResourceRegionHttpMessageConverter instead of hand-rolled
	 * FileChannel streaming.
	 */
	@GetMapping(value = "/download", headers = HttpHeaders.RANGE)
	public ResponseEntity<List<ResourceRegion>> downloadRange(@RequestParam(required = false) String filecode,
			@RequestHeader(HttpHeaders.RANGE) String rangeHeader,
			@RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRangeHeader,
			HttpServletRequest request) {

		FileMeta fm = lookupFile(filecode);
		Path path = fm.getPath();
		long fileSize = fm.getSizeInBytes();

		String eTag = generateETag(fm);
		MediaType mime = MediaTypeFactory.getMediaType(fm.getName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
		ContentDisposition contentDisposition = ContentDisposition.attachment().filename(fm.getName()).build();
		Resource resource = new FileSystemResource(path);

		boolean honorRange = ifRangeHeader == null || ifRangeHeader.equals(eTag);

		List<ResourceRegion> regions;
		HttpStatus status;
		if (honorRange) {
			List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
			regions = HttpRange.toResourceRegions(ranges, resource);
			status = HttpStatus.PARTIAL_CONTENT;
		} else {
			// If-Range didn't match the current ETag: send the whole file instead.
			regions = List.of(new ResourceRegion(resource, 0, fileSize));
			status = HttpStatus.OK;
		}

		long bytesServed = regions.stream().mapToLong(ResourceRegion::getCount).sum();
		logTransfer("MANUAL_DOWNLOAD", fm.getName(), request.getRemoteAddr(), bytesServed);

		return ResponseEntity.status(status).eTag(eTag).contentType(mime)
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
				.body(regions);
	}

	private FileMeta lookupFile(String filecode) {
		if (filecode == null || filecode.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filecode is required.");

		FileMeta fm = fs.getLocalFiles().get(filecode);
		if (fm == null)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File metadata not found.");

		if (fm.getPath() == null || !Files.exists(fm.getPath()))
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Physical file missing.");

		return fm;
	}

	private String generateETag(FileMeta fm) {
		String value = fm.getSizeInBytes() + "-" + fm.getLastModifiedEpoch();
		return "\"" + Integer.toHexString(value.hashCode()) + "\"";
	}

	private void logTransfer(String type, String filename, String ipAddress, long size) {
		try {
			Optional<com.source.open.payload.TransferHistory> recentOpt = transferHistoryRepo.findFirstByIpAddressAndFilenameAndTypeOrderByTimestampDesc(ipAddress, filename, type);

			if (recentOpt.isPresent()) {
				com.source.open.payload.TransferHistory recent = recentOpt.get();
				// If the transfer happened within the last 1 hour, accumulate size
				if (recent.getTimestamp().isAfter(LocalDateTime.now().minusHours(1))) {
					recent.setFileSize(recent.getFileSize() + size);
					recent.setTimestamp(LocalDateTime.now());
					transferHistoryRepo.save(recent);
					return;
				}
			}

			com.source.open.payload.TransferHistory th = new com.source.open.payload.TransferHistory();
			th.setType(type);
			th.setFilename(filename);
			th.setIpAddress(ipAddress);
			th.setFileSize(size);
			th.setTimestamp(LocalDateTime.now());
			transferHistoryRepo.save(th);
		} catch (Exception e) {
			log.error("Failed to log transfer history", e);
		}
	}

}
