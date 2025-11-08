package com.hippomaru.esbot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RandomCatService {

    @Value("${url.randomCatImage}")
    private String apiUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    public InputFile getRandomCatImage() throws IOException {
        try {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Ошибка от API поиска котов: " + response.statusCode());
            }

            List<RandomCatResponse> cats = objectMapper.readValue(
                    response.body(),
                    new TypeReference<>() {}
            );

            if (cats.isEmpty()) {
                throw new IOException("Пустой ответ от API поиска котов");
            }

            String imageUrl = cats.getFirst().url();
            log.info("URL с рандомным котиком: {}", imageUrl);

            HttpRequest imageRequest = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<byte[]> imageResponse = httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());

            if (imageResponse.statusCode() != 200) {
                throw new IOException("Не удалось загрузить картинку кота: " + imageResponse.statusCode());
            }

            return new InputFile(new ByteArrayInputStream(imageResponse.body()), "randomCat.jpg");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Запрос к API был прерван", e);
        }
    }

    public record RandomCatResponse(String id, String url, int width, int height) {}
}
