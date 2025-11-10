package com.hippomaru.esbot.bot;

import com.hippomaru.esbot.service.DutyRosterService;
import com.hippomaru.esbot.service.DutyRosterServiceImpl;
import com.hippomaru.esbot.service.RandomCatService;
import com.hippomaru.esbot.service.RandomCatServiceImpl;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class UpdateConsumer implements LongPollingSingleThreadUpdateConsumer {

    private final BotMessages messages;
    private final TelegramClient client;
    private final BotProperties props;
    private final RandomCatService catService;
    private final DutyRosterService dRService;
    private final ExecutorService catServiceExecutor;
    private final ExecutorService dRServiceExecutor;

    Map<Long, Boolean> waitingForDRUpdate = new ConcurrentHashMap<>();

    public UpdateConsumer(@Autowired BotProperties props,
                          @Autowired BotMessages messages,
                          @Autowired RandomCatServiceImpl catService,
                          @Autowired DutyRosterServiceImpl dRService) {
        this.props = props;
        this.client = new OkHttpTelegramClient(props.getToken());
        this.messages = messages;
        this.catService = catService;
        this.dRService = dRService;
        this.catServiceExecutor = Executors.newFixedThreadPool(20);
        this.dRServiceExecutor = Executors.newFixedThreadPool(20);
    }


    @SneakyThrows
    @Override
    public void consume(Update update) {
        log.info("Update {}", update.getUpdateId());
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

        log.info("CallBack from {}: \"{}\" chatId={}", user.getUserName(), data, chatId);

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


    private void sendRandomCat(Long chatId) throws TelegramApiException {
        sendMessage(chatId, messages.getRandomCatStarted());
        catServiceExecutor.submit(() -> {
            try {
                InputFile catImage = new InputFile(catService.getRandomCatImage(), "randomCat.jpg");
                sendPhoto(chatId, catImage, messages.getRandomCatFinished());
            } catch (IOException | TelegramApiException e) {
                try {
                    sendMessage(chatId, messages.getDefaultError());
                } catch (TelegramApiException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    private void processMMGreeting(Long chatId, User user) throws TelegramApiException {
        String msg = messages.getMMGreetingAnswer().formatted(user.getFirstName());
        sendMessage(chatId, msg);
    }

    private void updateDutyRoster(Message msg) throws TelegramApiException {
        if (!msg.hasPhoto()) {
            sendMessage(msg.getChatId(), messages.getDRUpdateWrongInput());
            return;
        }
        PhotoSize photo = msg.getPhoto().getLast();
        GetFile getFile = new GetFile(photo.getFileId());
        File file = client.execute(getFile);
        String fileUrl = file.getFileUrl(props.getToken());
        dRServiceExecutor.submit(() -> {
            try {
                dRService.updateDutyRoster(fileUrl);
                sendMessage(msg.getChatId(), messages.getDRUpdateFinished());
            } catch (IOException | TelegramApiException e) {
                try {
                    sendMessage(msg.getChatId(), messages.getDefaultError());
                    throw new IOException(e);
                } catch (TelegramApiException | IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }
    private void sendDutyRoster(Long chatId){
        dRServiceExecutor.submit(() -> {
            try {
                var dRServiceResult = dRService.getDutyRoster();
                if (dRServiceResult == null) {
                    sendMessage(chatId, messages.getDRGetNotFound());
                    return;
                }
                InputFile dRImage = new InputFile(dRServiceResult, "dutyRoster.jpg");
                sendPhoto(chatId, dRImage, messages.getDRGet());
            } catch (IOException | TelegramApiException e) {
                try {
                    sendMessage(chatId, messages.getDefaultError());
                    throw new IOException(e);
                } catch (TelegramApiException | IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
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
        Long chatId = msg.getChatId();
        String text = msg.hasText() ? msg.getText() : msg.getCaption();
        log.info("Incoming message from {}: \"{}\" chatId={}", msg.getFrom().getUserName(), text, chatId);
        if (waitingForDRUpdate.getOrDefault(chatId, false)) {
            updateDutyRoster(msg);
            waitingForDRUpdate.put(chatId, false);
            return;
        }
        String altCat = messages.getRKBRandomCatButton();
        String altMenu = messages.getRKBMainMenuButton();

        if ("/start".equals(text)) {
            sendReplyKeyboard(chatId, msg.getFrom().getFirstName());
        } else if ("/menu".equals(text) || altMenu.equals(text)) {
            sendMainMenu(chatId);
        } else if ("/cat".equals(text) || altCat.equals(text)) {
            sendRandomCat(chatId);
        } else if ("/dr_get".equals(text)) {
            sendDutyRoster(chatId);
        } else if ("/dr_update".equals(text)) {
            waitingForDRUpdate.put(chatId, true);
            sendMessage(chatId, messages.getDRUpdateImageRequest());
        } else {
            sendMessage(chatId, messages.getUnsupported());
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
