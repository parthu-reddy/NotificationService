package com.fooddelivery.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class GupshupWhatsAppService {

    @Value("${gupshup.api.key}")
    private String apiKey;

    @Value("${gupshup.source.number}")
    private String sourceNumber;

    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String dispatchWhatsAppTemplate(String destination, String templateId, List<String> templateParams) throws Exception {
        String endpoint = "https://api.gupshup.io/wa/api/v1/template/msg";

        // Construct the strictly required template object
        Map<String, Object> templateObj = Map.of(
                "id", templateId,
                "params", templateParams != null ? templateParams : List.of()
        );
        String templateJson = objectMapper.writeValueAsString(templateObj);

        String requestBody = "source=" + URLEncoder.encode(sourceNumber, StandardCharsets.UTF_8) +
                "&destination=" + URLEncoder.encode(destination, StandardCharsets.UTF_8) +
                "&template=" + URLEncoder.encode(templateJson, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("apikey", apiKey)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode rootNode = objectMapper.readTree(response.body());
                return rootNode.path("messageId").asText();
            } else if (response.statusCode() == 400 || response.statusCode() == 404) {
                throw new com.fooddelivery.notification.exception.RecipientUnreachableException("Gupshup Client Error (400/404). HTTP " + response.statusCode());
            } else if (response.statusCode() >= 400 && response.statusCode() < 500) {
                throw new com.fooddelivery.notification.exception.InvalidPayloadException("Gupshup Client Error (4xx). HTTP " + response.statusCode());
            } else if (response.statusCode() == 504 || response.statusCode() == 503 || response.statusCode() == 429) {
                throw new com.fooddelivery.notification.exception.ProviderGatewayTimeoutException("Gupshup Timeout/Rate limit HTTP " + response.statusCode());
            } else {
                throw new RuntimeException("Gupshup WhatsApp Failed. HTTP " + response.statusCode() + " Response: " + response.body());
            }
        } catch (java.net.http.HttpTimeoutException e) {
            throw new com.fooddelivery.notification.exception.ProviderGatewayTimeoutException("Gupshup Network Timeout");
        } catch (Exception e) {
            if (e instanceof com.fooddelivery.notification.exception.TerminalNotificationException || e instanceof com.fooddelivery.notification.exception.ProviderGatewayTimeoutException) {
                throw e;
            }
            throw new RuntimeException("Gupshup Dispatch Failed", e);
        }
    }
}
