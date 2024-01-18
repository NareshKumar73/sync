package com.source.open;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import com.source.open.payload.ApiMessage;
import com.source.open.payload.FileListJson;
import com.source.open.payload.FileMeta;
import com.source.open.util.FileService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
public class FileController {

	private final FileService fs;

	private final String content = "Superman and Supergirl take on the cybernetic being "
			+ "known as Brainiac, who boasts that he possesses the knowledge " + "and strength of 10,000 worlds.";

	@GetMapping("/sync")
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

		System.out.println("REQUEST:\n" + request.getHeaders());

		System.out.println("RANGE: " + rangeList);

		HttpHeaders headers = new HttpHeaders();

		headers.add("Accept-Ranges", "bytes");

		if (!rangeList.isEmpty()) {

			HttpRange hr = rangeList.get(0);

			int start = Long.valueOf(hr.getRangeStart(content.length())).intValue();
			int end = Long.valueOf(hr.getRangeEnd(content.length())).intValue();

			headers.add("Content-Range", "bytes " + start + "-" + end + "/" + content.length());

			headers.setContentLength(end - start + 1);

			System.out.println("RESPONSE PARTIAL:\n" + headers);

			return ResponseEntity.status(206).headers(headers).contentType(MediaType.TEXT_PLAIN)
					.body(content.substring(start, end + 1));
		} else {
			headers.add("Content-Range", "bytes 0-" + (content.length() - 1) + "/" + content.length());

			headers.setContentLength(content.length());

			System.out.println("RESPONSE FULL:\n" + headers);

		}

