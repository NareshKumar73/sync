package com.source.open;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContext;

import com.source.open.payload.FileMeta;
import com.source.open.util.FileService;
import com.source.open.util.NetworkUtil;


@SpringBootApplication
public class SyncApplication implements CommandLineRunner {

	@Autowired
	private FileService fs;

	@Autowired
	private NetworkUtil nu;

	@Autowired
	private ApplicationContext context;

	public static void main(String[] args) {
		SpringApplication.run(SyncApplication.class, args);
	}

	@Override
	public void run(String... args) throws IOException {
		
		try {
			Thread.sleep(Duration.ofSeconds(5));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println("WELCOME TO SYNC APP v0.1");

		System.out.println("SYNC SERVICE WITHOUT DISCOVERY AND SYNC FEATURE.\nFILE SHARING SUPPORT ONLY.");
		
		fs.refreshFileList();

//		TODO Working now store these hashes in filename.txt to not generate hash again
//		List<FileMeta> fileList = fs.refreshFileList();
//		fileList.forEach(file -> System.out.println("Filename: " + file.getName() + "\nhash: " + fs.getHash(file.getPath())));

//		nu.fetchLocalIpList();
//		EXIT CODE 1 = NO IP FOUND VERY IMPORTANT
		if (nu.getLocalIpList().isEmpty()) {
			System.out.println("No IP found. Check your network card or wifi connection.");
			
			SpringApplication.exit(context, () -> {
				return 1;
			});
			
			System.exit(1);
		}
		
		nu.startListening();

		nu.refreshServerList();
		
//		sync();
	}

	public void sync() {
//		------------------------------		REFRESH LOCAL FILE LIST ONCE		------------------------------
		fs.refreshFileList();

//		FILE CODE - OBJECT
//		Map<String, FileMeta> localFiles = fs.getLocalFiles();

//		------------------------------	PING IP LIST AND POPULATE SYNC SERVER	------------------------------

//		------------------------------			FETCH DOWNLOADABLE FILE			------------------------------

//		FETCH FILE LIST FROM EACH SERVER AND PUT IT IN A LIST OF FILE URLs
//		URL - FILES
//		ONLY FILES THAT ARE NOT ALREADY AVAILABLE IS DOWNLOADED IN THIS RELEASE
		Map<String, List<FileMeta>> downloadableFiles = new HashMap<>();

//		nu.getSyncServers().forEach(url -> {
//			downloadableFiles.put(url, new ArrayList<>());
//		});
//
////		FILECODE - FILE LIST TODO IN FUTURE RELEASE WILL HAVE OPTION TO REPLACE EXISTING FILES WITH OTHER SERVER FILE 
//
//		nu.getSyncServers().forEach(url -> {
//			nu.fetchFileList(url).getFiles().forEach(file -> {
//
////				IF FILE IS NOT AVAILABLE LOCALLY THEN ADD TO DOWNLOAD LIST
//				if (!localFiles.containsKey(file.getCode())) {
//
//					downloadableFiles.get(url).add(file);
//				}
//			});
//		});

		System.out.println("FILES :\n" + downloadableFiles);

//		DOWNLOAD ALL FILES IN DOWNLOAD LIST
		downloadableFiles.forEach((baseUrl, files) -> {
			files.forEach(file -> {
//				try {
//					nu.downloadFileSynchronously(baseUrl + file.getUrl(), file.getName());
					nu.downloadFileReactively(baseUrl + file.getUrl(), file.getName());
//				} catch (IOException e) {
//					e.printStackTrace();
//					System.err.println("Failed to download " + file.getName() + " from server " + baseUrl);
//				}
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
////	TODO IF MORE THAN ONE FILE AVAILABLE FOR A FILECODE THEN FOR NOW DOWNLOAD FIRST FILE IN THE LIST
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