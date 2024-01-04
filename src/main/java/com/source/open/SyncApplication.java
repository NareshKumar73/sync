package com.source.open;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import com.source.open.exception.ResourceNotFoundException;
import com.source.open.payload.FileMeta;
import com.source.open.util.ClientService;
import com.source.open.util.FileService;
import com.source.open.util.NetworkUtil;
import com.source.open.util.ServerSearch;

import jakarta.annotation.PreDestroy;

@SpringBootApplication
public class SyncApplication implements CommandLineRunner {

	@Autowired
	private ClientService cs;

	@Autowired
	private FileService fs;

	@Autowired
	private NetworkUtil nu;
	
	@Autowired
	private ApplicationContext appContext;
	
	private Thread discoveryServer;
	
	public static void main(String[] args) {
		SpringApplication.run(SyncApplication.class, args);
	}
	
	@PreDestroy
	public void destroy() {
		nu.getUdpServer().close();
	}

	@Override
	public void run(String... args) {
		
		if (nu.getLocalIpList().isEmpty()) 
			nu.discoverLocalIpList();

//		IF NO IP FOUND THEN EXIT
		if (nu.getLocalIpList().isEmpty()) {
			System.out.println("NO IP FOUND. ARE YOU CONNECTED TO THE ROUTER OR WIFI.");
			
			SpringApplication.exit(appContext, () -> {
				return 9;
			});
			
			System.exit(9);
		}
		
		discoveryServer = new Thread(new ServerSearch(nu));

		discoveryServer.setDaemon(true);

		discoveryServer.start();
		
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}

		System.out.println("WELCOME TO SYNC APP v0.1");

		System.out.println("SYNC SERVICE WITHOUT DISCOVERY AND SYNC FEATURE.\nFILE SHARING SUPPORT ONLY.");
		
		refreshServerList();
		
		sync();
	}
	
	public void refreshServerList() {
////		REMOVE PREVIOUS SERVER 
////		NOT NEEDED JUST CALL ON THEIR ENDPOINT AND REMOVE THEM
//		nu.getServers().clear();

//		NOW SEND ECHO SIGNAL AND WAIT FOR ACTIVE NODES
		nu.sendEcho();

		Map<String,CompletableFuture<Integer>> response = new HashMap<>();

//		SEND ECHO TO ALL FOUND IP
		for (Map.Entry<String, Integer> entry : nu.getActiveNodes().entrySet()) {

			String url = "http://" + entry.getKey() + ":" + entry.getValue();
			
			response.put(url, cs.pingServer(url));
		}
				
//		IF ANY IP RETURN 200 THEN PUT IT IN THE ONLINE SERVER LIST
		for (Map.Entry<String, CompletableFuture<Integer>> entry : response.entrySet()) {
			Integer status = 404;
			try {
				status = entry.getValue().get();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}

			if (status == 200)
				nu.getSyncServers().add(entry.getKey());
		}
		
		System.out.println(nu.getSyncServers());
	}

	public void syncFileWithServer(String ip) {
		
	}
	
	public void sync() {

		if (nu.getLocalIpList().isEmpty())
			throw new ResourceNotFoundException("Unable to fetch local ip of this machine.");

//		------------------------------		REFRESH LOCAL FILE LIST ONCE		------------------------------
		fs.refreshFileList();

//		FILE CODE - OBJECT
		Map<String, FileMeta> localFiles = fs.getLocalFiles();
		
		
//		------------------------------	PING IP LIST AND POPULATE SYNC SERVER	------------------------------

//		------------------------------			FETCH DOWNLOADABLE FILE			------------------------------
		
//		FETCH FILE LIST FROM EACH SERVER AND PUT IT IN A LIST OF FILE URLs
//		URL - FILES
//		ONLY FILES THAT ARE NOT ALREADY AVAILABLE IS DOWNLOADED IN THIS RELEASE
		Map<String, List<FileMeta>> downloadableFiles = new HashMap<>();
		
		nu.getSyncServers().forEach(url -> {
			downloadableFiles.put(url, new ArrayList<>());
		});
		
//		FILECODE - FILE LIST TODO IN FUTURE RELEASE WILL HAVE OPTION TO REPLACE EXISTING FILES WITH OTHER SERVER FILE 
		

		nu.getSyncServers().forEach(url -> {
			cs.fetchFileList(url).getFiles().forEach(file -> {

//				IF FILE IS NOT AVAILABLE LOCALLY THEN ADD TO DOWNLOAD LIST
				if (!localFiles.containsKey(file.getCode())) {

					downloadableFiles.get(url).add(file);
				}
			});
		});

		System.out.println("FILES :\n" + downloadableFiles);
		
//		DOWNLOAD ALL FILES IN DOWNLOAD LIST
		downloadableFiles.forEach((baseUrl, files) -> {
			files.forEach(file -> {
				try {
					cs.downloadFileSynchronously(baseUrl + file.getUrl(), file.getName());
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("Failed to download " + file.getName() + " from server " + baseUrl);
				}
			});
		});

//		DONE
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

//Map<String, List<FileMeta>> duplicateFiles = new HashMap<>();

////A modified file is found on other server which is available on our system.
//else if(localFiles.get(file.getCode()).getSizeInBytes() != file.getSizeInBytes()) {
//duplicateFiles.get(file.getCode()).add(file);
//}



//String leftPart = host.substring(0, host.lastIndexOf('.') + 1);

//Map<String, CompletableFuture<Integer>> serverStatusMap = new HashMap<>();

//SEND ONE REQUEST TO ALL IPs
//for (int i = 0; i < 255; i++) {
//
//	String server_ip = leftPart + i;
//
//	serverStatusMap.put(server_ip, cs.checkServer(server_ip));
//}


//try {
//	Thread.sleep(25000);
//} catch (InterruptedException e) {
//	e.printStackTrace();
//}


//downloadableFiles.forEach((fileCode, files) -> {
//
////	IF MORE THAN ONE FILE AVAILABLE FOR A FILECODE THEN TODO
////	FOR NOW DOWNLOAD FIRST FILE IN THE LIST
//
////	API CALL WAS NOT WAITING FOR COMPLETION - NOW USING BLOCING CODE INSTEAD
//	FileMeta meta = files.get(0);
//
//	try {
//		cs.downloadFileSynchronously(meta.getUrl(), meta.getName());
//	} catch (IOException e) {
//		e.printStackTrace();
//	}
//});