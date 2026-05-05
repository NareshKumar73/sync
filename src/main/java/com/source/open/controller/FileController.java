package com.source.open.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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

import com.source.open.exception.ResourceNotFoundException;
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

	@GetMapping("/resource")
	public ResponseEntity<Resource> get(HttpServletRequest request,
			@RequestParam(required = false) Optional<String> filecode) throws FileNotFoundException {

		if (filecode.isEmpty())
			throw new FileNotFoundException("Please provide a valid filecode to download the file.");

		FileMeta fileMeta = fs.getLocalFiles().get(filecode.get());

		if (fileMeta == null)
			throw new ResourceNotFoundException("The file you want to download is not available on server.");

		Path p = fileMeta.getPath();

		log.debug("Client {} is trying to download: {}", request.getRemoteAddr(), filecode.get());

		if (p == null)
			throw new FileNotFoundException("Please check directory and confirm the file exists.");

		FileSystemResource resource = new FileSystemResource(p);

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE).body(resource);
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
	public void uploadMultipleFiles(@RequestPart("file") List<MultipartFile> parts) {
		log.debug("File upload request arrived");

		for (MultipartFile file : parts) {
			try {
				Path newFile = fs.getAppDir().resolve(file.getOriginalFilename());
				try (InputStream in = file.getInputStream()) {
					Files.copy(in, newFile, StandardCopyOption.REPLACE_EXISTING);
				} catch (Exception e) {
					e.printStackTrace();
				}
			} catch (InvalidPathException e) {
				e.printStackTrace();
			}
		}

	}

	@PostMapping("/upload/chunk")
	public ResponseEntity<?> uploadChunk(
			@RequestParam("file") MultipartFile file,
			@RequestParam("filename") String filename,
			@RequestParam("relativePath") String relativePath,
			@RequestParam("chunkIndex") int chunkIndex,
			@RequestParam("totalChunks") int totalChunks,
			@RequestParam("uuid") String uuid) {

		try {
			// Resolve target directory
			Path targetDir = fs.getAppDir().resolve(relativePath).normalize();
			if (!targetDir.startsWith(fs.getAppDir())) {
				return ResponseEntity.badRequest().body("Invalid path");
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
				log.info("Upload completed for file: {}", filename);
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

	// NEW CODE STARTS HERE

	// @GetMapping("/download")
	// public ResponseEntity<?> download(HttpServletRequest request,
	// @RequestParam(required = false) Optional<String> filecode) throws Exception {
	//
	// if (filecode.isEmpty())
	// throw new FileNotFoundException("Please provide a valid filecode to download
	// the file.");
	//
	// FileMeta fm = fs.getLocalFiles().get(filecode.get());
	//
	// if (fm == null)
	// throw new ResourceNotFoundException("The file you want to download is not
	// available on server.");
	//
	// Path p = fm.getPath();
	//
	// String size = String.valueOf(fm.getSizeInBytes());
	//
	// log.debug("Client " + request.getRemoteAddr() + " is trying to download: " +
	// filecode.get());
	//
	// if (p == null)
	// throw new FileNotFoundException("Please check directory and confirm the file
	// exists.");
	//
	// MediaType mime =
	// MediaTypeFactory.getMediaType(fm.getName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
	//
	// String ifRangeHeader = request.getHeader("If-Range");
	// String rangeHeader = request.getHeader("Range");
	//
	// String eTag = generateETag(fm);
	//
	// if (ifRangeHeader != null && !ifRangeHeader.equals(eTag)) {
	// // If-Range doesn't match => send full file
	// return ResponseEntity.ok().eTag(eTag).header(HttpHeaders.CONTENT_LENGTH,
	// size)
	// .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" +
	// fm.getName() + "\"")
	// .header(HttpHeaders.CONTENT_TYPE, mime.toString()).body(new
	// UrlResource(p.toUri()));
	// }
	//
	// if (rangeHeader == null) {
	// // No Range => full file
	// return ResponseEntity.ok().eTag(eTag).header(HttpHeaders.CONTENT_LENGTH,
	// size)
	// .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" +
	// fm.getName() + "\"")
	// .header(HttpHeaders.CONTENT_TYPE, mime.toString()).body(new
	// UrlResource(p.toUri()));
	// }
	//
	// List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
	// if (ranges.isEmpty()) {
	// return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
	// .header(HttpHeaders.CONTENT_RANGE, "bytes */" +
	// fm.getSizeInBytes()).eTag(eTag).build();
	// }
	//
	// if (ranges.size() == 1) {
	// // Single range
	// HttpRange r = ranges.get(0);
	// long start = r.getRangeStart(fm.getSizeInBytes());
	// long end = r.getRangeEnd(fm.getSizeInBytes());
	// long len = end - start + 1;
	//
	// InputStream is = Files.newInputStream(p);
	// is.skip(start);
	// InputStream limited = new RangeInputStream(is, len);
	//// InputStream limited = new ThrottledInputStream(is, len, 1024 * 100); // 100
	/// KB/s throttle

	//
	// return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).eTag(eTag)
	// .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(len))
	// .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start,
	// end, fm.getSizeInBytes()))
	// .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" +
	// fm.getName() + "\"")
	// .header(HttpHeaders.CONTENT_TYPE, mime.toString()).body(new
	// InputStreamResource(limited));
	// }
	//
	// // Multi-range
	// String boundary = UUID.randomUUID().toString();
	//
	// StreamingResponseBody responseBody = outputStream -> {
	// for (HttpRange r : ranges) {
	// long start = r.getRangeStart(fm.getSizeInBytes());
	// long end = r.getRangeEnd(fm.getSizeInBytes());
	// long len = end - start + 1;
	//
	// outputStream.write(("--" + boundary + "\r\n").getBytes());
	// outputStream.write(("Content-Type: " + mime + "\r\n").getBytes());
	// outputStream.write(
	// ("Content-Range: bytes " + start + "-" + end + "/" + fm.getSizeInBytes() +
	// "\r\n").getBytes());
	// outputStream.write(("\r\n").getBytes());
	//
	// try (InputStream is = Files.newInputStream(p)) {
	// is.skip(start);
	// InputStream limited = new RangeInputStream(is, len);
	// copy(limited, outputStream);
	//// InputStream throttled = new ThrottledInputStream(is, len, 1024 * 100L); /
	/// copy(throttled, outputStream); copyRange(is, outputStream, len);
	// }
	// outputStream.write(("\r\n").getBytes());
	// }
	// outputStream.write(("--" + boundary + "--\r\n").getBytes());
	// };
	//
	// return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).eTag(eTag)
	// .header(HttpHeaders.CONTENT_TYPE, "multipart/byteranges; boundary=" +
	// boundary).body(responseBody);
	// }

	@GetMapping("/download")
	public ResponseEntity<Resource> download(@RequestParam(required = false) String filecode,
			ServletWebRequest request) { // Native helper for ETag/Last-Modified

		// 1. Validation using Modern Java checks
		if (filecode == null || filecode.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filecode is required.");
		}

		var fm = fs.getLocalFiles().get(filecode);
		if (fm == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File metadata not found.");
		}

		var path = fm.getPath();
		if (!Files.exists(path)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Physical file missing.");
		}

		// 2. Efficient caching (ETag & Last-Modified)
		// ServletWebRequest handles the "If-None-Match" / 304 Not Modified logic
		// automatically
		String eTag = generateETag(fm);
		long lastModified = fm.getLastModifiedEpoch();

		if (request.checkNotModified(eTag, lastModified)) {
			return null; // Spring automatically sends 304 response
		}

		// 3. Return Resource for OS-level Zero-Copy (sendfile)
		Resource resource = new FileSystemResource(path);

		// Modern Content-Disposition Builder
		var contentDisposition = ContentDisposition.attachment().filename(fm.getName()).build();

		return ResponseEntity.ok()
				.contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString()).lastModified(lastModified)
				.eTag(eTag).body(resource);
	}

	@GetMapping("/download/manual")
	public ResponseEntity<StreamingResponseBody> downloadCustom(@RequestParam(required = false) String filecode,
			@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
			@RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRangeHeader) {

		if (filecode == null || filecode.isBlank())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filecode required");

		var fm = fs.getLocalFiles().get(filecode);
		if (fm == null)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);

		var path = fm.getPath();
		long fileSize = fm.getSizeInBytes();
		String eTag = generateETag(fm);
		var mime = MediaTypeFactory.getMediaType(fm.getName()).orElse(MediaType.APPLICATION_OCTET_STREAM);

		// ETag Validation (If-Range logic)
		boolean validRange = rangeHeader != null && (ifRangeHeader == null || ifRangeHeader.equals(eTag));
		List<HttpRange> ranges = validRange ? HttpRange.parseRanges(rangeHeader) : List.of();

		// SCENARIO 1: Full File (No Range or Invalid Range)
		if (ranges.isEmpty()) {
			return ResponseEntity.ok().eTag(eTag).contentLength(fileSize).contentType(mime)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fm.getName() + "\"")
					.body(os -> {
						// Java 25 / OS Optimization: Use transferTo for cleaner copying
						try (var fileChannel = FileChannel.open(path, StandardOpenOption.READ);
								var outChannel = Channels.newChannel(os)) {
							fileChannel.transferTo(0, fileSize, outChannel);
						}
					});
		}

		// SCENARIO 2: Single Range
		if (ranges.size() == 1) {
			HttpRange r = ranges.get(0);
			long start = r.getRangeStart(fileSize);
			long end = r.getRangeEnd(fileSize);
			long length = end - start + 1;

			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).eTag(eTag).contentType(mime)
					.header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
					.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(length))
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fm.getName() + "\"")
					.body(os -> streamRange(path, start, length, os));
		}

		// SCENARIO 3: Multi-Range (Multipart)
		String boundary = UUID.randomUUID().toString();

		return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).eTag(eTag)
				.contentType(MediaType.parseMediaType("multipart/byteranges; boundary=" + boundary)).body(os -> {
					for (HttpRange r : ranges) {
						long start = r.getRangeStart(fileSize);
						long end = r.getRangeEnd(fileSize);
						long length = end - start + 1;

						// Write Boundary Headers
						String partHeader = String.format(
								"--%s\r\nContent-Type: %s\r\nContent-Range: bytes %d-%d/%d\r\n\r\n", boundary, mime,
								start, end, fileSize);
						os.write(partHeader.getBytes());

						// Stream the chunk
						streamRange(path, start, length, os);

						os.write("\r\n".getBytes());
					}
					os.write(("--" + boundary + "--\r\n").getBytes());
				});
	}

	/**
	 * Helper to stream efficient file chunks
	 */
	private void streamRange(java.nio.file.Path path, long start, long length, OutputStream os) throws IOException {
		// Java 21+ try-with-resources
		try (var fileChannel = FileChannel.open(path, StandardOpenOption.READ)) {
			// Fast seek using Channel (OS level seek) rather than InputStream.skip (Loop
			// scan)
			fileChannel.position(start);

			// Use a larger buffer for Ubuntu/Server environments (e.g., 32KB or 64KB)
			var buffer = java.nio.ByteBuffer.allocate(64 * 1024);
			long remaining = length;

			var outChannel = Channels.newChannel(os);

			while (remaining > 0) {
				// Cap the read at the remaining length
				if (remaining < buffer.capacity()) {
					buffer.limit((int) remaining);
				}

				int read = fileChannel.read(buffer);
				if (read == -1)
					break;

				buffer.flip(); // Switch to read mode
				outChannel.write(buffer);
				buffer.clear(); // Switch back to write mode

				remaining -= read;
			}
		}
	}

	private String generateETag(FileMeta fm) {
		String value = fm.getSizeInBytes() + "-" + fm.getLastModifiedEpoch();
		return "\"" + Integer.toHexString(value.hashCode()) + "\"";
	}

	// private void copy(InputStream in, OutputStream out) throws IOException {
	// byte[] buffer = new byte[8192];
	// int read;
	// while ((read = in.read(buffer)) != -1) {
	// out.write(buffer, 0, read);
	// out.flush();
	// }
	// }

}
