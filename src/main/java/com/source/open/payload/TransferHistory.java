package com.source.open.payload;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class TransferHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Type of transfer: "MANUAL_UPLOAD", "MANUAL_DOWNLOAD", "SYNC_JOB"
    private String type;

    // Name of the file transferred
    private String filename;

    // IP address of the remote client or sync node
    private String ipAddress;

    // Size of the file in bytes
    private Long fileSize;

    // When the transfer occurred
    private LocalDateTime timestamp;

    public String getFormattedSize() {
        if (fileSize == null) return "0 B";
        if (fileSize < 1024) return fileSize + " B";
        int z = (63 - Long.numberOfLeadingZeros(fileSize)) / 10;
        return String.format("%.1f %sB", (double)fileSize / (1L << (z * 10)), " KMGTPE".charAt(z));
    }
}
