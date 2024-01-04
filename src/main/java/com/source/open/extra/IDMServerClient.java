package com.source.open.extra;

public class IDMServerClient {

}


/**
 * Client Code from here
 * 
 */

//import org.springframework.core.io.InputStreamResource;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.RandomAccessFile;
//
//@RestController
//@RequestMapping("/api")
//public class FileController {
//    private final String filePath = "path/to/your/file.zip"; // Update with your file path
//
//    @GetMapping("/download")
//    public ResponseEntity<InputStreamResource> downloadFile(@RequestParam(name = "chunk", required = false) Long chunk) throws IOException {
//        File file = new File(filePath);
//        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
//
//        long fileLength = randomAccessFile.length();
//
//        if (chunk == null || chunk < 0) {
//            chunk = 0L;
//        }
//
//        if (chunk >= fileLength) {
//            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
//        }
//
//        long remainingBytes = fileLength - chunk;
//        int bufferSize = (int) Math.min(remainingBytes, 1024 * 1024); // 1 MB chunks
//        byte[] buffer = new byte[bufferSize];
//
//        randomAccessFile.seek(chunk);
//        int bytesRead = randomAccessFile.read(buffer, 0, bufferSize);
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//        headers.setContentLength(bytesRead);
//        headers.setContentRange(chunk, chunk + bytesRead - 1, fileLength);
//
//        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
//
//        return new ResponseEntity<>(resource, headers, HttpStatus.PARTIAL_CONTENT);
//    }
//}




/**
 * Server Code from here
 * 
 */

//import org.springframework.core.io.InputStreamResource;
//import org.springframework.http.HttpHeaders;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.RandomAccessFile;
//
//@RestController
//@RequestMapping("/api")
//public class FileController {
//    private final String filePath = "path/to/your/file.zip"; // Update with your file path
//
//    @GetMapping("/download")
//    public ResponseEntity<InputStreamResource> downloadFile(@RequestParam(name = "chunk", required = false) Long chunk) throws IOException {
//        File file = new File(filePath);
//        RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
//
//        long fileLength = randomAccessFile.length();
//
//        if (chunk == null || chunk < 0) {
//            chunk = 0L;
//        }
//
//        if (chunk >= fileLength) {
//            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
//        }
//
//        long remainingBytes = fileLength - chunk;
//        int bufferSize = (int) Math.min(remainingBytes, 1024 * 1024); // 1 MB chunks
//        byte[] buffer = new byte[bufferSize];
//
//        randomAccessFile.seek(chunk);
//        int bytesRead = randomAccessFile.read(buffer, 0, bufferSize);
//
//        HttpHeaders headers = new HttpHeaders();
//        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
//        headers.setContentLength(bytesRead);
//        headers.setContentRange(chunk, chunk + bytesRead - 1, fileLength);
//
//        InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
//
//        return new ResponseEntity<>(resource, headers, HttpStatus.PARTIAL_CONTENT);
//    }
//}
