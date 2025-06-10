package com.example.demotelegrambot1.persistence.repository;

import com.example.demotelegrambot1.persistence.entity.UserEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEventRepository extends JpaRepository<UserEvent, Long> {
}