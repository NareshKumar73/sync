package com.source.open;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.source.open.exception.ResourceNotFoundException;
import com.source.open.payload.FileMeta;

@SpringBootApplication
public class SyncApplication implements CommandLineRunner {

	@Autowired
	private ClientService cs;

	@Autowired
	private FileService fs;

	private String host = "";

	public final ConcurrentLinkedQueue<String> syncServers = new ConcurrentLinkedQueue<>();

	public static void main(String[] args) {
		SpringApplication.run(SyncApplication.class, args);
	}

	@Override
	public void run(String... args) {

		System.out.print("WELCOME TO SPRING SYNC APP 0.1\n");

		host = fs.getHost();

		System.out.println("MY IP: " + host);

		System.out.println("SYNC SERVICE WITHOUT DISCOVERY AND SYNC FEATURE.\nFILE SHARING SUPPORT ONLY.");

	}
	
	public void refreshServerList() {
		
	}

	public void syncFileWithServer(String ip) {
		
	}

	public void sync() {

		if (host.isBlank())
			throw new ResourceNotFoundException("Unable to fetch local ip of this machine.");

		String leftPart = host.substring(0, host.lastIndexOf('.') + 1);

		Map<String, CompletableFuture<Integer>> serverStatusMap = new HashMap<>();

//		SEND ONE REQUEST TO ALL IPs
		for (int i = 0; i < 255; i++) {

			String server_ip = leftPart + i;

			serverStatusMap.put(server_ip, cs.checkServer(server_ip));
		}

//		CLEAR PREVIOUS ONLINE SERVER LIST
		syncServers.clear();

//		IF ANY IP RETURN 200 THEN PUT IT IN THE ONLINE SERVER LIST
		for (Map.Entry<String, CompletableFuture<Integer>> entry : serverStatusMap.entrySet()) {
			Integer status = 404;
			try {
				status = entry.getValue().get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}

			if (status == 200)
				syncServers.add(entry.getKey());
		}

//		try {
//			Thread.sleep(25000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}

//		REFRESH LOCAL FILE LIST ONCE
		fs.refreshFileList();

//		FETCH FILE LIST FROM EACH SERVER AND PUT IT IN A LIST OF FILE URLs

//		FILE CODE - OBJECT
		Map<String, FileMeta> localFiles = fs.getLocalFiles();

//		FILECODE - FILE LIST
		Map<String, List<FileMeta>> downloadableFiles = new HashMap<>();
////		FILECODE - FILE LIST TODO IN FUTURE RELEASE WILL HAVE OPTION TO REPLACE EXISTING FILES WITH OTHER SERVER FILE 
//		Map<String, List<FileMeta>> duplicateFiles = new HashMap<>();

		syncServers.forEach(server_ip -> {
			cs.fetchFileList(server_ip).getFiles().forEach(file -> {

//				IF FILE IS NOT AVAILABLE LOCALLY OR IF FILE EXIST AND SIZE IS NOT SAME 
//				THEN DOWNLOAD
				if (!localFiles.containsKey(file.getCode())) {

					List<FileMeta> fl = new ArrayList<>();

					fl.add(file);

					downloadableFiles.put(file.getCode(), fl);
				}
////				A modified file is found on other server which is available on our system.
//				else if(localFiles.get(file.getCode()).getSizeInBytes() != file.getSizeInBytes()) {
//					duplicateFiles.get(file.getCode()).add(file);
//				}
			});
		});

		System.out.println(syncServers);

		System.out.println("FILES :\n" + downloadableFiles);

		downloadableFiles.forEach((fileCode, files) -> {

//			IF MORE THAN ONE FILE AVAILABLE FOR A FILECODE THEN TODO
//			FOR NOW DOWNLOAD FIRST FILE IN THE LIST

//			API CALL WAS NOT WAITING FOR COMPLETION - NOW USING BLOCING CODE INSTEAD
			FileMeta meta = files.get(0);

			try {
				cs.downloadFileSynchronously(meta.getUrl(), meta.getName());
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		System.out.println("FINISH");

//		EXTRA CODE
//		boolean syncServer = downloadService.isSyncServer("localhost");
//		
//		System.out.println("THE SERVER IS " + (syncServer? "ONLINE" : "OFFLINE"));

//		if (!Files.exists(baseDirPath)) {
//			Path path = Files.createDirectory(baseDirPath);
//			
//			System.out.println(path.toAbsolutePath().toString());
//		}
//		else
//			System.out.println("Sync Folder exist.");

	}

}

//private static List<String> getLocalIpList() {
//
//	List<String> ips = new ArrayList<>();
//	
//	Enumeration<NetworkInterface> e;
//	
//	try {
//		e = NetworkInterface.getNetworkInterfaces();
//		while (e.hasMoreElements()) {
//			NetworkInterface n = e.nextElement();
//			Enumeration<InetAddress> ee = n.getInetAddresses();
//			while (ee.hasMoreElements()) {
//				InetAddress i = ee.nextElement();
//				if (i.isSiteLocalAddress())
//					ips.add(i.getHostAddress());
//			}
//		}
//		
//	} catch (SocketException ex) {
//		// TODO Auto-generated catch block
//		ex.printStackTrace();
//	}
//	
//	return ips;
//}
//
//private static void printNetworkCard() {
//
//	Enumeration<NetworkInterface> e;
//	
//	try {
//		e = NetworkInterface.getNetworkInterfaces();
//		while (e.hasMoreElements()) {
//			NetworkInterface n = e.nextElement();
//			Enumeration<InetAddress> ee = n.getInetAddresses();
//			while (ee.hasMoreElements()) {
//				InetAddress i = ee.nextElement();
//				System.out.format("%-40s -> is site local %s\n", i.getHostAddress(), i.isSiteLocalAddress());
//			}
//		}
//		
//	} catch (SocketException ex) {
//		// TODO Auto-generated catch block
//		ex.printStackTrace();
//	}
//
//}
//System.out.print(String.format("%03d",i) + " ");
//
//try (final DatagramSocket datagramSocket = new DatagramSocket()) {
//    datagramSocket.connect(InetAddress.getByName("8.8.8.8"), 12345);
//    System.out.println("MY IP: " + datagramSocket.getLocalAddress().getHostAddress());
//} catch (SocketException | UnknownHostException ex) {
//	ex.printStackTrace();
//}
//
//try {
//	NetworkInterface local = NetworkInterface.getByName("lo");
//	
//	local.getInterfaceAddresses().forEach(item -> System.out.println(item.getNetworkPrefixLength()));
//	
//} catch (SocketException e) {
//	// TODO Auto-generated catch block
//	e.printStackTrace();
//}
