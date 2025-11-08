package com.hippomaru.esbot;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class UpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    private final BotMessages messages;
    private final TelegramClient client;
    private final RandomCatService catService;

    public UpdateConsumer(@Autowired BotProperties props, @Autowired BotMessages messages, @Autowired RandomCatService catService) {
        this.client = new OkHttpTelegramClient(props.getToken());
        this.messages = messages;
        this.catService = catService;
    }


    @SneakyThrows
    @Override
    public void consume(Update update) {
        if (update.hasMessage()){
            processIncomingMessage(update);
        }
        else if (update.hasCallbackQuery()){
            processCallbackQuery(update.getCallbackQuery());
        }
    }

    private void processCallbackQuery(CallbackQuery callbackQuery) throws TelegramApiException {
        var data = callbackQuery.getData();
        var chatId = callbackQuery.getFrom().getId();
        var user = callbackQuery.getFrom();

        client.execute(AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQuery.getId())
                .build()
        );

        switch (data) {
            case "mm_greeting_button" -> processMMGreeting(chatId, user);
            case "mm_whoIsTheBoss_button" -> sendMessage(chatId, messages.getMMWhoIsTheBossAnswer());
            case "mm_whoIsYourDaddy_button" -> sendMessage(chatId, messages.getMMWhoIsYourDaddyAnswer());
            default -> sendMessage(chatId, messages.getUnsupported());
        }
    }


    private void processRKBRandomCat(Long chatId) throws TelegramApiException {
        sendMessage(chatId, messages.getRKBRandomCatStarted());
        new Thread(() -> {
            try {
                InputFile catImage = catService.getRandomCatImage();
                sendPhoto(chatId, catImage, messages.getRKBRandomCatFinished());
            } catch (IOException | TelegramApiException e) {
                try {
                    sendMessage(chatId, messages.getDefaultError());
                } catch (TelegramApiException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }).start();
    }

    private void processMMGreeting(Long chatId, User user) throws TelegramApiException {
        String msg = messages.getMMGreetingAnswer().formatted(user.getFirstName());
        sendMessage(chatId, msg);
    }

    private void sendMessage(Long chatId, String msg) throws TelegramApiException {
        SendMessage sendMessage = SendMessage.builder()
                .text(msg)
                .chatId(chatId)
                .build();

        client.execute(sendMessage);

    }

    private void sendPhoto(Long chatId, InputFile photo, String msg) throws TelegramApiException {
        SendPhoto sendPhoto = SendPhoto.builder()
                .chatId(chatId)
                .photo(photo)
                .caption(msg)
                .build();

        client.execute(sendPhoto);
    }

    private void processIncomingMessage(Update update) throws TelegramApiException {
        Message msg = update.getMessage();
        String text = msg.getText();
        String altCat = messages.getRKBRandomCatButton();
        String altMenu = messages.getRKBMainMenuButton();

        if ("/start".equals(text)) {
            sendReplyKeyboard(msg.getChatId(), msg.getFrom().getFirstName());
        } else if ("/menu".equals(text) || altMenu.equals(text)) {
            sendMainMenu(msg.getChatId());
        } else if ("/cat".equals(text) || altCat.equals(text)) {
            processRKBRandomCat(msg.getChatId());
        } else {
            sendMessage(msg.getChatId(), messages.getUnsupported());
        }

    }


    private void sendReplyKeyboard(Long chatId, String userName) throws TelegramApiException {
        List<KeyboardRow> kbRows = List.of(
                new KeyboardRow(messages.getRKBMainMenuButton(), messages.getRKBRandomCatButton())
        );


        SendMessage sendMessage = SendMessage.builder()
                .text(messages.getRKBHeader().formatted(userName))
                .chatId(chatId)
                .replyMarkup(new ReplyKeyboardMarkup(kbRows))
                .parseMode("MarkdownV2")
                .build();

        client.execute(sendMessage);
    }

    private void sendMainMenu(Long chatId) throws TelegramApiException {
        SendMessage sendMessage = SendMessage.builder()
                .text(messages.getMMHeader())
                .chatId(chatId)
                .build();

        var greetingButton = InlineKeyboardButton.builder()
                .text(messages.getMMGreetingButton())
                .callbackData("mm_greeting_button")
                .build();

        var whoIsTheBossButton = InlineKeyboardButton.builder()
                .text(messages.getMMWhoIsTheBossButton())
                .callbackData("mm_whoIsTheBoss_button")
                .build();

        var whoIsYourDaddyButton = InlineKeyboardButton.builder()
                .text(messages.getMMWhoIsYourDaddyButton())
                .callbackData("mm_whoIsYourDaddy_button")
                .build();

        List<InlineKeyboardRow> keyboardRows = List.of(
                new InlineKeyboardRow(greetingButton),
                new InlineKeyboardRow(whoIsTheBossButton),
                new InlineKeyboardRow(whoIsYourDaddyButton)
        );

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup(keyboardRows);

        sendMessage.setReplyMarkup(keyboardMarkup);

        client.execute(sendMessage);

    }
}
