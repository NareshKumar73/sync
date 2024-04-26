package com.source.open.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import com.source.open.payload.ApiMessage;
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

	@GetMapping("/")
	public ResponseEntity<ApiMessage> sync() {
		return ResponseEntity.ok(new ApiMessage("SYNC SERVICE", true));
	}

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

//	@GetMapping(value = "/piece")
//	public ResponseEntity<String> piece(HttpServletRequest request, HttpHeaders requestHeaders) {
//
//		List<HttpRange> rangeList = requestHeaders.getRange();
//
//		log.debug("REQUEST:\n" + requestHeaders);
//
//		log.debug("RANGE: " + rangeList);
//
//		HttpHeaders headers = new HttpHeaders();
//
//		headers.add("Accept-Ranges", "bytes");
//
//		if (!rangeList.isEmpty()) {
//
//			HttpRange hr = rangeList.get(0);
//
//			int start = Long.valueOf(hr.getRangeStart(content.length())).intValue();
//			int end = Long.valueOf(hr.getRangeEnd(content.length())).intValue();
//
//			headers.add("Content-Range", "bytes " + start + "-" + end + "/" + content.length());
//
//			headers.setContentLength(end - start + 1);
//
//			log.debug("RESPONSE PARTIAL:\n" + headers);
//
//			return ResponseEntity.status(206).headers(headers).contentType(MediaType.TEXT_PLAIN)
//					.body(content.substring(start, end + 1));
//		} else {
//			headers.add("Content-Range", "bytes 0-" + (content.length() - 1) + "/" + content.length());
//
//			headers.setContentLength(content.length());
//
//			log.debug("RESPONSE FULL:\n" + headers);
//
//		}
//
//		return ResponseEntity.status(200).headers(headers).contentType(MediaType.TEXT_PLAIN)
//				.body(content.substring(0, content.length()));
//	}
//
//	@GetMapping("/part")
//	public void downloadPartial(@RequestParam(required = false) Optional<String> filename,
//			@RequestParam(required = false) Optional<String> filecode, HttpServletRequest request, HttpHeaders headers,
//			HttpServletResponse response) throws Exception {
//
//		if (filename.isEmpty() && filecode.isEmpty())
//			throw new FileNotFoundException("Please provide a filename or filecode to download the file.");
//
//		Path p = null;
//		// TODO UPDATE HERE AND UPDATE MAP TO USE FILE NAME INSTEAD OF FILECODE
//		if (filename.isPresent()
//				&& java.net.URLDecoder.decode(filename.get(), StandardCharsets.UTF_8).equals(filename.get())) {
//			p = Paths.get("sync-resource" + File.separator + filename.get());
//			log.debug("Client " + request.getRemoteAddr() + " is trying to download: " + filename.get());
//		} else {
//			p = fs.getLocalFiles().get(filecode.get()).getPath();
//			log.debug("Client " + request.getRemoteAddr() + " is trying to download: " + filecode.get());
//		}
//
//		if (p == null)
//			throw new FileNotFoundException(
//					"Please check the file list and provide a valid filename or filecode to download.");
//
//		File f = p.toFile();
//
//		if (f == null)
//			throw new FileNotFoundException("Unable to convert path to file.");
//
//		if (!Files.isRegularFile(p))
//			throw new FileNotFoundException(f.getName());
//
//		long len = f.length();
//
//		String fileType = Files.probeContentType(p);
//
//		if (fileType == null)
//			fileType = "application/octet-stream";
//
//		// RESPONSE
//		response.addHeader("Accept-Ranges", "bytes");
//
//		response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + f.getName() + "");
//
//		response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.valueOf(fileType).toString());
//
//		// RANGE
//
//		List<HttpRange> rangeList = headers.getRange();
//
//		log.debug("REQUEST:\n" + headers);
//
//		log.debug("RANGE: " + rangeList);
//
//		if (!rangeList.isEmpty()) {
//
//			HttpRange hr = rangeList.get(0);
//
//			long start = hr.getRangeStart(len);
//			long end = hr.getRangeEnd(len);
//
//			response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + len);
//
//			response.addHeader(HttpHeaders.CONTENT_LENGTH, (end - start + 1) + "");
//			
//			log.debug("RESPONSE PARTIAL:\n" + response);
//
//			response.setStatus(206);
//
//			byte[] buf = new byte[32768];
//
//			BufferedInputStream in = new BufferedInputStream(Files.newInputStream(p, StandardOpenOption.READ), 32768);
//			BufferedOutputStream out = new BufferedOutputStream(response.getOutputStream(), 32768);
//
//			in.skipNBytes(start);
//
//			long totalLen = end - start + 1;
//
//			while (in.read(buf) > 0) {
//				if (totalLen > 32768) {
//					out.write(buf);
//					totalLen -= 32768;
//				} else {
//					out.write(buf, 0, (int) totalLen);
//					break;
//				}
//			}
//
//			return;
//		}
//
//		response.setStatus(200);
//
//		response.addHeader(HttpHeaders.CONTENT_RANGE, "bytes 0-" + (len - 1) + "/" + len);
//
//		response.addHeader(HttpHeaders.CONTENT_LENGTH,len + "");
//
//		log.debug("RESPONSE FULL:\n" + response);
//
//		Files.copy(p, response.getOutputStream());
//	}

	@GetMapping("/resource")
	public ResponseEntity<Resource> get(HttpServletRequest request,
			@RequestParam(required = false) Optional<String> filecode) throws FileNotFoundException {

		if (filecode.isEmpty())
			throw new FileNotFoundException("Please provide a valid filecode to download the file.");

		FileMeta fileMeta = fs.getLocalFiles().get(filecode.get());

		if (fileMeta == null)
			throw new ResourceNotFoundException("The file you want to download is not available on server.");

		Path p = fileMeta.getPath();

		log.debug("Client " + request.getRemoteAddr() + " is trying to download: " + filecode.get());

		if (p == null)
			throw new FileNotFoundException("Please check directory and confirm the file exists.");

		FileSystemResource resource = new FileSystemResource(p);

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE).body(resource);
	}

