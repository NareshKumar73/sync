package com.source.open.util;

import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemMetricsService {

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hal;
    private final CentralProcessor processor;
    private final GlobalMemory memory;
    private final FileService fileService;
    private long[] prevTicks;
    
    private long lastNetworkTime;
    private long lastNetworkRx;
    private long lastNetworkTx;

    public SystemMetricsService(FileService fileService) {
        this.fileService = fileService;
        systemInfo = new SystemInfo();
        hal = systemInfo.getHardware();
        processor = hal.getProcessor();
        memory = hal.getMemory();
        prevTicks = processor.getSystemCpuLoadTicks();
        lastNetworkTime = System.currentTimeMillis();
        
        updateNetworkStats();
    }
    
    private void updateNetworkStats() {
        lastNetworkRx = 0;
        lastNetworkTx = 0;
        List<NetworkIF> networkIFs = hal.getNetworkIFs();
        for (NetworkIF net : networkIFs) {
            net.updateAttributes();
            lastNetworkRx += net.getBytesRecv();
            lastNetworkTx += net.getBytesSent();
        }
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // CPU
        double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
        prevTicks = processor.getSystemCpuLoadTicks();
        metrics.put("cpu", String.format("%.1f", cpuLoad));
        
        // Memory
        long totalMem = memory.getTotal();
        long availMem = memory.getAvailable();
        long usedMem = totalMem - availMem;
        metrics.put("memoryTotal", totalMem);
        metrics.put("memoryUsed", usedMem);
        
        // Network
        long currentTime = System.currentTimeMillis();
        long currentRx = 0;
        long currentTx = 0;
        List<NetworkIF> networkIFs = hal.getNetworkIFs();
        for (NetworkIF net : networkIFs) {
            net.updateAttributes();
            currentRx += net.getBytesRecv();
            currentTx += net.getBytesSent();
        }
        
        long timeDiff = currentTime - lastNetworkTime;
        if (timeDiff > 0) {
            long rxSpeed = ((currentRx - lastNetworkRx) * 1000) / timeDiff;
            long txSpeed = ((currentTx - lastNetworkTx) * 1000) / timeDiff;
            metrics.put("networkDownload", rxSpeed); // bytes per sec
            metrics.put("networkUpload", txSpeed); // bytes per sec
        } else {
            metrics.put("networkDownload", 0);
            metrics.put("networkUpload", 0);
        }
        
        lastNetworkTime = currentTime;
        lastNetworkRx = currentRx;
        lastNetworkTx = currentTx;
        
        // Disk
        java.io.File appDir = fileService.getAppDir().toFile();
        long totalSpace = appDir.getTotalSpace();
        long usableSpace = appDir.getUsableSpace();
        metrics.put("diskTotal", totalSpace);
        metrics.put("diskUsable", usableSpace);

        return metrics;
    }
}
