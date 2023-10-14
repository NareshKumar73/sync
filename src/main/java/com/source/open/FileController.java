package com.source.open;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@CrossOrigin("*")
@RequiredArgsConstructor
@RestController
public class FileController {

    private final FileService fs;

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

    @GetMapping("/download")
    public Mono<Void> downloadFile(
            @RequestParam(required = false) Optional<String> filename,
            @RequestParam(required = false) Optional<String> filecode,
            ServerHttpRequest request,
            ServerHttpResponse response) throws Exception {

        if (filename.isEmpty() && filecode.isEmpty())
            throw new FileNotFoundException("Please provide a filename or filecode to download the file.");

        Path p = null;

        if (filename.isPresent() && filename.get().replaceAll("[^a-zA-Z0-9-_]", "").equals(filename.get())) {
            p = Paths.get("sync-resource" + File.separator + filename.get());
            System.out.println("Client " + request.getRemoteAddress() + " is trying to download: " + filename.get());
        } 
        else {
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

        ZeroCopyHttpOutputMessage zeroCopyHttpOutputMessage = (ZeroCopyHttpOutputMessage) response;

        response.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + f.getName() + "");
        response.getHeaders().setContentType(MediaType.valueOf(fileType));
        response.getHeaders().setContentLength(len);

        return zeroCopyHttpOutputMessage.writeWith(f, 0, len);
    }

    // // Single File Upload
    // @PostMapping("/upload/single")
    // public Mono<Void> uploadFile(@RequestPart("file") Mono<FilePart>
    // filePartMono) {
    // return filePartMono
    // .doOnNext(fp -> System.out.println("Received File : " + fp.filename()))
    // .flatMap(fp -> fp.transferTo(fs.getSyncDir().resolve(fp.filename())))
    // .then();
    // }

    // file upload endpoint - support both multiple and single file upload
    @PostMapping("/upload")
    public Mono<Void> uploadMultipleFiles(@RequestPart("files") Flux<FilePart> partFlux) {
        System.out.println("MULTIPLE FILES ARE INFILTRATING FROM THE MAIN GATE");
        return partFlux
                .doOnNext(fp -> System.out.println("Received File : " + fp.filename()))
                .flatMap(fp -> fp.transferTo(fs.getSyncDir().resolve(fp.filename())))
                .then();
    }

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
