package com.source.open;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.source.open.payload.FileMeta;

import lombok.Getter;

@Getter
@Service
public class FileService {
	
	private final Path BASE_PATH;
	
	private final Path syncDir;
	
	private final Encoder base64Encoder;
	
	private final LinkedHashMap<String, FileMeta> localFiles;
	
	private String host;
	
	@Value("${server.port}")
	private Integer port;

	public FileService() throws IOException {
		super();
		
		BASE_PATH = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
		
		syncDir = createFolder(BASE_PATH.resolve("sync-resource"));
		
		base64Encoder = Base64.getUrlEncoder().withoutPadding();
		localFiles = new LinkedHashMap<>();
		
		host = InetAddress.getLocalHost().getHostAddress();
	}
	
	public Path createFolder(Path p) throws IOException {
		if (!Files.exists(p)) 
			p = Files.createDirectory(p);
		
		return p;
	}

	public Path createFile(Path p) throws IOException {
		if (!Files.exists(p)) 
			p = Files.createFile(p);
		return p;
	}
	
	public String friendlyFileSize(long bytes) {
		long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
		if (absB < 1024) {
			return bytes + " B";
		}
		long value = absB;
		CharacterIterator ci = new StringCharacterIterator("KMGTPE");
		for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
			value >>= 10;
			ci.next();
		}
		value *= Long.signum(bytes);
		return String.format("%.1f %ciB", value / 1024.0, ci.current());
	}
	
	public List<FileMeta> refreshFileList() {
		List<FileMeta> files = new ArrayList<>();

		localFiles.clear();

		try {
			Files
			.list(syncDir)
			.filter(p -> Files.isRegularFile(p))
			.forEach(path -> {
				File f = path.toFile();
				
				String urlSafeFilename = new String(base64Encoder.encode(f.getName().getBytes()));
				
//				String downloadLink = "http://" + host + ":" + port + "/dl?filecode=" + urlSafeFilename;
				String downloadLink = "http://" + host + ":" + port + "/part?filecode=" + urlSafeFilename;
//				String downloadLink = "http://" + host + ":" + port + "/download?filecode=" + urlSafeFilename;
		
						FileMeta fm = new FileMeta(
											downloadLink,
											urlSafeFilename, 
											f.getName(), 
											friendlyFileSize(f.length()),
											new Date(f.lastModified()).toString(), 
											f.length(), 
											f.lastModified(), 
											path);
				
				localFiles.put(urlSafeFilename, fm);
				files.add(fm);
			});
			
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Failed to read file from file system.");
		}
		
		return files;
	}
	
	public List<FileMeta> getLocalFilesList() {
		
		if(localFiles.isEmpty())
			return refreshFileList();
		
		return localFiles.values().stream().toList();
	}

}
