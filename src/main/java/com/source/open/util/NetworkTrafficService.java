package com.source.open.util;

import com.source.open.payload.NetworkTraffic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkTrafficService {

    private final NetworkTrafficRepository repository;

    private static class TrafficStat {
        AtomicLong sent = new AtomicLong(0);
        AtomicLong received = new AtomicLong(0);
    }

    private final Map<String, TrafficStat> buffer = new ConcurrentHashMap<>();

    public void recordTraffic(String ipAddress, long bytesRead, long bytesWritten) {
        if (ipAddress == null || ipAddress.isEmpty()) return;
        TrafficStat stat = buffer.computeIfAbsent(ipAddress, k -> new TrafficStat());
        if (bytesRead > 0) stat.received.addAndGet(bytesRead);
        if (bytesWritten > 0) stat.sent.addAndGet(bytesWritten);
    }

    @Scheduled(fixedRate = 10000)
    public void flushTrafficToDatabase() {
        if (buffer.isEmpty()) return;

        for (Map.Entry<String, TrafficStat> entry : buffer.entrySet()) {
            String ip = entry.getKey();
            TrafficStat stat = entry.getValue();

            long sentToFlush = stat.sent.getAndSet(0);
            long receivedToFlush = stat.received.getAndSet(0);

            if (sentToFlush > 0 || receivedToFlush > 0) {
                try {
                    NetworkTraffic traffic = repository.findByIpAddress(ip).orElseGet(() -> {
                        NetworkTraffic t = new NetworkTraffic();
                        t.setIpAddress(ip);
                        t.setBytesSent(0L);
                        t.setBytesReceived(0L);
                        return t;
                    });

                    traffic.setBytesSent((traffic.getBytesSent() != null ? traffic.getBytesSent() : 0) + sentToFlush);
                    traffic.setBytesReceived((traffic.getBytesReceived() != null ? traffic.getBytesReceived() : 0) + receivedToFlush);
                    traffic.setLastActive(LocalDateTime.now());
                    
                    repository.save(traffic);
                } catch (Exception e) {
                    log.error("Failed to flush network traffic for IP: {}", ip, e);
                    // Revert the stats if save fails so we don't lose data
                    stat.sent.addAndGet(sentToFlush);
                    stat.received.addAndGet(receivedToFlush);
                }
            }
        }
    }

    public void clearAllTraffic() {
        buffer.clear();
        repository.deleteAll();
    }

    public void clearTrafficById(Long id) {
        repository.findById(id).ifPresent(t -> {
            if (t.getIpAddress() != null) {
                buffer.remove(t.getIpAddress());
            }
            repository.delete(t);
        });
    }
}

