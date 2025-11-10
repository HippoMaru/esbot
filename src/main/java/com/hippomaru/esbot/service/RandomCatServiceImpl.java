package com.hippomaru.esbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class RandomCatServiceImpl implements RandomCatService {

    private final String apiUrl;

    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    public RandomCatServiceImpl(@Value("${url.randomCatImage}") String apiUrl){
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
        this.objectMapper = new ObjectMapper();
        this.apiUrl = apiUrl;
    }

    @Override
    public ByteArrayInputStream getRandomCatImage() throws IOException {
        try {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Error while requesting the random cat image url: " + response.statusCode());
            }

            List<RandomCatResponse> cats = objectMapper.readValue(
                    response.body(),
                    new TypeReference<>() {}
            );

            if (cats.isEmpty()) {
                throw new IOException("Empty answer from " + apiUrl);
            }

            String imageUrl = cats.getFirst().url();
            log.info("Random cat image url: {}", imageUrl);

            HttpRequest imageRequest = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<byte[]> imageResponse = httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());

            if (imageResponse.statusCode() != 200) {
                throw new IOException("Error while loading the image from " + imageUrl + ": " + imageResponse.statusCode());
            }

            return new ByteArrayInputStream(imageResponse.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request was interrupted: ", e);
        }
    }

    public record RandomCatResponse(String id, String url, int width, int height) {}
}
