package com.source.open.util;

import com.source.open.payload.NetworkTraffic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NetworkTrafficRepository extends JpaRepository<NetworkTraffic, Long> {
    Optional<NetworkTraffic> findByIpAddress(String ipAddress);
}
