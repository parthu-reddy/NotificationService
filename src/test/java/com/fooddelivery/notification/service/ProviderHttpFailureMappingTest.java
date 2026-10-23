package com.fooddelivery.notification.service;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fooddelivery.common.constants.NotificationTemplate;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.config.OperatorAlertProperties;
import com.fooddelivery.notification.exception.InvalidPayloadException;
import com.fooddelivery.notification.exception.ProviderAuthenticationException;
import com.fooddelivery.notification.exception.ProviderGatewayTimeoutException;
import com.fooddelivery.notification.exception.RecipientUnreachableException;
import com.fooddelivery.notification.exception.TerminalNotificationException;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import com.fooddelivery.notification.repository.UserPreferenceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * How each provider's HTTP failure becomes an exception, and so whether the consumer retries it
 * ({@code TerminalNotificationException} is excluded from retry) and what the log and dead-letter reason say. A rejected
 * API key (401/403) is its own type, not a malformed message: on Dev 2026-10-20 a Brevo 401 was logged and dead-lettered
 * as InvalidPayloadException.
 */
class ProviderHttpFailureMappingTest {

    @SuppressWarnings("unchecked")
    private static HttpClient answering(int status) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn("{\"code\":\"error\"}");
        HttpClient client = mock(HttpClient.class);
        doReturn(response).when(client).send(any(), any());
        return client;
    }

    private static BrevoEmailService brevo(HttpClient client) {
        var service = new BrevoEmailService();
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        ReflectionTestUtils.setField(service, "httpClient", client);
        return service;
    }

    /** One send through the named provider's real status ladder, its HTTP client answering {@code status}. */
    private static Executable send(String provider, int status) throws Exception {
        HttpClient client = answering(status);
        switch (provider) {
            case "Brevo": {
                var service = brevo(client);
                return () -> service.sendHtmlEmail("from@example.test", "to@example.test", "s", "<p>b</p>");
            }
            case "Exotel": {
                var service = new ExotelSmsService();
                for (String field : List.of("apiKey", "apiToken", "accountSid", "subdomain", "webhookBaseUrl", "webhookSecret")) {
                    ReflectionTestUtils.setField(service, field, "test");
                }
                ReflectionTestUtils.setField(service, "httpClient", client);
                return () -> service.dispatchSms("+910000000000", "body", "FOODDL", "entity", "template");
            }
            case "Gupshup": {
                var service = new GupshupWhatsAppService();
                ReflectionTestUtils.setField(service, "apiKey", "test");
                ReflectionTestUtils.setField(service, "sourceNumber", "910000000000");
                ReflectionTestUtils.setField(service, "httpClient", client);
                return () -> service.dispatchWhatsAppTemplate("910000000001", "template", List.of("x"));
            }
            case "Twilio": {
                var service = new TwilioSmsService();
                for (String field : List.of("accountSid", "authToken", "fromNumber")) {
                    ReflectionTestUtils.setField(service, field, "test");
                }
                ReflectionTestUtils.setField(service, "httpClient", client);
                return () -> service.dispatchSms("+910000000000", "body");
            }
            default:
                throw new IllegalArgumentException(provider);
        }
    }

    @ParameterizedTest(name = "{0} HTTP {1}")
    @CsvSource({"Brevo,401", "Brevo,403", "Exotel,401", "Exotel,403", "Gupshup,401", "Gupshup,403"})
    void aRejectedCredentialIsAnAuthenticationFailureNotABadPayload(String provider, int status) throws Exception {
        Throwable failure = assertThrows(Throwable.class, send(provider, status));
        assertInstanceOf(ProviderAuthenticationException.class, failure, provider + " " + status + " -> " + failure);
        // Still terminal: the retry topic would send the same key seconds later.
        assertInstanceOf(TerminalNotificationException.class, failure);
        // The message is what the dead-letter reason and the audit row carry.
        assertTrue(failure.getMessage().contains(provider + " rejected the credentials"), failure.getMessage());
        assertTrue(failure.getMessage().endsWith("HTTP " + status), failure.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Brevo", "Exotel", "Gupshup"})
    void aMalformedMessageIsStillAnInvalidPayload(String provider) throws Exception {
        Throwable failure = assertThrows(Throwable.class, send(provider, 422));
        assertEquals(InvalidPayloadException.class, failure.getClass(), String.valueOf(failure));
        assertEquals(RecipientUnreachableException.class, assertThrows(Throwable.class, send(provider, 400)).getClass());
        assertEquals(RecipientUnreachableException.class, assertThrows(Throwable.class, send(provider, 404)).getClass());
    }

    /**
     * Each ladder has its own "Timeout/Rate limit" branch for 429, but the general 4xx branch above it caught 429
     * first, so a rate limit was dead-lettered as a bad payload instead of retried (and counted toward SMS failover).
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Brevo", "Exotel", "Gupshup"})
    void aRateLimitIsRetriedNotDeadLettered(String provider) throws Exception {
        Throwable failure = assertThrows(Throwable.class, send(provider, 429));
        assertEquals(ProviderGatewayTimeoutException.class, failure.getClass(), String.valueOf(failure));
        assertFalse(failure instanceof TerminalNotificationException);
        assertEquals(ProviderGatewayTimeoutException.class, assertThrows(Throwable.class, send(provider, 503)).getClass());
    }

    /**
     * Twilio never mapped 4xx beyond 400/404 to a terminal type: a 401 is retried like any other failure, and its own
     * catch-all re-wraps it, so the status is only in the cause. Unchanged here (retrying it is the owner's call).
     */
    @Test
    void twilioStillRetriesARejectedCredential() throws Exception {
        Throwable failure = assertThrows(Throwable.class, send("Twilio", 401));
        assertEquals(RuntimeException.class, failure.getClass());
        assertEquals("Twilio IO Exception", failure.getMessage());
        assertEquals("Twilio Dispatch Failed. HTTP 401", failure.getCause().getMessage());
    }

    private static Executable ses(int status) {
        var service = new AwsSesEmailService();
        SesV2Client client = mock(SesV2Client.class);
        when(client.sendEmail(any(SendEmailRequest.class))).thenThrow((SesV2Exception) SesV2Exception.builder()
                .statusCode(status).awsErrorDetails(AwsErrorDetails.builder().errorMessage("ses says no").build()).build());
        ReflectionTestUtils.setField(service, "sesV2Client", client);
        return () -> service.sendHtmlEmail("from@example.test", "to@example.test", "s", "<p>b</p>");
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {401, 403})
    void aSesCredentialRejectionIsAnAuthenticationFailure(int status) {
        Throwable failure = assertThrows(Throwable.class, ses(status));
        assertInstanceOf(ProviderAuthenticationException.class, failure, String.valueOf(failure));
        assertEquals("AWS SES rejected the credentials (401/403): ses says no", failure.getMessage());
    }

    @Test
    void aSesMalformedMessageIsStillAnInvalidPayload() {
        assertEquals(InvalidPayloadException.class, assertThrows(Throwable.class, ses(422)).getClass());
        assertEquals(RecipientUnreachableException.class, assertThrows(Throwable.class, ses(400)).getClass());
    }

    /**
     * The path seen on Dev: an operator alert, Brevo answering 401. It reaches the consumer as an authentication
     * failure, terminal, and OperatorAlertEmailFailing's counter still sees it.
     */
    @Test
    void anOperatorAlertRejectedByBrevoIsCountedAndTerminalAsAnAuthenticationFailure() throws Exception {
        var metrics = new SimpleMeterRegistry();
        var sender = new OperatorAlertSender(brevo(answering(401)),
                new OperatorAlertProperties("test-operator-alert-token-0123456789abcdef", "operator@example.test", "alerts@example.test"), metrics);
        var templates = mock(NotificationTemplateRepository.class);
        var template = new com.fooddelivery.notification.domain.NotificationTemplate();
        template.setContent("{1}");
        when(templates.findByEventNameAndChannelAndIsActiveTrue("OPERATOR_ALERT", ChannelType.EMAIL)).thenReturn(Optional.of(template));
        var audit = mock(NotificationAuditLogRepository.class);
        var router = new NotificationRouterService(templates, mock(UserPreferenceRepository.class), mock(UserDeviceRepository.class),
                audit, List.of(), metrics, sender);
        var alert = NotificationRequestEvent.builder().eventName(NotificationTemplate.OPERATOR_ALERT).channel(ChannelType.EMAIL)
                .explicitRecipient("operator@example.test").templateParams(List.of("<p>body</p>"))
                .payload(Map.of("subject", "[FIRING:1] X")).build();

        Throwable failure = assertThrows(Throwable.class, () -> router.routeAndDispatch(alert));

        assertInstanceOf(ProviderAuthenticationException.class, failure, String.valueOf(failure));
        assertEquals(1.0, metrics.counter("operator.alerts.email", "result", "failed").count());
        assertEquals(0.0, metrics.counter("operator.alerts.email", "result", "sent").count());
        verify(audit, never()).save(any());
    }
}
