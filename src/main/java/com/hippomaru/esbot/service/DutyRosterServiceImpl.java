package com.hippomaru.esbot.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Service
@Slf4j
public class DutyRosterServiceImpl implements DutyRosterService{
    private final Path imagePath;

    private final HttpClient httpClient;

    public DutyRosterServiceImpl(@Value("${dutyRoster.imagePath}") String imagePath){
        this.imagePath = Path.of(imagePath);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
    }


    @Override
    public synchronized void updateDutyRoster(String newImageURL) throws IOException {
        try {
            HttpRequest imageRequest = HttpRequest.newBuilder()
                    .uri(URI.create(newImageURL))
                    .timeout(Duration.ofSeconds(60))
                    .build();
            HttpResponse<byte[]> imageResponse = httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (imageResponse.statusCode() != 200) {
                throw new IOException("Error while loading the duty roster image: " + imageResponse.statusCode());
            }
            Files.copy(new ByteArrayInputStream(imageResponse.body()), imagePath, StandardCopyOption.REPLACE_EXISTING);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request was interrupted: ", e);
        }
    }

    @Override
    public ByteArrayInputStream getDutyRoster() throws IOException {
        if (!Files.exists(imagePath)) return null;
        return new ByteArrayInputStream(Files.readAllBytes(imagePath));
    }
}
