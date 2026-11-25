package com.source.open.util;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileServiceTest {

	@Test
	void listDirectoryRejectsPathEscapingAppDir(@TempDir Path workDir) throws IOException {
		String originalUserDir = System.getProperty("user.dir");
		System.setProperty("user.dir", workDir.toString());
		try {
			FileService fs = new FileService();
			assertThatThrownBy(() -> fs.listDirectory("../../outside")).isInstanceOf(IllegalArgumentException.class);
		} finally {
			System.setProperty("user.dir", originalUserDir);
		}
	}
}
