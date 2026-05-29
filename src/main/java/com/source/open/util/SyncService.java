package com.source.open.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.source.open.payload.FileListJson;
import com.source.open.payload.FileMeta;
import com.source.open.payload.InstanceNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class SyncService {

    private final FileService fs;
    private final InstanceNodeRepository nodeRepository;
    private final com.source.open.util.TransferHistoryRepository transferHistoryRepo;
    private final RestClient restClient = RestClient.create();

    private boolean isAutoSyncEnabled = false;

    // A map to store conflicts: key is file relative path, value is remote FileMeta
    private final ConcurrentHashMap<String, FileMeta> syncConflicts = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    public void autoSync() {
        if (!isAutoSyncEnabled)
            return;
        log.info("Starting scheduled auto-sync...");
        triggerSync();
    }

    public void setAutoSync(boolean enabled) {
        this.isAutoSyncEnabled = enabled;
    }

    public boolean isAutoSyncEnabled() {
        return this.isAutoSyncEnabled;
    }

    public void triggerSync() {
        try {
            List<InstanceNode> nodes = nodeRepository.findAll();
            List<FileMeta> localFiles = fs.listDirectoryRecursive();

            // Use relative path + name as key for local files to easily find matches
            Map<String, FileMeta> localFilesMap = localFiles.stream()
                    .collect(Collectors.toMap(f -> f.getRelativePath(), f -> f));

            for (InstanceNode node : nodes) {
                try {
                    String baseUrl = "http://" + node.getIpAddress() + ":" + node.getPort();

                    // test connection
                    ResponseEntity<String> pingResponse = restClient.get().uri(baseUrl + "/api/ping").retrieve()
                            .toEntity(String.class);
                    if (!pingResponse.getStatusCode().is2xxSuccessful()) {
                        node.setIsWorking(false);
                        nodeRepository.save(node);
                        continue;
                    }

                    node.setIsWorking(true);
                    node.setLastActive(LocalDateTime.now());
                    nodeRepository.save(node);

                    FileListJson remoteFiles = restClient.get()
                            .uri(baseUrl + "/files/recursive")
                            .retrieve()
                            .body(FileListJson.class);

                    if (remoteFiles != null && remoteFiles.getFiles() != null) {
                        processRemoteFiles(baseUrl, remoteFiles.getFiles(), localFilesMap);
                    }

                } catch (Exception e) {
                    log.warn("Failed to sync with node {}:{}", node.getIpAddress(), node.getPort());
                    node.setIsWorking(false);
                    nodeRepository.save(node);
                }
            }
        } finally {
            cleanTempDir();
        }
    }

    private void processRemoteFiles(String baseUrl, List<FileMeta> remoteFiles, Map<String, FileMeta> localFilesMap) {
        for (FileMeta remote : remoteFiles) {
            String relativePath = remote.getRelativePath();

            if (remote.isDirectory()) {
                Path dirPath = fs.getAppDir().resolve(relativePath);
                if (!Files.exists(dirPath)) {
                    try {
                        Files.createDirectories(dirPath);
                    } catch (Exception e) {
                        log.error("Failed to create directory: " + relativePath);
                    }
                }
                continue;
            }

            FileMeta local = localFilesMap.get(relativePath);

            if (local == null) {
                // We don't have it, download it
                downloadFile(baseUrl, remote);
            } else {
                // We have it, check size and modified date
                if (local.getSizeInBytes() != remote.getSizeInBytes()
                        || local.getLastModifiedEpoch() < remote.getLastModifiedEpoch()) {
                    // Conflict found. Do not overwrite. Add to conflicts.
                    syncConflicts.put(relativePath, remote);
                    log.info("Conflict found for file: {}. Ignoring remote file.", relativePath);
                }
            }
        }
    }

    private void downloadFile(String baseUrl, FileMeta remote) {
        String downloadUrl = baseUrl + remote.getUrl();
        Path dest = fs.getAppDir().resolve(remote.getRelativePath());
        Path tempDir = fs.getData().resolve("temp");
        Path tempFile = tempDir.resolve(remote.getRelativePath() + ".part");

        try {
            // Ensure parent directory exists for both dest and tempFile
            Files.createDirectories(dest.getParent());
            Files.createDirectories(tempFile.getParent());

            long existingLength = 0;
            if (Files.exists(tempFile)) {
                existingLength = Files.size(tempFile);
                if (existingLength >= remote.getSizeInBytes()) {
                    Files.delete(tempFile);
                    existingLength = 0;
                }
            }

            final long startByte = existingLength;
            var requestHeadersSpec = restClient.get().uri(downloadUrl);
            if (startByte > 0) {
                requestHeadersSpec.header("Range", "bytes=" + startByte + "-");
            }

            requestHeadersSpec.exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (status == 200 || status == 206) {
                    boolean append = (status == 206 && startByte > 0);
                    try (InputStream is = response.getBody();
                         OutputStream os = Files.newOutputStream(tempFile, 
                             StandardOpenOption.CREATE, 
                             append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING, 
                             StandardOpenOption.WRITE)) {
                        is.transferTo(os);
                    }

                    long finalSize = Files.size(tempFile);
                    if (finalSize == remote.getSizeInBytes()) {
                        try {
                            Files.move(tempFile, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                            Files.move(tempFile, dest, StandardCopyOption.REPLACE_EXISTING);
                        }

                        try {
                            Files.setLastModifiedTime(dest, java.nio.file.attribute.FileTime.fromMillis(remote.getLastModifiedEpoch()));
                        } catch (Exception ex) {
                            log.error("Failed to set last modified time for " + dest, ex);
                        }

                        log.info("Downloaded missing file: " + remote.getRelativePath());
                        
                        try {
                            com.source.open.payload.TransferHistory th = new com.source.open.payload.TransferHistory();
                            th.setType("SYNC_JOB");
                            th.setFilename(remote.getRelativePath());
                            // Extract IP from baseUrl
                            String ip = baseUrl.replace("http://", "").replace("https://", "").split(":")[0];
                            th.setIpAddress(ip);
                            th.setFileSize(remote.getSizeInBytes());
                            th.setTimestamp(LocalDateTime.now());
                            transferHistoryRepo.save(th);
                        } catch (Exception ex) {
                            log.error("Failed to log sync transfer history", ex);
                        }
                    } else {
                        log.warn("Size mismatch for downloaded file {}: expected {} bytes, got {} bytes", 
                            remote.getRelativePath(), remote.getSizeInBytes(), finalSize);
                    }
                } else {
                    log.error("Failed to download file " + remote.getRelativePath() + ", HTTP status: " + status);
                    if (status == 416) {
                        try {
                            Files.deleteIfExists(tempFile);
                        } catch (Exception ignored) {}
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to download file " + remote.getRelativePath(), e);
        }
    }

    private void cleanTempDir() {
        try {
            Path tempDir = fs.getData().resolve("temp");
            if (Files.exists(tempDir)) {
                try (Stream<Path> stream = Files.walk(tempDir)) {
                    List<Path> paths = stream.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
                    for (Path p : paths) {
                        if (Files.isDirectory(p)) {
                            try (Stream<Path> s = Files.list(p)) {
                                if (s.findAny().isEmpty()) {
                                    Files.delete(p);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to clean empty temp directories", e);
        }
    }

    public Map<String, FileMeta> getConflicts() {
        return syncConflicts;
    }

    public void resolveConflict(String relativePath, String baseUrl) {
        FileMeta remote = syncConflicts.get(relativePath);
        if (remote != null) {
            downloadFile(baseUrl, remote);
            syncConflicts.remove(relativePath);
        }
    }
}
