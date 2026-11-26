package com.source.open.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.source.open.payload.FileMeta;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SyncServiceTest {

	@TempDir
	Path appDir;

	private SyncService newSyncService() {
		FileService fs = mock(FileService.class);
		when(fs.getAppDir()).thenReturn(appDir);
		return new SyncService(fs, mock(InstanceNodeRepository.class), mock(TransferHistoryRepository.class),
				mock(SimpMessagingTemplate.class));
	}

	private FileMeta file(String relativePath, long sizeInBytes, long lastModifiedEpoch) {
		return FileMeta.builder().relativePath(relativePath).directory(false).sizeInBytes(sizeInBytes)
				.lastModifiedEpoch(lastModifiedEpoch).build();
	}

	@Test
	void matchingSizeAndTimeIsNotAConflict() {
		SyncService sync = newSyncService();
		FileMeta remote = file("a.txt", 10, 1000);
		FileMeta local = file("a.txt", 10, 1000);

		sync.processRemoteFiles("http://peer", List.of(remote), Map.of("a.txt", local));

		assertThat(sync.getConflicts()).isEmpty();
	}

	@Test
	void differentSizeIsRecordedAsConflict() {
		SyncService sync = newSyncService();
		FileMeta remote = file("a.txt", 20, 1000);
		FileMeta local = file("a.txt", 10, 1000);

		sync.processRemoteFiles("http://peer", List.of(remote), Map.of("a.txt", local));

		assertThat(sync.getConflicts()).containsKey("a.txt");
	}

	@Test
	void modifiedTimeWithinTwoSecondToleranceIsNotAConflict() {
		SyncService sync = newSyncService();
		FileMeta remote = file("a.txt", 10, 2500);
		FileMeta local = file("a.txt", 10, 1000);

		sync.processRemoteFiles("http://peer", List.of(remote), Map.of("a.txt", local));

		assertThat(sync.getConflicts()).isEmpty();
	}

	@Test
	void modifiedTimeBeyondToleranceIsRecordedAsConflict() {
		SyncService sync = newSyncService();
		FileMeta remote = file("a.txt", 10, 5000);
		FileMeta local = file("a.txt", 10, 1000);

		sync.processRemoteFiles("http://peer", List.of(remote), Map.of("a.txt", local));

		assertThat(sync.getConflicts()).containsKey("a.txt");
	}

	@Test
	void remoteDirectoryOutsideSharedFolderIsRejected() {
		SyncService sync = newSyncService();
		FileMeta evilDir = FileMeta.builder().relativePath("../outside-" + System.nanoTime()).directory(true).build();

		sync.processRemoteFiles("http://peer", List.of(evilDir), Map.of());

		Path escaped = appDir.resolve(evilDir.getRelativePath()).normalize();
		assertThat(Files.exists(escaped)).isFalse();
	}

	@Test
	void remoteDirectoryInsideSharedFolderIsCreated() {
		SyncService sync = newSyncService();
		FileMeta okDir = FileMeta.builder().relativePath("subdir").directory(true).build();

		sync.processRemoteFiles("http://peer", List.of(okDir), Map.of());

		assertThat(Files.isDirectory(appDir.resolve("subdir"))).isTrue();
	}
}
