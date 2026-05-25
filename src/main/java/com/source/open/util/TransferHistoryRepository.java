package com.source.open.util;

import com.source.open.payload.TransferHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransferHistoryRepository extends JpaRepository<TransferHistory, Long> {
    List<TransferHistory> findAllByOrderByTimestampDesc();
    
    Optional<TransferHistory> findFirstByIpAddressAndFilenameAndTypeOrderByTimestampDesc(String ipAddress, String filename, String type);
}
