package com.hippomaru.esbot.service;

import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public interface DutyRosterService {
    public void updateDutyRoster(String newImageUrl) throws IOException;
    public ByteArrayInputStream getDutyRoster() throws IOException;
}
