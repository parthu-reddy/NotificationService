package com.fooddelivery.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.notification.exception.ProviderGatewayTimeoutException;
import com.fooddelivery.notification.exception.RecipientUnreachableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.net.URLEncoder;

@Service
public class TwilioSmsService {
    @java.lang.SuppressWarnings("all")
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TwilioSmsService.class);
    @Value("${twilio.account.sid:dummy}")
    private String accountSid;
    @Value("${twilio.auth.token:dummy}")
    private String authToken;
    @Value("${twilio.phone.number:dummy}")
    private String fromNumber;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public String dispatchSms(String recipient, String content) {
        String url = String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json", accountSid);
        String auth = "Basic " + Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
        String formBody = "From=" + URLEncoder.encode(fromNumber, StandardCharsets.UTF_8) + "&To=" + URLEncoder.encode(recipient, StandardCharsets.UTF_8) + "&Body=" + URLEncoder.encode(content, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", auth).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(formBody)).timeout(Duration.ofSeconds(10)).build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                com.fasterxml.jackson.databind.JsonNode rootNode = new ObjectMapper().readTree(response.body());
                return rootNode.path("sid").asText();
            } else if (response.statusCode() == 400 || response.statusCode() == 404) {
                throw new RecipientUnreachableException("Twilio: Recipient unreachable or invalid payload. HTTP " + response.statusCode());
            } else if (response.statusCode() == 429 || response.statusCode() == 503 || response.statusCode() == 504) {
                throw new ProviderGatewayTimeoutException("Twilio Timeout/Rate limit HTTP " + response.statusCode());
            } else {
                throw new RuntimeException("Twilio Dispatch Failed. HTTP " + response.statusCode());
            }
        } catch (java.net.http.HttpTimeoutException e) {
            throw new ProviderGatewayTimeoutException("Twilio Network Timeout");
        } catch (Exception e) {
            if (e instanceof RecipientUnreachableException || e instanceof ProviderGatewayTimeoutException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException("Twilio IO Exception", e);
        }
    }
}
