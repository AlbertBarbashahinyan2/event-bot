package com.example.demotelegrambot1.persistence.repository;

import com.example.demotelegrambot1.persistence.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByOrderByIdAsc();

}
