package com.fooddelivery.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Service
public class BrevoEmailService {

    @Value("${brevo.api.key}")
    private String apiKey;

    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String sendHtmlEmail(String senderAddress, String recipientAddress, String subject, String htmlBody) throws Exception {
        String endpoint = "https://api.brevo.com/v3/smtp/email";

        Map<String, Object> payloadObj = Map.of(
                "sender", Map.of("email", senderAddress),
                "to", List.of(Map.of("email", recipientAddress)),
                "subject", subject,
                "htmlContent", htmlBody
        );

        String jsonPayload = objectMapper.writeValueAsString(payloadObj);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode rootNode = objectMapper.readTree(response.body());
            return rootNode.path("messageId").asText();
        } else if (response.statusCode() >= 400 && response.statusCode() < 500) {
            throw new IllegalArgumentException("Brevo Client Error (4xx). HTTP " + response.statusCode() + " Response: " + response.body());
        } else {
            throw new RuntimeException("Brevo Dispatch Failed. HTTP " + response.statusCode() + " Response: " + response.body());
        }
    }
}
