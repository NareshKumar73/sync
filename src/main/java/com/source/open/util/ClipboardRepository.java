package com.source.open.util;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.source.open.payload.Clipboard;

@Repository
public interface ClipboardRepository extends JpaRepository<Clipboard, Long> {
}
