package com.example.demotelegrambot1.persistence.repository;

import com.example.demotelegrambot1.persistence.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

}