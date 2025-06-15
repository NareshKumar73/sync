package com.source.open.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.source.open.exception.ResourceNotFoundException;
import com.source.open.payload.FileListJson;
import com.source.open.payload.FileMeta;
import com.source.open.payload.FileRequest;
import com.source.open.payload.RangeInputStream;
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

		List<FileMeta> metaList = fs.getLocalFilesList();

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

	// file upload endpoint - support both multiple and single file upload
	@PostMapping("/upload")
	public void uploadMultipleFiles(@RequestPart("file") List<MultipartFile> parts) {
		log.debug("File upload request arrived");

		for (MultipartFile file : parts) {
			try {
//				Files.copy(file.getInputStream(), fs.getSyncDir().resolve(file.getOriginalFilename()), StandardCopyOption.REPLACE_EXISTING);
				Path newFile = fs.getSyncDir().resolve(file.getOriginalFilename());
				Files.deleteIfExists(newFile);
				file.transferTo(newFile);
			} catch (IllegalStateException | IOException e) {
				e.printStackTrace();
			}
		}

	}

//	NEW CODE STARTS HERE

	@GetMapping("/download")
	public ResponseEntity<?> download(HttpServletRequest request,
			@RequestParam(required = false) Optional<String> filecode) throws Exception {

		if (filecode.isEmpty())
			throw new FileNotFoundException("Please provide a valid filecode to download the file.");

		FileMeta fm = fs.getLocalFiles().get(filecode.get());

		if (fm == null)
			throw new ResourceNotFoundException("The file you want to download is not available on server.");

		Path p = fm.getPath();

		String size = String.valueOf(fm.getSizeInBytes());

		log.debug("Client " + request.getRemoteAddr() + " is trying to download: " + filecode.get());

		if (p == null)
			throw new FileNotFoundException("Please check directory and confirm the file exists.");

		MediaType mime = MediaTypeFactory.getMediaType(fm.getName()).orElse(MediaType.APPLICATION_OCTET_STREAM);

		String ifRangeHeader = request.getHeader("If-Range");
		String rangeHeader = request.getHeader("Range");

		String eTag = generateETag(fm);

		if (ifRangeHeader != null && !ifRangeHeader.equals(eTag)) {
			// If-Range doesn't match => send full file
			return ResponseEntity.ok().eTag(eTag).header(HttpHeaders.CONTENT_LENGTH, size)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fm.getName() + "\"")
					.header(HttpHeaders.CONTENT_TYPE, mime.toString()).body(new UrlResource(p.toUri()));
		}

		if (rangeHeader == null) {
			// No Range => full file
			return ResponseEntity.ok().eTag(eTag).header(HttpHeaders.CONTENT_LENGTH, size)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fm.getName() + "\"")
					.header(HttpHeaders.CONTENT_TYPE, mime.toString()).body(new UrlResource(p.toUri()));
		}

		List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
		if (ranges.isEmpty()) {
			return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
					.header(HttpHeaders.CONTENT_RANGE, "bytes */" + fm.getSizeInBytes()).eTag(eTag).build();
		}

		if (ranges.size() == 1) {
			// Single range
			HttpRange r = ranges.get(0);
			long start = r.getRangeStart(fm.getSizeInBytes());
			long end = r.getRangeEnd(fm.getSizeInBytes());
			long len = end - start + 1;

			InputStream is = Files.newInputStream(p);
			is.skip(start);
			InputStream limited = new RangeInputStream(is, len);
//			InputStream limited = new ThrottledInputStream(is, len, 1024 * 100); // 100 KB/s throttle

			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).eTag(eTag)
					.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(len))
					.header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fm.getSizeInBytes()))
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fm.getName() + "\"")
					.header(HttpHeaders.CONTENT_TYPE, mime.toString()).body(new InputStreamResource(limited));
		}

		// Multi-range
		String boundary = UUID.randomUUID().toString();

		StreamingResponseBody responseBody = outputStream -> {
			for (HttpRange r : ranges) {
				long start = r.getRangeStart(fm.getSizeInBytes());
				long end = r.getRangeEnd(fm.getSizeInBytes());
				long len = end - start + 1;

				outputStream.write(("--" + boundary + "\r\n").getBytes());
				outputStream.write(("Content-Type: " + mime + "\r\n").getBytes());
				outputStream.write(
						("Content-Range: bytes " + start + "-" + end + "/" + fm.getSizeInBytes() + "\r\n").getBytes());
				outputStream.write(("\r\n").getBytes());

				try (InputStream is = Files.newInputStream(p)) {
					is.skip(start);
					InputStream limited = new RangeInputStream(is, len);
					copy(limited, outputStream);
//					InputStream throttled = new ThrottledInputStream(is, len, 1024 * 100L);
//					copy(throttled, outputStream);
//					copyRange(is, outputStream, len);
				}
				outputStream.write(("\r\n").getBytes());
			}
			outputStream.write(("--" + boundary + "--\r\n").getBytes());
		};

		return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).eTag(eTag)
				.header(HttpHeaders.CONTENT_TYPE, "multipart/byteranges; boundary=" + boundary).body(responseBody);
	}

	private String generateETag(FileMeta fm) {
		String value = fm.getSizeInBytes() + "-" + fm.getLastModifiedEpoch();
		return "\"" + Integer.toHexString(value.hashCode()) + "\"";
	}

	private void copy(InputStream in, OutputStream out) throws IOException {
		byte[] buffer = new byte[8192];
		int read;
		while ((read = in.read(buffer)) != -1) {
			out.write(buffer, 0, read);
			out.flush();
		}
	}

//	private void copyRange(InputStream in, OutputStream out, long len) throws IOException {
//		byte[] buffer = new byte[8192];
//		long remaining = len;
//		int read;
//		while (remaining > 0 && (read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
//			out.write(buffer, 0, read);
//			remaining -= read;
//		}
//	}

}
