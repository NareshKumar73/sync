package com.source.open.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.source.open.payload.FileListJson;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
public class NetworkUtil {

//	private WebClient webClient;
	private RestClient client;

//	THIS DEVICE IP LIST - IP & BOOLEAN INDICATING IF IT IS REACHABLE FROM LAN
	private final ConcurrentHashMap<String, Boolean> localIpList = new ConcurrentHashMap<>();

//	DEVICE OVER LAN WITH THIS SOFTWARE RUNNING - THEIR -> IP & PORT
	@Getter
	private final ConcurrentHashMap<String, Integer> activeNodes = new ConcurrentHashMap<>();

	private final int serverPort;

	private final int udpPort;

	private final DatagramSocket udpServer;

//	Can be replaced with found IP function to search all broadcast address and then using this list to broadcast discovery packet 
//	if this global broadcast doesn't work correctly.
	private InetAddress broadcastIp;

	private Thread udpServerThread;

	private ExecutorService udpServerExecutor;
	private ExecutorService udpClientExecutor;

	/*
	 * Their are two type of udp message 
	 * 1. b:port b = broadcast 
	 * 2. r:port r = reply to broadcast
	 */

	public NetworkUtil(@Value("${server.port}") Integer serverPort,
			@Value("${server.port}") Integer udpPort) throws UnknownHostException, SocketException {

		this.serverPort = serverPort;

		this.udpPort = udpPort;

		client = RestClient.create();

		this.broadcastIp = InetAddress.getByAddress(new byte[] { (byte) 255, (byte) 255, (byte) 255, (byte) 255 });

		udpServer = new DatagramSocket(udpPort);

		udpServerExecutor = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable);
			thread.setName("UDP Server Thread");
			thread.setDaemon(true);
			return thread;
		});

		udpClientExecutor = Executors.newSingleThreadExecutor(runnable -> {
			Thread thread = new Thread(runnable);
			thread.setName("UDP Client Thread");
			thread.setDaemon(true);
			return thread;
		});

	}

	public void startListening() {

		Runnable server = () -> {

			log.info("Starting sync app discovery service over lan");
			
			while (!Thread.interrupted()) {
				try {
					DatagramPacket buf = new DatagramPacket(new byte[7], 7);

					udpServer.receive(buf);

					String data = new String(buf.getData(), buf.getOffset(), buf.getLength(),
							StandardCharsets.US_ASCII);

					String[] message = data.split(":");

					String host = buf.getAddress().getHostAddress();
					String port = message[1];

					if (message[0].equals("b")) {

//						IF IT WAS ME THE VALIDATE ONE IP
						if (localIpList.containsKey(host)) 
							localIpList.put(host, true);
//						IF IT WAS ANOTHER NODE BROADCAST THEN SEND ATTENDENCE
						else
							sendAttendance(buf.getAddress());
					}
//					IF IT WAS A REPLY MESSAGE THE PUT THIS HOST TO ACTIVE CLIENTS
					else
						activeNodes.put(host, Integer.parseInt(port));
					
					System.out.println("Greetings from: " + host + ":" + port);

				} catch (IOException e) {
					log.error(e);
					continue;
				}
			}

			log.info("Closing Discovery Server");

			udpServer.close();

		};

		this.udpServerThread = new Thread(server);

		udpServerExecutor.execute(this.udpServerThread);
	}

//	Send echo message with this server port and wait for active nodes reply
	public void sendBroadcast() {
		Runnable broadcast = () -> {
			try {
				byte[] data = ("b:" + serverPort + "").getBytes(StandardCharsets.US_ASCII);

				udpServer.send(new DatagramPacket(data, data.length, broadcastIp, udpPort));
			} catch (Exception e) {
				System.out.println("Failed to send broadcast");
			}
		};

		System.out.println("Sending Broadcast");
		udpClientExecutor.execute(broadcast);
	}

	public void sendAttendance(InetAddress address) {
		Runnable echo = () -> {
			try {
				byte[] data = ("r:" + serverPort + "").getBytes(StandardCharsets.US_ASCII);

				udpServer.send(new DatagramPacket(data, data.length, address, udpPort));
			} catch (Exception e) {
				System.out.println("Failed to send reply");
			}
		};

		System.out.println("Sending reply");
		udpClientExecutor.execute(echo);
	}
	
	public Map<String, Boolean> getLocalIpList() {
		if (localIpList.isEmpty()) {
			return fetchLocalIpList();
		}		
		return localIpList;
	}

