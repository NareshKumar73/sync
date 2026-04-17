package com.source.open.util;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.source.open.payload.InstanceNode;
import java.util.Optional;

@Repository
public interface InstanceNodeRepository extends JpaRepository<InstanceNode, Long> {
    Optional<InstanceNode> findByIpAddressAndPort(String ipAddress, Integer port);
}
