package com.source.open.util;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.source.open.payload.Chat;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
}
