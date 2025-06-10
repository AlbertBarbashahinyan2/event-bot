package com.example.demotelegrambot1.service;

import com.example.demotelegrambot1.config.BotConfig;
import com.example.demotelegrambot1.enums.RegistrationState;
import com.example.demotelegrambot1.persistence.entity.Event;
import com.example.demotelegrambot1.persistence.entity.User;
import com.example.demotelegrambot1.persistence.entity.UserEvent;
import com.example.demotelegrambot1.persistence.repository.EventRepository;
import com.example.demotelegrambot1.persistence.repository.UserEventRepository;
import com.example.demotelegrambot1.persistence.repository.UserRepository;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class TelegramBotService extends TelegramLongPollingBot {
    final BotConfig botConfig;
    final UserRepository userRepository;
    final EventRepository eventRepository;
    final UserEventRepository userEventRepository;

    private static final String HELP_MESSAGE = EmojiParser.parseToUnicode(
            """
                    Welcome to the Telegram Bot! :robot_face:
                    
                    Here are some commands you can use:
                    /start - Start the bot
                    /events - List current events
                    /help - Show this help message
                    
                    Feel free to ask me anything! :smiley:
                    """
    );

    private static final String DEFAULT_MESSAGE = EmojiParser.parseToUnicode(
            "Sorry, I didn't understand that command :confused:. Type /help for assistance.");

    public TelegramBotService(BotConfig botConfig, UserRepository userRepository, EventRepository eventRepository, UserEventRepository userEventRepository) {
        this.botConfig = botConfig;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        List<BotCommand> commands = List.of(
                new BotCommand("/start", "Start the bot"),
                new BotCommand("/events", "List current events"),
                new BotCommand("/help", "Show help message")
        );
        try {
            this.execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
            log.info("Bot commands set successfully.");
        } catch (TelegramApiException e) {
            log.error("Failed to set bot commands: {}", e.getMessage());
        }
        this.userEventRepository = userEventRepository;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            User user = userRepository.findById(chatId).orElse(
                    registerUser(update.getMessage())
            );
            handleDefaultMessage(update, messageText, chatId, user);
            if (user.getActiveRegistration() != null) {
                UserEvent activeRegistration = user.getActiveRegistration();
                RegistrationState registrationState = activeRegistration.getState();

                switch (registrationState.name()) {
                    case "AWAITING_TEAM_MEMBERS" -> {
                        handleTeamMembersInput(chatId, messageText, activeRegistration);
                        userRepository.save(user);
                    }
                    case "AWAITING_TEAM_NAME" -> {
                        handleTeamNameInput(chatId, messageText, activeRegistration);
                        userRepository.save(user);
                    }
                    case "AWAITING_CONTACT_PHONE" -> {
                        handleContactPhoneInput(chatId, messageText, activeRegistration);
                        userRepository.save(user);
                    }
                    default -> sendMessage(chatId, DEFAULT_MESSAGE);
                }
            }
            log.info("Received message: {} from chat ID: {}", messageText, chatId);
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();
            int messageId = update.getCallbackQuery().getMessage().getMessageId();
            User user = userRepository.findById(chatId).orElse(null);
            if (user == null) {
                log.error("User not found for chat ID: {}", chatId);
                return;
            }

            if (callbackData.startsWith("event_")) {
                editEventDetails(chatId, callbackData, messageId);
            } else if (callbackData.startsWith("register_event_")) {
                long eventId = extractEventIdFromCallBack(callbackData);
                Event event = eventRepository.findById(eventId).orElse(null);
                if (event == null) {
                    sendMessage(chatId, "❌ Event not found.");
                    return;
                }

                UserEvent userEvent = new UserEvent();
                userEvent.setEvent(event);
                userEvent.setUser(user);
                userEvent.setState(RegistrationState.AWAITING_TEAM_MEMBERS);
                userEventRepository.save(userEvent);
                user.setActiveRegistration(userEvent);

                userRepository.save(user);

                sendMessage(chatId, "Please enter the first and last names of your team members, one per line.");
            } else if (callbackData.equals("back_to_events")) {
                editToEvents(chatId, messageId);
            } else {
                log.warn("Received an unsupported callback data: {}", callbackData);
            }
        } else {
            log.warn("Received an unsupported update type: {}", update);
        }

    }

    private long extractEventIdFromCallBack(String callbackData) {
        String[] parts = callbackData.split("_");
        return Long.parseLong(parts[2]);
    }

    private void handleDefaultMessage(Update update, String messageText, long chatId, User user) {
        switch (messageText) {
            case "/start" -> {
                user.setActiveRegistration(null);
                sendStart(chatId, update.getMessage().getChat().getFirstName());
                userRepository.save(user);
            }
            case "/help" -> {
                user.setActiveRegistration(null);
                sendMessage(chatId, HELP_MESSAGE);
                userRepository.save(user);

            }
            case "/events" -> {
                user.setActiveRegistration(null);
                sendEvents(chatId);
                userRepository.save(user);
            }
        }
    }

    private void sendEvents(long chatId) {
        List<Event> events = eventRepository.findAllByOrderByIdAsc();
        String eventsMessage = buildEventsMessage(events);
        sendMessage(chatId, eventsMessage, createInlineKeyboardMarkupForEvents(events));
    }

    private void editToEvents(long chatId, int messageId) {
        List<Event> events = eventRepository.findAllByOrderByIdAsc();
        String eventsMessage = buildEventsMessage(events);
        editMessage(chatId, eventsMessage, messageId, createInlineKeyboardMarkupForEvents(events));
    }

    private void editEventDetails(long chatId, String callbackData, int messageId) {
        long eventId = Long.parseLong(callbackData.split("_")[1]);
        eventRepository.findById(eventId).ifPresentOrElse(event -> {
            String text = EmojiParser.parseToUnicode(String.format(
                    """
                            *%s*
                            
                            📅 Date: %s
                            📍 Location: %s
                            📝 %s
                            """,
                    event.getTitle(),
                    event.getDate(),
                    event.getLocation(),
                    event.getDescription()
            ));
            editMessage(chatId, text, messageId, createInlineKeyboardMarkupForRegistration(eventId));
        }, () -> editMessage(chatId, "Event not found.", messageId, null));
    }

    private User registerUser(Message message) {
        long chatId = message.getChatId();
        String firstName = message.getChat().getFirstName();
        String lastName = message.getChat().getLastName();
        String userName = message.getChat().getUserName();
        User existingUser = userRepository.findById(chatId).orElse(null);
        if (existingUser == null) {
            User user = new User();
            user.setChatId(chatId);
            user.setFirstName(firstName);
            user.setLastName(lastName);
            user.setUserName(userName);
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));
            userRepository.save(user);
            log.info("New user registered: {}", firstName + " " + lastName);
            return user;
        } else {
            log.info("User already registered: {}", firstName + " " + lastName);
            return existingUser;
        }
    }

    @Override
    public String getBotUsername() {
        return botConfig.getBotName();
    }

    @Override
    public String getBotToken() {
        return botConfig.getBotToken();
    }

    private void sendStart(long chatId, String name) {

        String answer = EmojiParser.parseToUnicode(
                "Hello, " + name + "! How can I assist you today? :wave:");
        sendMessage(chatId, answer);
    }

    private void editMessage(long chatId, String text, int messageId,
                             InlineKeyboardMarkup inlineKeyboardMarkup) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId(messageId);
        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to edit message: {}", e.getMessage());
        }
    }


    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(createKeyboardMarkup());

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message: {}", e.getMessage());
        }
    }

    private void sendMessage(long chatId, String text, ReplyKeyboard replyMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        message.setReplyMarkup(replyMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send message: {}", e.getMessage());
        }
    }

    private InlineKeyboardMarkup createInlineKeyboardMarkupForEvents(List<Event> events) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(String.valueOf(i + 1));
            button.setCallbackData("event_" + event.getId());
            row.add(button);
        }

        rows.add(row);
        inlineKeyboardMarkup.setKeyboard(rows);
        return inlineKeyboardMarkup;
    }

    private InlineKeyboardMarkup createInlineKeyboardMarkupForRegistration(long eventId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        InlineKeyboardButton RegisterButton = new InlineKeyboardButton();
        RegisterButton.setText("Register");
        RegisterButton.setCallbackData("register_event_" + eventId);
        row.add(RegisterButton);

        InlineKeyboardButton BackButton = new InlineKeyboardButton();
        BackButton.setText("Back");
        BackButton.setCallbackData("back_to_events");
        row.add(BackButton);

        rows.add(row);
        inlineKeyboardMarkup.setKeyboard(rows);
        return inlineKeyboardMarkup;
    }

    private ReplyKeyboardMarkup createKeyboardMarkup() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow row1 = new KeyboardRow();

        row1.addAll(List.of("/events", "/help", "/start"));
        keyboard.add(row1);
