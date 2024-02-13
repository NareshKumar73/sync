package com.source.open.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.source.open.payload.FileListJson;

import lombok.Getter;
import lombok.Setter;

@Getter
@Service
public class NetworkUtil {

	private final FileService fs;

	private HttpClient httpClient;

	private WebClient webClient;

//	THIS DEVICE IP LIST - IP & BOOLEAN INDICATING IF IT IS REACHABLE FROM LAN
	private final ConcurrentHashMap<String, Boolean> localIpList = new ConcurrentHashMap<>();

//	DEVICE OVER LAN WITH THIS SOFTWARE RUNNING - THEIR -> IP & PORT
	private final ConcurrentHashMap<String, Integer> activeNodes = new ConcurrentHashMap<>();

//	DEVICE OVER LAN WITH THIS SOFTWARE INSTALLED
	private final ConcurrentLinkedQueue<String> syncServers = new ConcurrentLinkedQueue<>();

	private final Integer serverPort;

	private final byte[] msg;

	private final Integer udpPort = 50505;

	private DatagramSocket udpServer;

//	Can be replaced with found IP function to search all broadcast address and then using this list to broadcast discovery packet 
//	if this global broadcast doesn't work correctly.
	private InetAddress broadcastIp;

	@Setter
	private boolean needed = true;

	public NetworkUtil(FileService fs, @Value("${server.port}") Integer serverPort)
			throws UnknownHostException, SocketException {

		this.fs = fs;

		this.serverPort = serverPort;

		this.msg = (serverPort + "").getBytes(StandardCharsets.US_ASCII);

		httpClient = HttpClient.newBuilder().build();

		broadcastIp = InetAddress.getByAddress(new byte[] { (byte) 255, (byte) 255, (byte) 255, (byte) 255 });

		udpServer = new DatagramSocket(udpPort);
//			udpServer.setSoTimeout(15000);
	}

	public void sendEcho() {
		try {
			udpServer.send(new DatagramPacket(msg, msg.length, broadcastIp, udpPort));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

//	FETCH ALL LOCAL IP AND STORE FOR LATER USE
	public Map<String, Boolean> discoverLocalIpList() {
		
		Map<String, Boolean> map = new HashMap<>();

		try {
			Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

			while (en.hasMoreElements()) {
				NetworkInterface ni = en.nextElement();

				Enumeration<InetAddress> ipList = ni.getInetAddresses();

				while (ipList.hasMoreElements()) {
					InetAddress ip = ipList.nextElement();

					if (ip.isSiteLocalAddress())
						map.put(ip.getHostAddress(), false);
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		localIpList.clear();
		localIpList.putAll(map);

		System.out.println("IP found in LAN\n" + localIpList);
		
		return map;
	}

	public void refreshServerList() {
////	REMOVE PREVIOUS SERVER 
////	NOT NEEDED JUST CALL ON THEIR ENDPOINT AND REMOVE THEM
//	nu.getServers().clear();

//	NOW SEND ECHO SIGNAL AND WAIT FOR ACTIVE NODES
		sendEcho();

		Map<String, CompletableFuture<Integer>> response = new HashMap<>();

//	SEND ECHO TO ALL FOUND IP
		for (Map.Entry<String, Integer> entry : activeNodes.entrySet()) {

			String url = "http://" + entry.getKey() + ":" + entry.getValue();

			response.put(url, pingServer(url));
		}

//	IF ANY IP RETURN 200 THEN PUT IT IN THE ONLINE SERVER LIST
		syncServers.clear();
		
		for (Map.Entry<String, CompletableFuture<Integer>> entry : response.entrySet()) {
			Integer status = 404;
			try {
				status = entry.getValue().get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}

			if (status == 200)
				syncServers.add(entry.getKey());
		}

		System.out.println(syncServers);
	}

	public CompletableFuture<Integer> pingServer(String url) {
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url + "/sync")).timeout(Duration.ofSeconds(15))
				.GET().build();

		return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::statusCode)
				.exceptionally(ex -> 404);
	}

	public FileListJson fetchFileList(String url) {

		return webClient
				.get()
				.uri(url)
				.retrieve()
				.bodyToMono(FileListJson.class)
				.doOnError(Throwable.class, throwable -> {
					System.err.println("Error: " + throwable.getMessage());
				})
				.onErrorReturn(new FileListJson())
				.block();
	}
	
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
		
		int status = 500;

		try {
			status = httpClient
					.send(request, HttpResponse.BodyHandlers.ofFile(dest, StandardOpenOption.CREATE_NEW))
					.statusCode();
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			System.err.println("Failed to download " + filename);
		}
		
		if (status == 200)
			System.out.println(filename + " downloaded successfully.");		
		
//		.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
//		.thenApply(HttpResponse::body)
//		.thenAccept(responseBody -> {
//			try {
////				THIS CODE SHOULD BE MORE PERFORMANT
//				Files.copy(responseBody, dest, StandardCopyOption.REPLACE_EXISTING);
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//				
//        });

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
	
	

//	files = WebClient.create().get().uri(url + "/files/refresh").accept(MediaType.APPLICATION_JSON).retrieve()
//			.bodyToMono(FileListJson.class).block();
	
//	  public Flux<Employee> getAllEmployees() {
//
//		    return webClient.get()
//		      .uri("/employees")
//		      .retrieve()
//		      .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(),
//		              clientResponse -> handleErrorResponse(clientResponse.statusCode()))
//		      .bodyToFlux(Employee.class)
//		      .onErrorResume(Exception.class, e -> Flux.empty()); // Return an empty collection on error
//		  }
//
//		  public Mono<Employee> getEmployeeById(int id) {
//		    return webClient.get()
//		            .uri("/employees/{id}", id)
//		            .retrieve()
//		            .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(),
//		                    clientResponse -> handleErrorResponse(clientResponse.statusCode()))
//		            .bodyToMono(Employee.class);
//		  }
//		  
//		  private Mono<? extends Throwable> handleErrorResponse(HttpStatus statusCode) {
//
//			    // Handle non-success status codes here (e.g., logging or custom error handling)
//			    return Mono.error(new EmployeeServiceException("Failed to fetch employee. Status code: " + statusCode));
//			  }

}
