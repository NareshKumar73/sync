package com.source.open.util;

import com.source.open.payload.FileListJson;
import com.source.open.payload.FileMeta;
import com.source.open.payload.InstanceNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class SyncService {

    private final FileService fs;
    private final InstanceNodeRepository nodeRepository;
    private final RestClient restClient = RestClient.create();

    private boolean isAutoSyncEnabled = false;

    // A map to store conflicts: key is file relative path, value is remote FileMeta
    private final ConcurrentHashMap<String, FileMeta> syncConflicts = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 3600000)
    public void autoSync() {
        if (!isAutoSyncEnabled) return;
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
        List<InstanceNode> nodes = nodeRepository.findAll();
        List<FileMeta> localFiles = fs.listDirectoryRecursive();
        
        // Use relative path + name as key for local files to easily find matches
        Map<String, FileMeta> localFilesMap = localFiles.stream()
                .collect(Collectors.toMap(f -> f.getRelativePath(), f -> f));

        for (InstanceNode node : nodes) {
            try {
                String baseUrl = "http://" + node.getIpAddress() + ":" + node.getPort();
                
                // test connection
                ResponseEntity<String> pingResponse = restClient.get().uri(baseUrl + "/api/ping").retrieve().toEntity(String.class);
                if (!pingResponse.getStatusCode().is2xxSuccessful()) {
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
                if (local.getSizeInBytes() != remote.getSizeInBytes() || local.getLastModifiedEpoch() < remote.getLastModifiedEpoch()) {
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
        
        try {
            // Ensure parent directory exists
            Files.createDirectories(dest.getParent());
            
            restClient.get()
                    .uri(downloadUrl)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            try (InputStream is = response.getBody()) {
                                Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
                                log.info("Downloaded missing file: " + remote.getRelativePath());
                            }
                        }
                        return null;
                    });
        } catch (Exception e) {
            log.error("Failed to download file " + remote.getRelativePath(), e);
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
