package com.source.open.extra;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.function.BiConsumer;

public class CustomFileReader {

	private static final Path BASE_PATH = Paths.get(System.getProperty("user.dir")).toAbsolutePath();

	public static void main(String[] args) throws IOException {

		Path syncDir = createNonExistingPathAndGet(BASE_PATH.resolve("shady-sync-folder"));

		Path dir1 = createNonExistingPathAndGet(syncDir.resolve("shady-sync-folder-1"));
		Path dir2 = createNonExistingPathAndGet(syncDir.resolve("shady-sync-folder-2"));

		System.out.println(BASE_PATH);
		System.out.println(dir1);
		System.out.println(dir2);

		System.out.println(dir1.resolve("jdownload.exe"));

		// creating a object of BasicFileAttributes
		BasicFileAttributes attr = Files.readAttributes(dir1.resolve("jdownload.exe"), BasicFileAttributes.class);
//		System.out.println("Creation = " + attr.creationTime());
//		System.out.println("LastAccess = " + attr.lastAccessTime());
//		System.out.println("LastModified = " + attr.lastModifiedTime());

		System.out.println("Size = " + humanReadableByteCount1024(attr.size()));
		System.out.println("isDirectory = " + attr.isDirectory());
		System.out.println("isRegularFile = " + attr.isRegularFile());
//		System.out.println("isOther = " + attr.isOther());
//		System.out.println("isSymbolicLink = " + attr.isSymbolicLink());

//		long time = getRuntimeMilis(dir1.resolve("jdownload.exe"), dir2.resolve("jdownload.exe"), CustomFileReader::readDirectFile);
//		163 seconds

//		long time = getRuntimeMilis(dir1.resolve("jdownload.exe"), dir2.resolve("jdownload.exe"), CustomFileReader::readBufferedFile);
//		60 ms
		
//		long time = getRuntimeMilis(dir1.resolve("docker.exe"), dir2.resolve("docker.exe"), CustomFileReader::readBufferedFile);
//		29 seconds using 8kb buffer
//		20 seconds using 32kb buffer
		
		long time = getRuntimeMilis(dir1.resolve("docker.exe"), dir2.resolve("docker.exe"), CustomFileReader::readChannelFile);
//		20 seconds using 32kb buffer		
		
		System.out.println("Time elapsed: " + time);
//		System.out.println("Time elapsed: " + Duration.ofMillis(time).toSeconds());
	}

	public static Path createNonExistingPathAndGet(Path p) throws IOException {
		return createNonExistingPathAndGet(p, false);
	}

	public static Path createNonExistingPathAndGet(Path p, boolean isFile) throws IOException {
//		Create directory if not exist
		if (!Files.exists(p)) {
			if (isFile)
				p = Files.createFile(p);
			else
				p = Files.createDirectory(p);
		}

		return p;
	}

	public static String humanReadableByteCount1000(long bytes) {
		if (-1000 < bytes && bytes < 1000) {
			return bytes + " B";
		}
		CharacterIterator ci = new StringCharacterIterator("kMGTPE");
		while (bytes <= -999_950 || bytes >= 999_950) {
			bytes /= 1000;
			ci.next();
		}
		return String.format("%.1f %cB", bytes / 1000.0, ci.current());
	}

	public static String humanReadableByteCount1024(long bytes) {
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

	public static long getRuntimeMilis(Path src, Path dest, BiConsumer<Path, Path> c) {

		long startTime = System.currentTimeMillis();

		c.accept(src, dest);

		long endTime = System.currentTimeMillis();

		return endTime - startTime;
	}

	public static void readDirectFile(Path src, Path dest) {
		int ch;

		File s = src.toFile();
		File d = null;

		try {
			d = createNonExistingPathAndGet(dest, true).toFile();
		} catch (IOException e) {
			e.printStackTrace();
		}

		FileInputStream fis = null;
		FileOutputStream fos = null;

		try {
			fis = new FileInputStream(s);
			fos = new FileOutputStream(d);

			while ((ch = fis.read()) != -1)
				fos.write(ch);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (fis != null)
					fis.close();
				if (fos != null)
					fos.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	
	public static void readBufferedFile(Path src, Path dest) {
		File s = src.toFile();
		File d = null;

		BufferedInputStream reader = null;
		BufferedOutputStream writer = null;
		
		try {
			
			d = createNonExistingPathAndGet(dest, true).toFile();

			reader = new BufferedInputStream(new FileInputStream(s), 32768);
			writer = new BufferedOutputStream(new FileOutputStream(d), 32768);
			
			reader.transferTo(writer);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null)
					reader.close();
				if (writer != null)
					writer.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	
	public static void readChannelFile(Path src, Path dest) {
		FileChannel in = null;
		FileChannel out = null;

		try {
			
			dest = createNonExistingPathAndGet(dest, true);

			in = FileChannel.open(src, StandardOpenOption.READ);
			out = FileChannel.open(dest, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			
			in.transferTo(0, in.size(), out);
			
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (in != null)
					in.close();
				if (out != null)
					out.close();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

}
