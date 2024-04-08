package com.source.open.controller;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.source.open.exception.ResourceNotFoundException;
import com.source.open.payload.ApiMessage;
import com.source.open.payload.FileListJson;
import com.source.open.payload.FileMeta;
import com.source.open.util.FileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Log4j2
@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
public class FileController {

	private final FileService fs;

	private final String content = "Superman and Supergirl " + "take on the cybernetic being known as Brainiac, "
			+ "who boasts that he possesses the knowledge and strength " + "of 10,000 worlds.";

	@GetMapping("/")
	public Mono<ApiMessage> sync() {
		return Mono.just(new ApiMessage("SYNC SERVICE", true));
	}

	@GetMapping("/files")
	public Mono<FileListJson> fileList() {

		List<FileMeta> metaList = fs.getLocalFilesList();

		return Mono.just(new FileListJson(metaList, metaList.size()));
	}

	@GetMapping("/files/refresh")
	public Mono<FileListJson> fetchFilesFromFileSystem() {

		List<FileMeta> list = fs.refreshFileList();

		return Mono.just(new FileListJson(list, list.size()));
	}

	@GetMapping(value = "/piece")
	public ResponseEntity<String> piece(ServerHttpRequest request) {

		List<HttpRange> rangeList = request.getHeaders().getRange();

		log.debug("REQUEST:\n" + request.getHeaders());

		log.debug("RANGE: " + rangeList);

		HttpHeaders headers = new HttpHeaders();

		headers.add("Accept-Ranges", "bytes");

		if (!rangeList.isEmpty()) {

			HttpRange hr = rangeList.get(0);

			int start = Long.valueOf(hr.getRangeStart(content.length())).intValue();
			int end = Long.valueOf(hr.getRangeEnd(content.length())).intValue();

			headers.add("Content-Range", "bytes " + start + "-" + end + "/" + content.length());

			headers.setContentLength(end - start + 1);

			log.debug("RESPONSE PARTIAL:\n" + headers);

			return ResponseEntity.status(206).headers(headers).contentType(MediaType.TEXT_PLAIN)
					.body(content.substring(start, end + 1));
		} else {
			headers.add("Content-Range", "bytes 0-" + (content.length() - 1) + "/" + content.length());

			headers.setContentLength(content.length());

			log.debug("RESPONSE FULL:\n" + headers);

		}

		return ResponseEntity.status(200).headers(headers).contentType(MediaType.TEXT_PLAIN)
				.body(content.substring(0, content.length()));
	}

	@GetMapping("/part")
	public Mono<Void> downloadPartial(@RequestParam(required = false) Optional<String> filename,
			@RequestParam(required = false) Optional<String> filecode, ServerHttpRequest request,
			ServerHttpResponse response) throws Exception {

		if (filename.isEmpty() && filecode.isEmpty())
			throw new FileNotFoundException("Please provide a filename or filecode to download the file.");

		Path p = null;
		// TODO UPDATE HERE AND UPDATE MAP TO USE FILE NAME INSTEAD OF FILECODE
		if (filename.isPresent()
				&& java.net.URLDecoder.decode(filename.get(), StandardCharsets.UTF_8).equals(filename.get())) {
			p = Paths.get("sync-resource" + File.separator + filename.get());
			log.debug("Client " + request.getRemoteAddress() + " is trying to download: " + filename.get());
		} else {
			p = fs.getLocalFiles().get(filecode.get()).getPath();
			log.debug("Client " + request.getRemoteAddress() + " is trying to download: " + filecode.get());
		}

		if (p == null)
			throw new FileNotFoundException(
					"Please check the file list and provide a valid filename or filecode to download.");

		File f = p.toFile();

		if (f == null)
			throw new FileNotFoundException("Unable to convert path to file.");

		if (!Files.isRegularFile(p))
			throw new FileNotFoundException(f.getName());

		long len = f.length();

		String fileType = Files.probeContentType(p);

		if (fileType == null)
			fileType = "application/octet-stream";

		// RESPONSE

		ZeroCopyHttpOutputMessage zeroCopyHttpOutputMessage = (ZeroCopyHttpOutputMessage) response;

		response.getHeaders().add("Accept-Ranges", "bytes");

		response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + f.getName() + "");

		response.getHeaders().setContentType(MediaType.valueOf(fileType));

		// RANGE

		List<HttpRange> rangeList = request.getHeaders().getRange();

		log.debug("REQUEST:\n" + request.getHeaders());

		log.debug("RANGE: " + rangeList);

		if (!rangeList.isEmpty()) {

			HttpRange hr = rangeList.get(0);

			long start = hr.getRangeStart(len);
			long end = hr.getRangeEnd(len);

			response.getHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + len);

			response.getHeaders().setContentLength(end - start + 1);

			log.debug("RESPONSE PARTIAL:\n" + response.getHeaders());

			response.setStatusCode(HttpStatusCode.valueOf(206));

			return zeroCopyHttpOutputMessage.writeWith(f, start, end - start + 1);
		}

		response.setStatusCode(HttpStatusCode.valueOf(200));

		response.getHeaders().add("Content-Range", "bytes 0-" + (len - 1) + "/" + len);

		response.getHeaders().setContentLength(len);

		log.debug("RESPONSE FULL:\n" + response.getHeaders());

		return zeroCopyHttpOutputMessage.writeWith(f, 0, len);
	}

	@GetMapping("/resource")
	public Mono<ResponseEntity<Resource>> get(ServerHttpRequest request,
			@RequestParam(required = false) Optional<String> filecode) throws FileNotFoundException {

		if (filecode.isEmpty())
			throw new FileNotFoundException("Please provide a valid filecode to download the file.");

		FileMeta fileMeta = fs.getLocalFiles().get(filecode.get());

		if (fileMeta == null)
			throw new ResourceNotFoundException("The file you want to download is not available on server.");

		Path p = fileMeta.getPath();

		log.debug("Client " + request.getRemoteAddress() + " is trying to download: " + filecode.get());

		if (p == null)
			throw new FileNotFoundException("Please check directory and confirm the file exists.");

		FileSystemResource resource = new FileSystemResource(p);

		return Mono.just(ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE).body(resource));
	}

	@PostMapping("/zip")
	public Mono<ResponseEntity<Resource>> getZippedFile(@RequestBody List<String> files,
			ServerHttpRequest request, ServerHttpResponse response) throws FileNotFoundException {

		if (files.isEmpty()) {
			log.debug("Empty filecode list for Zip file download of multiple files");
			response.setRawStatusCode(204);
			return Mono.empty();
		}
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		ZipOutputStream zipOut = new ZipOutputStream(out);

		LinkedHashMap<String, FileMeta> localFiles = fs.getLocalFiles();

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
			response.setRawStatusCode(204);
			return Mono.empty();
		}

		String filename = "custom_" + LocalDateTime.now() + ".zip";

		log.debug("Client " + request.getRemoteAddress() + " is trying to download: " + filename);
		
		Resource data = new ByteArrayResource(out.toByteArray());

		return Mono.just(
				ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
						.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE).body(data));
	}
	
	// file upload endpoint - support both multiple and single file upload
	@PostMapping("/upload")
	public Mono<Void> uploadMultipleFiles(@RequestPart("file") Flux<FilePart> partFlux) {
		log.debug("File upload request arrived");
		return partFlux.doOnNext(fp -> log.debug("Received File : " + fp.filename()))
				.flatMap(fp -> fp.transferTo(fs.getSyncDir().resolve(fp.filename()))).then();
	}

}