//	FETCH ALL LOCAL IP AND STORE FOR LATER USE
	public Map<String, Boolean> fetchLocalIpList() {

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

		if (map.isEmpty())
			System.out.println("No site local ip found. Are you connected to any access point?");

		localIpList.clear();
		localIpList.putAll(map);

		System.out.println("IP found in LAN\n" + localIpList);

		return map;
	}

	public void refreshServerList() {
		//	REMOVE PREVIOUS SERVER 
		activeNodes.clear();

		//	NOW SEND ECHO SIGNAL AND WAIT FOR ACTIVE NODES
		sendBroadcast();
	}

	public List<FileListJson> fetchFileListFromEveryone() {
		
		List<FileListJson> list = new ArrayList<>();

		for (Map.Entry<String, Integer> entry : activeNodes.entrySet()) {
			String host = entry.getKey();
			Integer port = entry.getValue();
			
			list.add(fetchFileList("http://" + host + ":" + port));
		}
		
		return list;
	}

//	OLD METHOD WITHOUT ERROR HANDLING
//	public FileListJson fetchFileList(String url) {
//
//		return WebClient
//				.create()
//				.get()
//				.uri(url + "/files/refresh")
//				.accept(MediaType.APPLICATION_JSON)
//				.retrieve().bodyToMono(FileListJson.class)
//				.block();		
//	}

//	TODO NEW METHOD
	public FileListJson fetchFileList(String url) {
		
		return client.get().uri(url + "/files/refresh")
				.accept(MediaType.APPLICATION_JSON)
				.retrieve()
				.body(FileListJson.class);
	}
	
//	List<Article> articles = restClient.get()
//			  .uri(uriBase + "/articles")
//			  .retrieve()
//			  .body(new ParameterizedTypeReference<>() {});
	
	
//	public Mono<Void> downloadFileReactively(String remoteFileUrl, String filename) {
//		System.out.println("Remote URL: " + remoteFileUrl + " Local Filename: " + filename);
//
//		Path dest = fs.getSyncDir().resolve(filename);
//
//		return webClient.get().uri(remoteFileUrl).retrieve().bodyToMono(byte[].class).flatMap(data -> {
//			try {
//				Files.write(dest, data, StandardOpenOption.CREATE_NEW);
//				return Mono.empty(); // Signal success
//			} catch (IOException e) {
//				return Mono.error(e); // Propagate any exceptions
//			}
//		})
//		.doOnNext(unused -> System.out.println(filename + " downloaded successfully."))
//		.doOnError(throwable -> System.err.println("Failed to download " + filename + ": " + throwable.getMessage()))
//		.then();
//	}

}

//DEVICE OVER LAN WITH THIS SOFTWARE INSTALLED
//private final ConcurrentLinkedQueue<String> syncServers = new ConcurrentLinkedQueue<>();

//public void doSomething() {
////Map<String, CompletableFuture<Integer>> response = new HashMap<>();
//Map<String, Mono<HttpStatusCode>> response = new HashMap<>();
//
////SEND ECHO TO ALL FOUND IP
//for (Map.Entry<String, Integer> entry : activeNodes.entrySet()) {
//
//String url = "http://" + entry.getKey() + ":" + entry.getValue();
//
////response.put(url, pingServer(url));
//response.put(url, pingServerReactively(url));
//}
//
////IF ANY IP RETURN 200 THEN PUT IT IN THE ONLINE SERVER LIST
//syncServers.clear();
//
//for (Map.Entry<String, Mono<HttpStatusCode>> entry : response.entrySet()) {
//if (entry.getValue().block().is2xxSuccessful())
//	syncServers.add(entry.getKey());
//}
//
////for (Map.Entry<String, CompletableFuture<Integer>> entry : response.entrySet()) {
////Integer status = 404;
////try {
////	status = entry.getValue().get();
////} catch (InterruptedException | ExecutionException e) {
////	e.printStackTrace();
////}
////
////if (status == 200)
////	syncServers.add(entry.getKey());
////}
//
//System.out.println(syncServers);
//}

