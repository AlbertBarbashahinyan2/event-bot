package com.example.demotelegrambot1.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.List;

@Entity
@Table(name = "`user`")
@Getter
@Setter
public class User {
    @Id
    private Long chatId;

    @OneToMany(cascade = CascadeType.PERSIST)
    private List<UserEvent> userEvents;

    @OneToOne(cascade = CascadeType.PERSIST)
    @JoinColumn(name = "active_registration_id")
    private UserEvent activeRegistration;

    private String firstName;

    private String lastName;

    private String userName;

    private Timestamp registeredAt;

}