		return ResponseEntity.status(200).headers(headers).contentType(MediaType.TEXT_PLAIN)
				.body(content.substring(0, content.length()));
	}

	@GetMapping("/part")
	public Mono<Void> downloadPartial(
			@RequestParam(required = false) Optional<String> filename,
			@RequestParam(required = false) Optional<String> filecode,
			ServerHttpRequest request,
			ServerHttpResponse response) throws Exception {

		if (filename.isEmpty() && filecode.isEmpty())
			throw new FileNotFoundException("Please provide a filename or filecode to download the file.");

		Path p = null;
		// TODO UPDATE HERE AND UPDATE MAP TO USE FILE NAME INSTEAD OF FILECODE
		if (filename.isPresent()
				&& java.net.URLDecoder.decode(filename.get(), StandardCharsets.UTF_8).equals(filename.get())) {
			p = Paths.get("sync-resource" + File.separator + filename.get());
			System.out.println("Client " + request.getRemoteAddress() + " is trying to download: " + filename.get());
		} else {
			p = fs.getLocalFiles().get(filecode.get()).getPath();
			System.out.println("Client " + request.getRemoteAddress() + " is trying to download: " + filecode.get());
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

		System.out.println("REQUEST:\n" + request.getHeaders());

		System.out.println("RANGE: " + rangeList);

		if (!rangeList.isEmpty()) {

			HttpRange hr = rangeList.get(0);

			long start = hr.getRangeStart(len);
			long end = hr.getRangeEnd(len);

			response.getHeaders().add("Content-Range", "bytes " + start + "-" + end + "/" + len);

			response.getHeaders().setContentLength(end - start + 1);

			System.out.println("RESPONSE PARTIAL:\n" + response.getHeaders());

			response.setStatusCode(HttpStatusCode.valueOf(206));

			return zeroCopyHttpOutputMessage.writeWith(f, start, end - start + 1);
		}

		response.setStatusCode(HttpStatusCode.valueOf(200));

		response.getHeaders().add("Content-Range", "bytes 0-" + (len - 1) + "/" + len);

		response.getHeaders().setContentLength(len);

		System.out.println("RESPONSE FULL:\n" + response.getHeaders());

		return zeroCopyHttpOutputMessage.writeWith(f, 0, len);
	}
	
    @GetMapping("/resource")
    public ResponseEntity<Resource> get(ServerHttpRequest request, 
    		@RequestParam(required = false) Optional<String> filecode) throws FileNotFoundException {
    	
		if (filecode.isEmpty())
			throw new FileNotFoundException("Please provide a valid filecode to download the file.");

		Path p = fs.getLocalFiles().get(filecode.get()).getPath();

		System.out.println("Client " + request.getRemoteAddress() + " is trying to download: " + filecode.get());

		if (p == null)
			throw new FileNotFoundException("Please check directory and confirm the file exists.");
		
		FileSystemResource resource = new FileSystemResource(p);
		
        return ResponseEntity.ok()
        		.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
        		.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
        		.body(resource);
    }
	
	// file upload endpoint - support both multiple and single file upload
	@PostMapping("/upload")
	public Mono<Void> uploadMultipleFiles(@RequestPart("files") Flux<FilePart> partFlux) {
		System.out.println("MULTIPLE FILES ARE INFILTRATING FROM THE MAIN GATE");
		return partFlux.doOnNext(fp -> System.out.println("Received File : " + fp.filename()))
				.flatMap(fp -> fp.transferTo(fs.getSyncDir().resolve(fp.filename()))).then();
	}
	
//	@GetMapping("/download")
//	public Mono<Void> downloadFile(@RequestParam(required = false) Optional<String> filename,
//			@RequestParam(required = false) Optional<String> filecode, ServerHttpRequest request,
//			ServerHttpResponse response) throws Exception {
//
//		if (filename.isEmpty() && filecode.isEmpty())
//			throw new FileNotFoundException("Please provide a filename or filecode to download the file.");
//
//		Path p = null;
//		// TODO UPDATE HERE AND UPDATE MAP TO USE FILE NAME INSTEAD OF FILECODE
//		if (filename.isPresent()
//				&& java.net.URLDecoder.decode(filename.get(), StandardCharsets.UTF_8).equals(filename.get())) {
//			p = Paths.get("sync-resource" + File.separator + filename.get());
//			System.out.println("Client " + request.getRemoteAddress() + " is trying to download: " + filename.get());
//		} else {
//			p = fs.getLocalFiles().get(filecode.get()).getPath();
//			System.out.println("Client " + request.getRemoteAddress() + " is trying to download: " + filecode.get());
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
//		ZeroCopyHttpOutputMessage zeroCopyHttpOutputMessage = (ZeroCopyHttpOutputMessage) response;
//
//		response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + f.getName() + "");
//		response.getHeaders().setContentType(MediaType.valueOf(fileType));
//		response.getHeaders().setContentLength(len);
//
//		return zeroCopyHttpOutputMessage.writeWith(f, 0, len);
//	}


	// @GetMapping("/multipart")
	// public ResponseEntity<?> multiRangeDownload(
	// 		@RequestParam(required = false) Optional<String> filecode,
	// 		ServerHttpRequest request) throws IOException, Exception {

	// 	Path p = fs.getLocalFiles().get(filecode.get()).getPath();
	// 	System.out.println("Client " + request.getRemoteAddress() + " is trying to download: " + filecode.get());

	// 	if (p == null)
	// 		throw new FileNotFoundException(
	// 				"Please check the file list and provide a valid filename or filecode to download.");

	// 	File f = p.toFile();


	// 	// FILE TYPE CHECK

	// 	if (!Files.isRegularFile(p))
	// 		throw new FileNotFoundException(f.getName());

	// 	String fileType = Files.probeContentType(p);

	// 	if (fileType == null)
	// 		fileType = "application/octet-stream";

	// 	// RESPONSE

	// 	HttpHeaders headers = new HttpHeaders();

	// 	headers.add("Accept-Ranges", "bytes");

	// 	// headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + f.getName() + "");

	// 	// headers.setContentType(MediaType.valueOf(fileType));
	// 	headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

	// 	// RANGE

	// 	List<HttpRange> rangeList = request.getHeaders().getRange();

	// 	System.out.println("REQUEST:\n" + request.getHeaders());

	// 	System.out.println("RANGE: " + rangeList);

	// 	FileSystemResource resource = new FileSystemResource(p);

	// 	if (!rangeList.isEmpty()) {

	// 		List<ResourceRegion> regions = HttpRange.toResourceRegions(rangeList, resource);

	// 		List<HttpEntity<Resource>> entities = new ArrayList<>();

	// 		for(ResourceRegion region : regions) {

	// 			HttpHeaders partHeader = new HttpHeaders();
	// 			partHeader.setContentType(MediaType.APPLICATION_OCTET_STREAM);
	// 			partHeader.setContentLength(region.getCount());
	// 			partHeader.set(HttpHeaders.CONTENT_RANGE, "bytes " + region.getPosition() + "-"
	// 					+ (region.getPosition() + region.getCount() - 1) + "/" + resource.contentLength());
	
	// 			entities.add(new HttpEntity<>(region.getResource(), partHeader));
	// 		}

	// 		return new ResponseEntity<List<HttpEntity<Resource>>>(entities, headers, HttpStatus.PARTIAL_CONTENT);
	// 	}
		
	// 	// BufferedInputStream buffered = new BufferedInputStream(resource.getInputStream());

	// 	return new ResponseEntity<HttpEntity<Resource>>(new HttpEntity<>(resource), headers, HttpStatus.PARTIAL_CONTENT);
	// }

	//// START
	//
	// RandomAccessFile aFile = new RandomAccessFile(f, "r");
	//
	// aFile.seek(start);
	//
	// FileChannel inChannel = aFile.getChannel();
	//
	//// create buffer with capacity of 48 bytes
	// ByteBuffer buf = ByteBuffer.allocate(32_768);
	//
	// int bytesRead = inChannel.read(buf); //read into buffer.
	// while (bytesRead != -1) {
	//
	// buf.flip(); //make buffer ready for read
	//
	// while(buf.hasRemaining()){
	// System.out.print((char) buf.get()); // read 1 byte at a time
	// }
	//
	// buf.clear(); //make buffer ready for writing
	// bytesRead = inChannel.read(buf);
	// }
	// aFile.close();
	//
	//// END

	// headers.setContentType(MediaType.TEXT_PLAIN);

	// headers.setAccessControlAllowOrigin("*");

	// Flux<DataBuffer> flux = DataBufferUtils.read( new
	// ByteArrayResource(content.getBytes()), response.bufferFactory(), 2048);

	// response = ServerResponse.status(206).headers(h ->
	// h.addAll(headers)).contentType(MediaType.TEXT_PLAIN)
	// .bodyValue(content.substring(start, end + 1));
	// return ResponseEntity.ok(new ResourceRegion(new
	// ByteArrayResource(content.getBytes()), 0, content.length()));

	// @GetMapping("/dl")
	// public ResponseEntity<Object> resumableDownload(
	// @RequestHeader(value = "Range", required = false) String range,
	// @RequestParam(required = false) Optional<String> filename,
	// @RequestParam(required = false) Optional<String> filecode,
	// ServerHttpRequest request,
	// ServerHttpResponse response) throws Exception {
	//
	// System.out.println(request.getHeaders());
	//
	//
	// if (filename.isEmpty() && filecode.isEmpty())
	// throw new FileNotFoundException("Please provide a filename or filecode to
	// download the file.");
	//
	// Path p = null;
	//// TODO UPDATE HERE AND UPDATE MAP TO USE FILE NAME INSTEAD OF FILECODE
	// if (filename.isPresent()
	// && java.net.URLDecoder.decode(filename.get(),
	// StandardCharsets.UTF_8).equals(filename.get())) {
	// p = Paths.get("sync-resource" + File.separator + filename.get());
	// System.out.println("Client " + request.getRemoteAddress() + " is trying to
	// download: " + filename.get());
	// } else {
	// p = fs.getLocalFiles().get(filecode.get()).getPath();
	// System.out.println("Client " + request.getRemoteAddress() + " is trying to
	// download: " + filecode.get());
	// }
	//
	// if (p == null)
	// throw new FileNotFoundException(
	// "Please check the file list and provide a valid filename or filecode to
	// download.");
	//
	// File f = p.toFile();
	//
	// if (f == null)
	// throw new FileNotFoundException("Unable to convert path to file.");
	//
	// if (!Files.isRegularFile(p))
	// throw new FileNotFoundException(f.getName());
	//
	// long len = f.length();
	//
	// String fileType = Files.probeContentType(p);
	//
	// if (fileType == null)
	// fileType = "application/octet-stream";
	//
	// if (range != null && !range.isBlank()) {
	//
	// List<HttpRange> rangeList = HttpRange.parseRanges(range);
	//
	// if (rangeList != null && !rangeList.isEmpty()) {
	// List<ResourceRegion> resourceRegions = HttpRange.toResourceRegions(rangeList,
	// new PathResource(p));
	//
	// response.getHeaders().set("Accept-Ranges", "bytes");
	//
	//// TODO SET CONTENT RANGE = Content-Range: bytes 200-500/50000
	//
	// response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION, "attachment;
	// filename=" + f.getName() + "");
	//
	// if (rangeList.size() > 1) {
	// response.getHeaders().set("Content-Type", "multipart/byteranges");
	//
	// System.out.println("MULTIPLE RANGE REQUEST\n\n" + response.getHeaders());
	//
	// return
	// ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(response.getHeaders())
	// .body(resourceRegions);
	// } else {
	// response.getHeaders().set("Content-Type", fileType);
	// response.getHeaders().setContentLength(resourceRegions.get(0).getCount());
	//
	// System.out.println("SINGLE RANGE REQUEST\n\n" + response.getHeaders());
	//
	// return
	// ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(response.getHeaders())
	// .body(resourceRegions.get(0));
	// }
	// }
	// }
	//// IF NO RANGE HEADER OR BLANK RANGE HEADER
	// response.getHeaders().setContentLength(len);
	// response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION, "attachment;
	// filename=" + f.getName() + "");
	//
	// System.out.println("FULL REQUEST\n\n" + response.getHeaders());
	//
	// return
	// ResponseEntity.status(HttpStatus.OK).headers(response.getHeaders()).body(new
	// PathResource(p));
	//
	// }

	// @GetMapping(value = "download-single-report-partial")
	// public void downloadSingleReportPartial(HttpServletRequest request,
	// HttpServletResponse response) {
	// File dlFile = new File("some_path");
	// if (!dlFile.exists()) {
	// response.setStatus(HttpStatus.NOT_FOUND.value());
	// return;
	// }
	// try {
	// writeRangeResource(request, response, dlFile);
	// } catch (Exception ex) {
	// response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
	// }
	// }
	//
	// public static void writeRangeResource(HttpServletRequest request,
	// HttpServletResponse response, File file)
	// throws IOException {
	// String range = request.getHeader("Range");
	// if (StringUtils.hasLength(range)) {
	// // http
	// ResourceRegion region = getResourceRegion(file, range);
	// long start = region.getPosition();
	// long end = start + region.getCount() - 1;
	// long resourceLength = region.getResource().contentLength();
	// end = Math.min(end, resourceLength - 1);
	// long rangeLength = end - start + 1;
	//
	// response.setStatus(206);
	// response.addHeader("Accept-Ranges", "bytes");
	// response.addHeader("Content-Range", String.format("bytes %s-%s/%s", start,
	// end, resourceLength));
	// response.setContentLengthLong(rangeLength);
	// try (OutputStream outputStream = response.getOutputStream()) {
	// try (InputStream inputStream = new BufferedInputStream(new
	// FileInputStream(file))) {
	// StreamUtils.copyRange(inputStream, outputStream, start, end);
	// }
	// }
	// } else {
	// response.setStatus(200);
	// response.addHeader("Accept-Ranges", "bytes");
	// response.setContentLengthLong(file.length());
	// try (OutputStream outputStream = response.getOutputStream()) {
	// try (InputStream inputStream = new BufferedInputStream(new
	// FileInputStream(file))) {
	// StreamUtils.copy(inputStream, outputStream);
	// }
	// }
	// }
	// }
	//
	// private static ResourceRegion getResourceRegion(File file, String range) {
	// List<HttpRange> httpRanges = HttpRange.parseRanges(range);
	// if (httpRanges.isEmpty()) {
	// return new ResourceRegion(new FileSystemResource(file), 0, file.length());
	// }
	// return httpRanges.get(0).toResourceRegion(new FileSystemResource(file));
	// }

	// // Single File Upload
	// @PostMapping("/upload/single")
	// public Mono<Void> uploadFile(@RequestPart("file") Mono<FilePart>
	// filePartMono) {
	// return filePartMono
	// .doOnNext(fp -> System.out.println("Received File : " + fp.filename()))
	// .flatMap(fp -> fp.transferTo(fs.getSyncDir().resolve(fp.filename())))
	// .then();
	// }

	// public void fun(String filename) {
	//
	// Path p = SyncApplication.baseDirPath.resolve(filename);
	//
	// Files.createFile(p);
	//
	// final AsynchronousFileChannel fileChannel = AsynchronousFileChannel.open(p,
	// StandardOpenOption.WRITE);
	//
	// AtomicLong fileSize = new AtomicLong(p.toFile().length());
	//
	// Flux<DataBuffer> fileDataStream = webClient
	// .get()
	// .uri(remoteXFerServiceTargetHost)
	// .accept(MediaType.APPLICATION_OCTET_STREAM)
	// .header("Range", String.format("bytes=%d-", fileSize.get()))
	// .retrieve()
	// .onStatus(HttpStatus::is4xxClientError, clientResponse -> Mono.error(new
	// CustomException("4xx error")))
	// .onStatus(HttpStatus::is5xxServerError, clientResponse -> Mono.error(new
	// CustomException("5xx error")))
	// .bodyToFlux(DataBuffer.class);
	//
	// DataBufferUtils
	// .write(fileDataStream , fileChannel)
	// .map(DataBufferUtils::release)
	// .doOnError(throwable -> {
	// try {
	// fileChannel.force(true);
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// })
	// .retryWhen(Retry.backoff(5, Duration.ofSeconds(5))
	// .doOnComplete(() -> {
	// try {
	// fileChannel.force(true);
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// })
	// .doOnError(e -> !(e instanceof ChannelException), e -> {
	// try {
	// Files.deleteIfExists(targetPath);
	// } catch (IOException exc) {
	// exc.printStackTrace();
	// }
	// })
	// .doOnError(ChannelException.class, e -> {
	// try {
	// Files.deleteIfExists(targetPath);
	// } catch (IOException exc) {
	// exc.printStackTrace();
	// }
	// })
	// .doOnTerminate(() -> {
	// try {
	// fileChannel.close();
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// })
	// .blockLast();
	// }

}