//public Mono<HttpStatusCode> pingServerReactively(String url) {
//return webClient.get().uri(url + "/").retrieve().toBodilessEntity()
//	.map(responseEntity -> responseEntity.getStatusCode()).onErrorReturn(HttpStatusCode.valueOf(404));
//}

//public CompletableFuture<Integer> pingServer(String url) {
//HttpRequest request = HttpRequest.newBuilder()
//				.uri(URI.create(url + "/"))
//				.timeout(Duration.ofSeconds(15))
//				.GET()
//				.build();
//
//return httpClient
//	.sendAsync(request, HttpResponse.BodyHandlers.ofString())
//	.thenApply(HttpResponse::statusCode)
//	.exceptionally(ex -> 404);
//}

//public void startDiscoveryServer() throws IOException {
//
//	// Open a DatagramChannel
//	DatagramChannel channel = DatagramChannel.open();
//
//	// Bind the channel to a specific address (optional)
//	channel.bind(new InetSocketAddress(udpPort));
//
//	// Create a buffer to hold the data
//	ByteBuffer buffer = ByteBuffer.allocate(5);
//
//	// Define a task to run in the daemon thread
//	Runnable udpServer = () -> {
//		
//		try {				
//            String message = "9005";
//            byte[] sendData = message.getBytes();
//            
//            ByteBuffer buf = ByteBuffer.wrap(sendData);
//
//            channel.send(buf, new InetSocketAddress("255.255.255.255", udpPort+1));
//			} catch (Exception e) {
//				e.printStackTrace();
//				System.out.println("Failed to send message through channel");
//			}
//
//		while (!Thread.interrupted()) {
//			try {
//				// Clear the buffer for receiving
//				buffer.clear();
//
//				// Receive a message
//				channel.receive(buffer);
//				buffer.flip();
//
//				// Extract and display the received message
//				String receivedMessage = new String(buffer.array(), 0, buffer.limit());
//				System.out.println("Received message: " + receivedMessage);
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//		log.info("Closing UDP Channel");
//	};
//
//	// Submit the receiver task to the executor service
//	executor0.submit(udpServer);
//
//	System.out.println("UDP Channel Server is running and listening for messages...");
//}

//public void downloadFileSynchronously(String remoteFileUrl, String filename) throws IOException {
//
//	System.out.println("Remote Filecode: " + remoteFileUrl + " Local File: " + filename);
//
//	HttpClient httpClient = HttpClient.newBuilder().build();
//
//	HttpRequest request = HttpRequest.newBuilder().uri(URI.create(remoteFileUrl)).build();
//
//	Path dest = fs.getSyncDir().resolve(filename);
//
//	int status = 500;
//
//	try {
//		status = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(dest, StandardOpenOption.CREATE_NEW))
//				.statusCode();
//	} catch (IOException | InterruptedException e) {
//		e.printStackTrace();
//		System.err.println("Failed to download " + filename);
//	}
//
//	if (status == 200)
//		System.out.println(filename + " downloaded successfully.");
//
//}

//.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
//.thenApply(HttpResponse::body)
//.thenAccept(responseBody -> {
//	try {
////		THIS CODE SHOULD BE MORE PERFORMANT
//		Files.copy(responseBody, dest, StandardCopyOption.REPLACE_EXISTING);
//	} catch (IOException e) {
//		e.printStackTrace();
//	}
//		
//});

//httpClient
//.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
//.thenApply(HttpResponse::body)
//.thenAccept(responseBody -> {
//	try (BufferedInputStream reader = new BufferedInputStream(responseBody, 32768);
//		BufferedOutputStream writer = new BufferedOutputStream(new FileOutputStream(d), 32768)) {
//		
//		reader.transferTo(writer);
//	} catch (IOException e) {
//		e.printStackTrace();
//	}
//		
//});

//udpServer.setSoTimeout(15000);
