package com.source.open.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
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
import java.util.stream.Stream;

import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;

import com.source.open.payload.FileMeta;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Getter
@Service
public class FileService {

	// Present Working Directory - The directory from where java -jar was called to
	// run the Application
	private final Path pwd;

	private final Path data;

	private final Path appDir;

	private final Encoder base64Encoder;

	private MessageDigest digest;

	// FILE CODE - FILE META
	private final LinkedHashMap<String, FileMeta> localFiles;

	private final Comparator<FileMeta> modifiedDate = Comparator.comparingLong(FileMeta::getLastModifiedEpoch)
			.reversed();

	public FileService() throws IOException {

		pwd = Path.of(System.getProperty("user.dir")).toAbsolutePath();

		data = createFolder(pwd.resolve("data"));

		appDir = createFolder(data.resolve("shared"));

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

		localFiles.clear();

		return listDirectory(null);

		// try {
		// Files.walk(appDir) // NOW SUPPORT SUB DIRECTORY ACCESS
		// // .list(syncDir) OLD METHOD FOR SINGLE DIRECTORY ACCESS
		// .filter(Files::isRegularFile).forEach(path -> {
		//
		// String name = path.getFileName().toString();
		//
		// BasicFileAttributes attrs = Files.readAttributes(path,
		// BasicFileAttributes.class);
		// long size = attrs.size();
		// FileTime time = attrs.lastModifiedTime();
		//
		// FileMeta fm = new FileMeta();
		// fm.setName(name);
		// fm.setRelativePath(base.relativize(path).toString());
		// try {
		// fm.setLastModifiedEpoch(Files.getLastModifiedTime(path).toMillis());
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		// fm.setLastModified(new Date(fm.getLastModifiedEpoch()).toString());
		// fm.setPath(path);
		//
		// String urlSafeFilename = new
		// String(base64Encoder.encode(getHashLength8(name)));
		//
		// if (Files.isDirectory(path)) {
		// fm.setDirectory(true);
		//
		// fm.setUrl("");
		// fm.setSize("-");
		// } else {
		// fm.setDirectory(false);
		//
		// String downloadLink = "/download?filecode=" + urlSafeFilename;
		//
		// fm.setCode(urlSafeFilename);
		// fm.setUrl(downloadLink);
		//
		// long size = 0;
		// try {
		// size = Files.size(path);
		// } catch (IOException e) {
		// e.printStackTrace();
		// }
		// fm.setSize(friendlyFileSize(size));
		// fm.setSizeInBytes(size);
		//
		// MediaType mime = MediaTypeFactory.getMediaType(name)
		// .orElse(MediaType.APPLICATION_OCTET_STREAM);
		//
		// fm.setFileType(mime);
		// }
		//
		// result.add(fm);
		//
		// File f = path.toFile();
		//
		// String urlSafeFilename = new String(base64Encoder.encode(getHashLength8(f)));
		//
		// String downloadLink = "/download?filecode=" + urlSafeFilename;
		//// String downloadLink = "/resource?filecode=" + urlSafeFilename; String /
		/// downloadLink = "/part?filecode=" + urlSafeFilename;
		//
		// MediaType mime = MediaTypeFactory.getMediaType(f.getName())
		// .orElse(MediaType.APPLICATION_OCTET_STREAM);
		//
		//// if (MediaType.APPLICATION_OCTET_STREAM.equals(mime)) { / }
		//
		// String name = path.getFileName().toString();
		//
		// FileMeta fm = new FileMeta(downloadLink, urlSafeFilename, f.getName(),
		// syncDir.relativize(path.getParent()).toString(),
		// friendlyFileSize(f.length()),
		// new Date(f.lastModified()).toString(), f.length(), f.lastModified(), mime,
		// path);
		//
		// localFiles.put(urlSafeFilename, fm);
		// files.add(fm);
		// });
		//
		// } catch (IOException e) {
		// e.printStackTrace();
		// System.err.println("Failed to read file from file system.");
		// }
		//
		// files.sort(modifiedDate);
		//
		// if (log.isDebugEnabled()) {
		// files.forEach(log::debug);
		// }
	}

