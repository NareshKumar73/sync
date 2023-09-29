package com.source.open;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
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
	
	private String host;
	
	@Value("${server.port}")
	private Integer port;
	
	private HttpClient httpClient;

	public ClientService() throws UnknownHostException {
		super();
		
		host = InetAddress.getLocalHost().getHostAddress();
		
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
	public void downloadFileSynchronously(String remoteFileUrl, Path localFilePath) throws IOException {
		
		System.out.println("Remote Filecode: " + remoteFileUrl + " Local File: " + localFilePath);
		
		HttpClient httpClient = HttpClient
								.newBuilder()
								.connectTimeout(Duration.ofMinutes(15))
								.build();

		HttpRequest request = HttpRequest
								.newBuilder()
								.uri(URI.create(remoteFileUrl))
								.build();

		File d = fs.createFile(localFilePath).toFile();		

		httpClient
		.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
		.thenApply(HttpResponse::body)
		.thenAccept(responseBody -> {
			try (BufferedInputStream reader = new BufferedInputStream(responseBody, 32768);
				BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(d), 32768)) {
				
				reader.transferTo(writer);
			} catch (IOException e) {
				e.printStackTrace();
			}
				
        });
	}

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
