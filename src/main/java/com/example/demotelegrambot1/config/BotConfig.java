package com.example.demotelegrambot1.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
public class BotConfig {

    String botName = System.getenv("BOT_NAME");

    String botToken = System.getenv("BOT_TOKEN");
}