package com.example.localrag.repository;

import com.example.localrag.entity.ChatResponse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatResponseRepository extends JpaRepository<ChatResponse, Long> {
}