//	@PostMapping(value = "/zip", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
//	public ResponseEntity<Resource> getZippedFile(FileRequest fileRequest,
//			HttpServletRequest request, HttpServletResponse response) throws FileNotFoundException {
//		
//		System.out.println(fileRequest);
//		
//		List<String> files = fileRequest.getFilecode();
//
//		if (files.isEmpty()) {
//			log.debug("Empty filecode list for Zip file download of multiple files");
//			return ResponseEntity.noContent().build();
//		}
//		
//		ByteArrayOutputStream out = new ByteArrayOutputStream();
//
//		ZipOutputStream zipOut = new ZipOutputStream(out);
//
//		LinkedHashMap<String, FileMeta> localFiles = fs.getLocalFiles();
//
//		try {
//
//			for (String filecode : files) {
//				FileMeta fm = localFiles.get(filecode);
//				if (fm != null) {
//					zipOut.putNextEntry(new ZipEntry(fm.getName()));
//					Files.copy(fm.getPath(), zipOut);
//				}
//			}
//			zipOut.finish();
//
//		} catch (Exception e) {
//			log.error(e);
//			return ResponseEntity.noContent().build();
//		}
//
//		String filename = "custom_" + LocalDateTime.now() + ".zip";
//
//		log.debug("Client " + request.getRemoteAddr() + " is trying to download: " + filename);
//		
//		Resource data = new ByteArrayResource(out.toByteArray());
//
//		return ResponseEntity.ok()
//				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
//				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
//				.body(data);
//	}

	@PostMapping(value = "/zip-stream", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<StreamingResponseBody> getZippedStream(FileRequest fileRequest,
			HttpServletRequest request, HttpServletResponse response) throws FileNotFoundException {
		
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
				file.transferTo(fs.getSyncDir().resolve(file.getOriginalFilename()));
			} catch (IllegalStateException | IOException e) {
				e.printStackTrace();
			}
		}

	}

}
