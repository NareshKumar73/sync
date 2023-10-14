package com.source.open;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SyncApplication implements CommandLineRunner {
	
	// @Autowired
	// private ClientService cs;
	
	@Autowired
	private FileService fs;
	
	public final ConcurrentLinkedQueue<String> syncServers = new ConcurrentLinkedQueue<>();

	public static void main(String[] args) {
		SpringApplication.run(SyncApplication.class, args);
	}

	@Override
	public void run(String... args) {
		
		System.out.print("WELCOME TO SPRING SYNC APP\n");

		String host = fs.getHost();
		
		System.out.println("MY IP: " + host);
		
//		String leftPart = host.substring(0, host.lastIndexOf('.') + 1);
//
//		Map<String, CompletableFuture<Integer>> serverStatusMap = new HashMap<>();
//
//		for (int i = 0; i < 255; i++) {
//			String server_ip = leftPart + i;
//
//			serverStatusMap.put(server_ip, cs.checkServer(server_ip));
//		}
//
//		for (Map.Entry<String, CompletableFuture<Integer>> entry : serverStatusMap.entrySet()) {
//			Integer status = 404;
//			try {
//				status = entry.getValue().get();
//			} catch (InterruptedException | ExecutionException e) {
//				e.printStackTrace();
//			}
//
//			if (status == 200)
//				syncServers.add(entry.getKey());
//		}
//
//		try {
//			Thread.sleep(25000);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
//		
//		Map<String, FileMeta> localFileMap = new HashMap<>();
//		
//		cs.fetchFileList(host).getFiles().forEach(file -> {
//			localFileMap.put(file.getFilecode(), file);
//		});
//		
//		Map<String, List<FileMeta>> files = new HashMap<>();
//		
//		syncServers.forEach(server_ip -> {
//			cs.fetchFileList(server_ip).getFiles().forEach(file -> {
//				
////				IF FILE IS NOT AVAILABLE LOCALLY OR IF FILE EXIST AND SIZE IS NOT SAME 
////				THEN DOWNLOAD
//				if (!localFileMap.containsKey(file.getFilecode()) || 
//					localFileMap.get(file.getFilecode()).getSizeInBytes() != file.getSizeInBytes()) {
//
//					if (!files.containsKey(server_ip))
//						files.put(server_ip, new ArrayList<>());
//
//					files.get(server_ip).add(file);
//				}
//				
//			});
//		});
//		
//		System.out.println(syncServers);
//		
//		System.out.println("FILES :\n" + files);
//		
//		files.forEach((k,v) -> {
//			String serverUrl = "http://" + k + ":" + cs.getPort() + "/download";
//			
////			API CALL WAS NOT WAITING FOR COMPLETION - NOW USING BLOCING CODE INSTEAD
//			v.forEach(fileMeta -> {
//				try {
//					cs.downloadFileSynchronously(serverUrl + "?filecode=" + fileMeta.getFilecode(), fs.getSyncDir().resolve(fileMeta.getName()));
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			});
//		});
//		
//		System.out.println("FINISH");

		
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

//System.out.print(String.format("%03d",i) + " ");
