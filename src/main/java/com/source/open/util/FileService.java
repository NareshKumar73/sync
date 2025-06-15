package com.source.open.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;

import org.springframework.stereotype.Service;

import com.source.open.payload.FileMeta;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
@Service
public class FileService {

	private final Path BASE_PATH;

	private final Path syncDir;

	private final Encoder base64Encoder;

	private MessageDigest digest;

//	FILE CODE - FILE META
	private final LinkedHashMap<String, FileMeta> localFiles;

	private final Comparator<FileMeta> modifiedDate = Comparator.comparingLong(FileMeta::getLastModifiedEpoch)
			.reversed();

	public FileService() throws IOException {

		BASE_PATH = Path.of(System.getProperty("user.dir")).toAbsolutePath();

		syncDir = createFolder(BASE_PATH.resolve("resource-for-sync-app"));

		base64Encoder = Base64.getUrlEncoder().withoutPadding();

		localFiles = new LinkedHashMap<>();
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
		return "%.1f %ciB".formatted(value / 1024.0, ci.current());
	}

	public List<FileMeta> refreshFileList() {

		List<FileMeta> files = new ArrayList<>();

		localFiles.clear();

		try {
			Files.walk(syncDir) // NOW SUPPORT SUB DIRECTORY ACCESS
//			.list(syncDir)	OLD METHOD FOR SINGLE DIRECTORY ACCESS
					.filter(p -> Files.isRegularFile(p)).forEach(path -> {

						File f = path.toFile();

						String urlSafeFilename = new String(base64Encoder.encode(getHashLength8(f)));

						String downloadLink = "/download?filecode=" + urlSafeFilename;
//						String downloadLink = "/resource?filecode=" + urlSafeFilename;
//				String downloadLink = "/part?filecode=" + urlSafeFilename;
//				String downloadLink = "http://" + host + ":" + port + "/dl?filecode=" + urlSafeFilename;
//				String downloadLink = "http://" + host + ":" + port + "/download?filecode=" + urlSafeFilename;

						FileMeta fm = new FileMeta(downloadLink, urlSafeFilename, f.getName(),
								syncDir.relativize(path.getParent()).toString(), friendlyFileSize(f.length()),
								new Date(f.lastModified()).toString(), f.length(), f.lastModified(), path);

						localFiles.put(urlSafeFilename, fm);
						files.add(fm);
					});

		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Failed to read file from file system.");
		}

		files.sort(modifiedDate);

		if (log.isDebugEnabled()) {
			files.forEach(log::debug);
		}

		return files;
	}

	public List<FileMeta> getLocalFilesList() {

		if (localFiles.isEmpty())
			return refreshFileList();

		return localFiles.values().stream().sorted(modifiedDate).toList();
	}

	public byte[] getHashLength8(File f) {
		byte[] hashBytes = getHash(f);
		byte[] shortHash = new byte[8]; // 8 bytes = 64 bits
		
		System.arraycopy(hashBytes, 0, shortHash, 0, shortHash.length);

		return shortHash;
	}

	public byte[] getHash(File f) {
		try {
			if (this.digest == null)
				this.digest = MessageDigest.getInstance("SHA3-256");

			return digest.digest(f.getName().getBytes(StandardCharsets.UTF_8));

		} catch (Exception e) {
			log.error(e);
		}

		return new byte[] {};
	}

//	public String getHash(File f) {
//		try {
//			if (this.digest == null)
//				this.digest = MessageDigest.getInstance("SHA3-256");
//
//			final byte[] hashbytes = digest.digest(f.getName().getBytes(StandardCharsets.UTF_8));
//
//			return bytesToHex(hashbytes);
//		} catch (Exception e) {
//			log.error(e);
//		}
//		return "";
//	}

//	private String bytesToHex(byte[] hash) {
//	StringBuilder hexString = new StringBuilder(2 * hash.length);
//	for (int i = 0; i < hash.length; i++) {
//		String hex = Integer.toHexString(0xff & hash[i]);
//		if (hex.length() == 1) {
//			hexString.append('0');
//		}
//		hexString.append(hex);
//	}
//	return hexString.toString();
//}

}