//        KeyboardRow row2 = new KeyboardRow();
//        row2.addAll(List.of("Option 3", "Option 4", "Option 5"));
//        keyboard.add(row2);
        keyboardMarkup.setKeyboard(keyboard);

        keyboardMarkup.setResizeKeyboard(true);
        return keyboardMarkup;
    }

    private String buildEventsMessage(List<Event> events) {
        StringBuilder sb = new StringBuilder("Here are the upcoming events:\n");
        int count = 1;
        for (Event event : events) {
            sb.append(":").append(numberToEmoji(count)).append(": ")
                    .append(event.getTitle()).append("\n");
            count++;
        }
        return EmojiParser.parseToUnicode(sb.toString());
    }

    private String numberToEmoji(int number) {
        return switch (number) {
            case 1 -> "one";
            case 2 -> "two";
            case 3 -> "three";
            case 4 -> "four";
            case 5 -> "five";
            default -> "hash";
        };
    }

    public void handleTeamMembersInput(long chatId, String messageText, UserEvent userEvent) {
        String[] members = messageText.split("\n");
        if (members.length < 2 || members.length > 6) {
            sendMessage(chatId, "❌ Please enter between 2 and 6 team members, one per line.");
            return;
        }
        userEvent.setTeamMembers(Arrays.stream(members).toList());
        userEvent.setState(RegistrationState.AWAITING_TEAM_NAME);
        userEventRepository.save(userEvent);
        sendMessage(chatId, "✅ Team registered with " + members.length + " members.");
        sendMessage(chatId, "Please enter the team name (1-40 characters):");
    }

    private void handleTeamNameInput(long chatId, String messageText, UserEvent userEvent) {
        String teamName = messageText.trim();
        if (teamName.isBlank() || teamName.length() > 40) {
            sendMessage(chatId, "❌ Team name must be between 1 and 40 characters long.");
            return;
        }
        userEvent.setTeamName(teamName);
        userEvent.setState(RegistrationState.AWAITING_CONTACT_PHONE);
        userEventRepository.save(userEvent);
        sendMessage(chatId, "✅ Team name set to: " + teamName + ".");
        sendMessage(chatId, "Please enter your contact phone number (9-15 digits, optional +):");
    }

    private void handleContactPhoneInput(long chatId, String messageText, UserEvent userEvent) {
        String phoneNumber = messageText.trim();
        if (!phoneNumber.matches("^\\+?[0-9]{9,15}$")) {
            sendMessage(chatId, "❌ Please enter a valid phone number (10-15 digits, optional +).");
            return;
        }
        userEvent.setContactPhone(phoneNumber);
        userEvent.setRegistrationDate(new Timestamp(System.currentTimeMillis()));
        sendMessage(chatId, EmojiParser.parseToUnicode("✅ Registration completed successfully! :tada:"));
        userEvent.setState(RegistrationState.COMPLETED_REGISTRATION);
        userEventRepository.save(userEvent);
    }
}