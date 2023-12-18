package com.source.open;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.source.open.payload.FileListJson;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Getter
@Service
public class ClientService {

	@Autowired 
	private FileService fs;
	
//	private String host;
	
	@Value("${server.port}")
	private Integer port;
	
	private HttpClient httpClient;

	public ClientService() {
		super();
		
//		host = InetAddress.getLocalHost().getHostAddress();
		
		httpClient = HttpClient.newBuilder().build();
	}
	
	public CompletableFuture<Integer> checkServer(String url) {
		HttpRequest request = HttpRequest.newBuilder()
				  				.uri(URI.create("http://" + url + ":" + port + "/sync"))
				  				.timeout(Duration.ofSeconds(15))
				  				.GET().build();
		
		return httpClient
				.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::statusCode)
				.exceptionally(ex -> 404);
	}
	
	public FileListJson fetchFileList(String serverUrl) {
		
		return WebClient
				.create()
				.get()
				.uri("http://" + serverUrl + ":" + port + "/files")
				.accept(MediaType.APPLICATION_JSON)
				.retrieve().bodyToMono(FileListJson.class)
				.block();		
	}

	public void downloadFileAsynchronously(String remoteFileUrl, Path localFilePath) throws IOException {
		
		System.out.println("Remote File: " + remoteFileUrl + " Local File: " + localFilePath);
		
		Flux<byte[]> byteStream = WebClient
									.create().get()
									.uri(remoteFileUrl).exchangeToFlux(res -> {
										if (res.statusCode().is2xxSuccessful())
											return res.bodyToFlux(byte[].class);
										else
											return null;									
									});
		
		File d = fs.createFile(localFilePath).toFile();
		
		try (BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(d), 32768)) {
						
			byteStream.subscribe(bytes -> {
				try {
					writer.write(bytes);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});

			Mono<Void> downloadCompletion = byteStream.then();

            downloadCompletion.block(); 
			
		} catch (IOException e) {
			e.printStackTrace();
		} 

		
//		WebClient
//		.create()
//		.get()
//		.uri(remoteFileUrl)
//		.retrieve()
//		.bodyToMono(byte[].class)
//		.subscribe(content -> {
//			try (AsynchronousFileChannel fc = AsynchronousFileChannel.open(
//												localFilePath,
//												StandardOpenOption.TRUNCATE_EXISTING,
//												StandardOpenOption.CREATE, 
//												StandardOpenOption.WRITE)) {
//				fc.write(java.nio.ByteBuffer.wrap(content), 0, null, new CompletionHandler<Integer, Void>() {
//					@Override
//					public void completed(Integer result, Void attachment) {
//						System.out.println("File downloaded to: " + localFilePath);
//					}
//					
//					@Override
//					public void failed(Throwable exc, Void attachment) {
//						System.err.println("File download failed: " + exc.getMessage());
//					}
//				});
//
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		
//		}, error -> {
//			System.err.println("WebClient request failed: ");
//			error.printStackTrace();
//		});
	}
	
