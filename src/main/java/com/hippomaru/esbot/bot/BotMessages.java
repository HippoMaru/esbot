package com.hippomaru.esbot.bot;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "messages")
@Getter
@Setter
public class BotMessages {
    private String start;
    private String unsupported;
    private String mMHeader;
    private String mMGreetingButton;
    private String mMGreetingAnswer;
    private String mMWhoIsTheBossButton;
    private String mMWhoIsTheBossAnswer;
    private String mMWhoIsYourDaddyButton;
    private String mMWhoIsYourDaddyAnswer;
    private String rKBHeader;
    private String rKBMainMenuButton;
    private String rKBRandomCatButton;
    private String randomCatStarted;
    private String randomCatFinished;
    private String defaultError;
    private String dRGet;
    private String dRGetNotFound;
    private String dRUpdateImageRequest;
    private String dRUpdateWrongInput;
    private String dRUpdateFinished;
}
