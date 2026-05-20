package com.source.open.util;

import com.source.open.payload.TransferHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferHistoryRepository extends JpaRepository<TransferHistory, Long> {
    List<TransferHistory> findAllByOrderByTimestampDesc();
}