	public List<FileMeta> listDirectory(String relativePath) {

		Path currentDir = (relativePath == null || relativePath.isBlank()) ? appDir
				: appDir.resolve(relativePath).normalize();

		if (!currentDir.startsWith(appDir))
			throw new IllegalArgumentException("Invalid path");

		List<FileMeta> result = new ArrayList<>();

		try (Stream<Path> stream = Files.list(currentDir)) {

			stream.forEach(path -> {

				String name = path.getFileName().toString();

				BasicFileAttributes meta;
				long size = 0;
				long time = 0;
				boolean isDir = false;

				try {
					meta = Files.readAttributes(path, BasicFileAttributes.class);
					size = meta.size();
					time = meta.lastModifiedTime().toMillis();
					isDir = meta.isDirectory();
				} catch (IOException e) {
					e.printStackTrace();
				}

				FileMeta fm = new FileMeta();
				fm.setName(name);
				fm.setRelativePath(appDir.relativize(path).toString());
				fm.setLastModifiedEpoch(time);
				fm.setLastModified(new Date(time).toString());
				fm.setPath(path);

				String urlSafeFilename = new String(base64Encoder.encode(getHashLength8(name)));

				if (isDir) {
					fm.setDirectory(true);

					fm.setFileType("");
					// TODO Set Folder Zip Download URL
					fm.setUrl("");
					fm.setSize("-");
				} else {
					fm.setDirectory(false);

					String downloadLink = "/download?filecode=" + urlSafeFilename;

					fm.setCode(urlSafeFilename);
					fm.setUrl(downloadLink);

					fm.setSize(friendlyFileSize(size));
					fm.setSizeInBytes(size);

					MediaType mime = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);

					fm.setFileType(mime.getSubtype());
				}

				localFiles.put(urlSafeFilename, fm);

				result.add(fm);
			});

		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// folders first
		// result.sort(Comparator.comparing(FileMeta::isDirectory).reversed().thenComparing(FileMeta::getName,
		// String.CASE_INSENSITIVE_ORDER));
		result.sort(
				Comparator.comparing(FileMeta::isDirectory).reversed().thenComparing(FileMeta::getLastModifiedEpoch));

		return result;
	}

	public List<FileMeta> listDirectoryRecursive() {
		List<FileMeta> result = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(appDir)) {
			stream.filter(p -> !p.equals(appDir)).forEach(path -> {
				String name = path.getFileName().toString();
				BasicFileAttributes meta;
				long size = 0;
				long time = 0;
				boolean isDir = false;
				try {
					meta = Files.readAttributes(path, BasicFileAttributes.class);
					size = meta.size();
					time = meta.lastModifiedTime().toMillis();
					isDir = meta.isDirectory();
				} catch (IOException e) {
					e.printStackTrace();
				}
				FileMeta fm = new FileMeta();
				fm.setName(name);
				fm.setRelativePath(appDir.relativize(path).toString().replace("\\", "/"));
				fm.setLastModifiedEpoch(time);
				fm.setLastModified(new Date(time).toString());
				fm.setPath(path);
				String urlSafeFilename = new String(base64Encoder.encode(getHashLength8(fm.getRelativePath())));
				if (isDir) {
					fm.setDirectory(true);
					fm.setFileType("");
					fm.setUrl("");
					fm.setSize("-");
				} else {
					fm.setDirectory(false);
					String downloadLink = "/download?filecode=" + urlSafeFilename;
					fm.setCode(urlSafeFilename);
					fm.setUrl(downloadLink);
					fm.setSize(friendlyFileSize(size));
					fm.setSizeInBytes(size);
					MediaType mime = MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
					fm.setFileType(mime.getSubtype());
				}
				localFiles.put(urlSafeFilename, fm);
				result.add(fm);
			});
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		result.sort(
				Comparator.comparing(FileMeta::isDirectory).reversed().thenComparing(FileMeta::getLastModifiedEpoch));
		return result;
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

	public byte[] getHashLength8(String name) {
		byte[] hashBytes = getHash(name);
		byte[] shortHash = new byte[8]; // 8 bytes = 64 bits

		System.arraycopy(hashBytes, 0, shortHash, 0, shortHash.length);

		return shortHash;
	}

	public byte[] getHash(String name) {
		try {
			if (this.digest == null)
				this.digest = MessageDigest.getInstance("SHA3-256");

			return digest.digest(name.getBytes(StandardCharsets.UTF_8));

		} catch (Exception e) {
			log.error(e);
		}

		return new byte[] {};
	}

	// public String getHash(File f) {
	// try {
	// if (this.digest == null)
	// this.digest = MessageDigest.getInstance("SHA3-256");
	//
	// final byte[] hashbytes =
	// digest.digest(f.getName().getBytes(StandardCharsets.UTF_8));
	//
	// return bytesToHex(hashbytes);
	// } catch (Exception e) {
	// log.error(e);
	// }
	// return "";
	// }

	// private String bytesToHex(byte[] hash) {
	// StringBuilder hexString = new StringBuilder(2 * hash.length);
	// for (int i = 0; i < hash.length; i++) {
	// String hex = Integer.toHexString(0xff & hash[i]);
	// if (hex.length() == 1) {
	// hexString.append('0');
	// }
	// hexString.append(hex);
	// }
	// return hexString.toString();
	// }

}
