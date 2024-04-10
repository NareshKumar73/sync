package com.source.open.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Base64.Encoder;
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

//	FILE CODE - FILE META
	private final LinkedHashMap<String, FileMeta> localFiles;

	private final Comparator<FileMeta> modifiedDate = Comparator.comparingLong(FileMeta::getLastModifiedEpoch)
			.reversed();

	public FileService() throws IOException {

		BASE_PATH = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

		syncDir = createFolder(BASE_PATH.resolve("sync-resource"));

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
		return String.format("%.1f %ciB", value / 1024.0, ci.current());
	}

	public List<FileMeta> refreshFileList() {

		List<FileMeta> files = new ArrayList<>();

		localFiles.clear();

		try {
			Files.walk(syncDir) // NOW SUPPORT SUB DIRECTORY ACCESS
//			.list(syncDir)	OLD METHOD FOR SINGLE DIRECTORY ACCESS
					.filter(p -> Files.isRegularFile(p)).forEach(path -> {

						File f = path.toFile();

						String urlSafeFilename = new String(base64Encoder.encode(f.getName().getBytes()));

						String downloadLink = "/resource?filecode=" + urlSafeFilename;
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

	public String getHash(Path p) {

		String sha256Hash = "";

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA3-256");

			try (InputStream inputStream = new FileInputStream(p.toFile())) {
				// Wrap the FileInputStream with DigestInputStream
				DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest);

				// Read the file in chunks and update the digest
				byte[] buffer = new byte[1048576]; // Adjust the buffer size as needed
				while (digestInputStream.read(buffer) != -1) {
					// Reading automatically updates the digest
				}

				// Get the computed hash value
				byte[] hashBytes = digest.digest();

				// Convert the hash bytes to a hexadecimal string
				StringBuilder hexString = new StringBuilder();
				for (byte hashByte : hashBytes) {
					String hex = Integer.toHexString(0xff & hashByte);
					if (hex.length() == 1) {
						hexString.append('0');
					}
					hexString.append(hex);
				}

				sha256Hash = hexString.toString();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}

//		System.out.println("SHA-256 Hash of the file: " + sha256Hash);

		return sha256Hash;
	}

}

////NEW CODE
//private final Path tempDir;
//
//tempDir = syncDir.resolveSibling(".tmp");
//
//public Mono<Void> mergeChunks(String filename) {
//    Path mergedFile = syncDir.resolve(filename);
//    try (OutputStream outputStream = Files.newOutputStream(mergedFile)) {
//        int i = 0;
//        while (true) {
//            Path tempFile = tempDir.resolve(filename + ".part-" + i);
//            if (!Files.exists(tempFile)) {
//                break; // No more chunks to merge
//            }
//            Files.copy(tempFile, outputStream);
//            Files.delete(tempFile); // Delete temporary chunk after merging
//            i++;
//        }
//    } catch (IOException e) {
//    	return Mono.error(e);
//    }
//    
//    return Mono.empty(); // Signal successful merging
//}
//
//public Mono<Void> writeChunk(String filename, DataBuffer dataBuffer) {
//    Path tempFile = tempDir.resolve(filename);
//    return Mono.fromRunnable(() -> {
//        try {
//            byte[] bytes = new byte[dataBuffer.readableByteCount()]; // Allocate byte array based on DataBuffer size
//            dataBuffer.read(bytes); // Read data from DataBuffer into byte array
//            Files.write(tempFile, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
//        } catch (IOException e) {
//            throw new RuntimeException("Error writing chunk: " + e.getMessage(), e);
//        } finally {
////            dataBuffer.release();
//        }
//    });
//}
