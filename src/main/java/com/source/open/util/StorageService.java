package com.source.open.util;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Log4j2
@Service
public class StorageService {

    private final FileService fileService;
    private final ConcurrentHashMap<String, Long> activeUploads;

    @Value("${app.storage.limit:26843545600}") // Default 25GB
    private long maxLimit;

    public StorageService(FileService fileService) {
        this.fileService = fileService;
        this.activeUploads = new ConcurrentHashMap<>();
    }

    public long getCurrentUsedSpace() {
        long size = 0;
        try (Stream<Path> stream = Files.walk(fileService.getAppDir())) {
            size = stream
                .filter(p -> p.toFile().isFile())
                .mapToLong(p -> p.toFile().length())
                .sum();
        } catch (IOException e) {
            log.error("Failed to calculate folder size", e);
        }
        return size;
    }

    public long getReservedSpace() {
        return activeUploads.values().stream().mapToLong(Long::longValue).sum();
    }

    public boolean reserveSpace(String uuid, long expectedTotalSize) {
        long currentUsed = getCurrentUsedSpace();
        long currentReserved = getReservedSpace();
        
        if (currentUsed + currentReserved + expectedTotalSize > maxLimit) {
            return false;
        }
        
        activeUploads.put(uuid, expectedTotalSize);
        return true;
    }

    public void releaseSpace(String uuid) {
        activeUploads.remove(uuid);
    }

    public Map<String, Object> getStorageStatus() {
        long currentUsed = getCurrentUsedSpace();
        long currentReserved = getReservedSpace();
        long remaining = maxLimit - (currentUsed + currentReserved);
        if (remaining < 0) remaining = 0;
        
        return Map.of(
            "limit", maxLimit,
            "used", currentUsed,
            "reserved", currentReserved,
            "remaining", remaining
        );
    }
}
