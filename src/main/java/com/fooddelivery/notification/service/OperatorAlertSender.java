package com.fooddelivery.notification.service;

import com.fooddelivery.notification.config.OperatorAlertProperties;

import java.util.List;
import java.util.Map;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.exception.InvalidPayloadException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Sends an {@code OPERATOR_ALERT} through Brevo for real, in every profile. On Dev the channel strategies are the mocks
 * of {@code DevNotificationChannelConfiguration} and every customer notification stays mocked; this is the one path
 * that is not, by owner decision (GatewayScrapeAndAlerting_2026-10-18 D-4). Not a {@code NotificationChannelStrategy}:
 * the router's strategy map is keyed by channel and already has an EMAIL strategy.
 */
@Component
@lombok.extern.slf4j.Slf4j
public class OperatorAlertSender {

    private final BrevoEmailService brevo;
    private final OperatorAlertProperties properties;
    private final Counter sent;
    private final Counter failed;

    public OperatorAlertSender(BrevoEmailService brevo, OperatorAlertProperties properties, MeterRegistry metrics) {
        this.brevo = brevo;
        this.properties = properties;
        // Registered at startup: increase() over a series that first appears at 1 is 0, so a lazy counter would never
        // fire OperatorAlertEmailFailing on the first failure.
        this.sent = metrics.counter("operator.alerts.email", "result", "sent");
        this.failed = metrics.counter("operator.alerts.email", "result", "failed");
    }

    /**
     * An {@code OPERATOR_ALERT} must be an EMAIL to exactly the configured operator address and carry no user. Anything
     * else on the topic with that template is refused (terminal), so a forged message cannot email an arbitrary
     * address.
     */
    public void requireAddressedToOperator(NotificationRequestEvent event) {
        if (event.getUserId() != null || event.getChannel() != ChannelType.EMAIL
                || event.getExplicitRecipient() == null
                || !properties.recipient().equalsIgnoreCase(event.getExplicitRecipient().trim())) {
            throw new InvalidPayloadException(
                    "OPERATOR_ALERT must be an EMAIL to the configured operator address, with no user");
        }
    }

    /** @return Brevo's message id */
    public String dispatch(NotificationRequestEvent event, NotificationTemplate template) throws Exception {
        Map<String, String> payload = event.getPayload();
        String subject = payload == null ? null : payload.get("subject");
        List<String> params = event.getTemplateParams();
        if (subject == null || subject.isBlank() || params == null || params.size() != 1) {
            throw new InvalidPayloadException("OPERATOR_ALERT needs payload.subject and exactly one template parameter");
        }
        // One placeholder by design (NotificationTemplate.OPERATOR_ALERT): the body may itself contain "{2}".
        String html = template.getContent().replace("{1}", params.get(0));
        try {
            String messageId = brevo.sendHtmlEmail(properties.sender(), properties.recipient(), subject, html);
            sent.increment();
            log.info("Operator alert sent: eventId={} subject={} messageId={}", event.getEventId(), subject, messageId);
            return messageId;
        } catch (Exception e) {
            failed.increment();
            log.warn("Operator alert not sent: eventId={} subject={} failure={}", event.getEventId(), subject,
                    e.getClass().getSimpleName());
            throw e;
        }
    }
}
