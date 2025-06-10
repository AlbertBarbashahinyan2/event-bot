package com.example.demotelegrambot1.persistence.entity;

import com.example.demotelegrambot1.enums.RegistrationState;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_event")
@Getter
@Setter
public class UserEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "registration_state", nullable = false)
    private RegistrationState state;

    @Column(name = "registration_date")
    private Timestamp registrationDate;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "team_name")
    private String teamName;

    @Column(name = "team_members")
    private List<String> teamMembers = new ArrayList<>();

}