//	NOT WORKING URL
	public void downloadFileSynchronously(String remoteFileUrl, String filename) throws IOException {
		
		System.out.println("Remote Filecode: " + remoteFileUrl + " Local File: " + filename);
		
		HttpClient httpClient = HttpClient
								.newBuilder()
//								.connectTimeout(Duration.ofMinutes(15))	//	TESTING CODE NOT NEEDED IN PRODUCTION
								.build();

		HttpRequest request = HttpRequest
								.newBuilder()
								.uri(URI.create(remoteFileUrl))
								.build();

		Path dest = fs.getSyncDir().resolve(filename);

		fs.createFile(dest);

		httpClient
		.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
		.thenApply(HttpResponse::body)
		.thenAccept(responseBody -> {
			try {
//				THIS CODE SHOULD BE MORE PERFORMANT
				Files.copy(responseBody, dest, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				e.printStackTrace();
			}
				
        });

//		httpClient
//		.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
//		.thenApply(HttpResponse::body)
//		.thenAccept(responseBody -> {
//			try (BufferedInputStream reader = new BufferedInputStream(responseBody, 32768);
//				BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(d), 32768)) {
//				
//				reader.transferTo(writer);
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//				
//        });
	}
	
//	TO MANAGE OVERLAPPING RANGE IN DOWNLOAD
//	public void mergeInterval(HttpRange[] rangeList, long fileSize) {
//
//		if (rangeList.length == 1)
//			return;
//
//		Arrays.sort(rangeList, (o1, o2) -> {
//			if (o1.getRangeStart(fileSize) > o2.getRangeStart(fileSize))
//				return 0;
//			else if (o1.getRangeStart(fileSize) > o2.getRangeStart(fileSize))
//				return 1;
//			else
//				return -1;
//		});
//
//		int index = 0;
//		for (int i = 1; i < rangeList.length; i++) {
//			if (rangeList[index].getRangeEnd(fileSize) >= rangeList[i].getRangeStart(fileSize)) {
//
//				long end = Math.max(rangeList[index].getRangeEnd(fileSize), rangeList[i].getRangeEnd(fileSize));
//				
//				rangeList[index] = HttpRange.createByteRange(rangeList[index].getRangeStart(fileSize), end);
//			}
//			else {
//				index++;
//				rangeList[index] = rangeList[i];
//			}
//		}
//
//	}
	
//	void fileReader() {
//		try (SeekableByteChannel ch = java.nio.file.Files.newByteChannel(Paths.get(fileName), StandardOpenOption.READ)) {
//		    ByteBuffer bf = ByteBuffer.allocate(1000);
//		    while (ch.read(bf) > 0) {
//		        bf.flip();
//		        // System.out.println(new String(bf.array()));
//		        bf.clear();
//		    }
//		}
//	}
//	
//	void bestFileWriter( ) {
//		
////		GOOD
//		try (BufferedInputStream in = new BufferedInputStream(new URL(FILE_URL).openStream());
//				  FileOutputStream fileOutputStream = new FileOutputStream(FILE_NAME)) {
//				    byte dataBuffer[] = new byte[1024];
//				    int bytesRead;
//				    while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
//				        fileOutputStream.write(dataBuffer, 0, bytesRead);
//				    }
//				} catch (IOException e) {
//				    // handle exception
//				}
//		
////		BETTER
//		ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
//		FileOutputStream fileOutputStream = new FileOutputStream(FILE_NAME);
//		FileChannel fileChannel = fileOutputStream.getChannel();
//		fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
//	}
//	
//	void resumableDownload() {
//		
//		URL url = new URL(FILE_URL);
//		HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
//		httpConnection.setRequestMethod("HEAD");
//		long removeFileSize = httpConnection.getContentLengthLong();
//		
//		long existingFileSize = outputFile.length();
//		if (existingFileSize < fileLength) {
//		    httpFileConnection.setRequestProperty(
//		      "Range", 
//		      "bytes=" + existingFileSize + "-" + fileLength
//		    );
//		}
//		
//		OutputStream os = new FileOutputStream(FILE_NAME, true);
//	}

}


//Constructor code

//HttpClient httpClient;
//
//httpClient = HttpClient.create()
//			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
//			.responseTimeout(Duration.ofMillis(5000))
//			.doOnConnected(conn -> conn
//				.addHandlerLast(new ReadTimeoutHandler(15000, TimeUnit.MILLISECONDS))
//				.addHandlerLast(new WriteTimeoutHandler(15000, TimeUnit.MILLISECONDS)));
//
//syncServerDiscoveryClient = WebClient.builder()
//							.clientConnector(new ReactorClientHttpConnector(httpClient))
//							.build();

//.exchangeToMono(res -> {
//if (res.statusCode().is2xxSuccessful())
//	return res.bodyToMono(ApiMessage.class);
//
//System.out.println("AN ERROR OCCURED");
//return Mono.just(null);
//}).block();
