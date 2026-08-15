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
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@lombok.extern.slf4j.Slf4j
public class ExotelSmsService {
    @java.lang.SuppressWarnings("all")

    @Value("${exotel.api.key}")
    private String apiKey;
    @Value("${exotel.api.token}")
    private String apiToken;
    @Value("${exotel.account.sid}")
    private String accountSid;
    @Value("${exotel.subdomain:api.in.exotel.com}")
    private String subdomain;
    @Value("${platform.webhook.base-url}")
    private String webhookBaseUrl;
    @Value("${platform.webhook.secret}")
    private String webhookSecret;
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String dispatchSms(String recipient, String content, String senderId, String dltEntityId, String dltTemplateId) throws Exception {
        String url = String.format("https://%s/v1/Accounts/%s/Sms/send.json", subdomain, accountSid);
        String authHeader = "Basic " + Base64.getEncoder().encodeToString((apiKey + ":" + apiToken).getBytes(StandardCharsets.UTF_8));
        Map<String, String> formData = Map.of("From", senderId, "To", recipient, "Body", content, "DltEntityId", dltEntityId, "DltTemplateId", dltTemplateId, "StatusCallback", webhookBaseUrl + "/webhooks/providers/exotel/status?token=" + webhookSecret // Instructs Exotel to POST back delivery updates
        );
        String formBody = formData.entrySet().stream().map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", authHeader).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(formBody)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode rootNode = objectMapper.readTree(response.body());
                // A 200 OK means the message is 'queued', not delivered. The Sid is used for tracking.
                return rootNode.path("SMSMessage").path("Sid").asText();
            } else if (response.statusCode() == 400 || response.statusCode() == 404) {
                throw new com.fooddelivery.notification.exception.RecipientUnreachableException("Exotel Client Error (400/404). HTTP " + response.statusCode());
            } else if (response.statusCode() >= 400 && response.statusCode() < 500) {
                throw new com.fooddelivery.notification.exception.InvalidPayloadException("Exotel Client Error (4xx). HTTP " + response.statusCode());
            } else if (response.statusCode() == 504 || response.statusCode() == 503 || response.statusCode() == 429) {
                throw new com.fooddelivery.notification.exception.ProviderGatewayTimeoutException("Exotel Timeout/Rate limit HTTP " + response.statusCode());
            } else {
                throw new RuntimeException("Exotel SMS Failed. HTTP " + response.statusCode() + " Response: " + response.body());
            }
        } catch (java.net.http.HttpTimeoutException e) {
            throw new com.fooddelivery.notification.exception.ProviderGatewayTimeoutException("Exotel Network Timeout");
        } catch (Exception e) {
            if (e instanceof com.fooddelivery.notification.exception.TerminalNotificationException || e instanceof com.fooddelivery.notification.exception.ProviderGatewayTimeoutException) {
                throw e;
            }
            throw new RuntimeException("Exotel Dispatch Failed", e);
        }
    }
}
