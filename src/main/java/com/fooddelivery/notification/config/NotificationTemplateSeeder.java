package com.fooddelivery.notification.config;

import com.fooddelivery.common.constants.NotificationTemplate;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes one active template row for every notification this platform can send.
 *
 * <p>Driven off {@link NotificationTemplate#values()}, not a hand-written list of calls. The
 * hand-written version seeded 16 (code, channel) pairs over 15 codes while the platform emitted 19,
 * and they were not the same set: nine emitted codes had no row and failed every time -- including
 * DELAY_APPROVAL_REQUESTED, so a customer was never asked to approve a delay and was then
 * auto-cancelled ten minutes later for not answering a question they never received. Five seeded
 * rows were for codes nothing emitted.
 *
 * <p>That was possible because the list named a mixture of {@code NotificationTemplate} values and
 * {@code EventType} values, and nothing could enumerate what was actually emitted. Now: the enum is
 * the vocabulary, {@code NotificationRequestEvent.eventName} is typed as it, and a value with no
 * copy here <strong>fails startup</strong> rather than failing silently at the first customer who
 * needs it.
 */
@Component
@lombok.extern.slf4j.Slf4j
public class NotificationTemplateSeeder implements CommandLineRunner {

    private final NotificationTemplateRepository repository;

    public NotificationTemplateSeeder(final NotificationTemplateRepository repository) {
        this.repository = repository;
    }

    /**
     * The copy, by code and channel.
     *
     * <p>{@code {1}}, {@code {2}}… are positional and substituted by
     * {@code NotificationChannelStrategy.hydrateTemplate} from the emitter's {@code templateParams}.
     * A body may not use a higher index than its emitter passes, or the customer receives the
     * literal text "{2}". {@code TemplateSubstitutionTest} pins that against the emitters.
     */
    static final Map<NotificationTemplate, Map<ChannelType, String>> COPY = build();

    private static Map<NotificationTemplate, Map<ChannelType, String>> build() {
        Map<NotificationTemplate, Map<ChannelType, String>> copy = new EnumMap<>(NotificationTemplate.class);

        // -- Order lifecycle. One parameter: {1} is the order id. -------------------------------
        push(copy, NotificationTemplate.ORDER_PAID,
                "Payment received. Order {1} is confirmed and the restaurant is preparing it.");
        push(copy, NotificationTemplate.ORDER_READY_FOR_PICKUP,
                "Order {1} is ready and waiting for a rider to collect it.");
        push(copy, NotificationTemplate.DRIVER_ON_THE_WAY,
                "A rider is on the way with order {1}.");
        push(copy, NotificationTemplate.DELAY_APPROVAL_REQUESTED,
                "The restaurant needs more time for order {1}. Open the app to accept the delay or "
                        + "cancel for a full refund -- we will cancel it for you if we do not hear back.");
        push(copy, NotificationTemplate.ORDER_DELAY_REJECTED,
                "Order {1} has been cancelled and you will be refunded in full.");
        push(copy, NotificationTemplate.ORDER_DELIVERED,
                "Order {1} has been delivered. Enjoy!");
        email(copy, NotificationTemplate.ORDER_DELIVERED,
                "Your order {1} has been delivered. Your receipt is available in the app.");

        // -- Endings. Each says who ended it, because each means something different. ------------
        push(copy, NotificationTemplate.ORDER_CANCELLED_BY_RESTAURANT,
                "We are sorry -- the restaurant could not fulfil order {1}. You will be refunded in full.");
        push(copy, NotificationTemplate.ORDER_CANCELLED_BY_ADMIN,
                "Order {1} has been cancelled by our support team. You will be refunded in full.");
        push(copy, NotificationTemplate.DISPATCH_FAILED,
                "We could not find a rider for order {1}, so we have cancelled it and you will be "
                        + "refunded in full. We are sorry.");
        push(copy, NotificationTemplate.DELIVERY_FAILED,
                "Order {1} could not be delivered. You will be refunded in full and our support team "
                        + "will be in touch.");

        // -- Refunds. {2} is the amount; REFUND_REQUESTED also has {3}, the destination. ---------
        push(copy, NotificationTemplate.REFUND_REQUESTED,
                "We have started a refund of {2} for order {1}. It will be returned to {3}.");
        push(copy, NotificationTemplate.PAYMENT_REFUNDED,
                "Your refund of {2} for order {1} has been processed.");
        email(copy, NotificationTemplate.PAYMENT_REFUNDED,
                "Your refund of {2} for order {1} has been processed. It may take a few days to "
                        + "appear on your statement.");
        push(copy, NotificationTemplate.PAYMENT_PARTIALLY_REFUNDED,
                "A partial refund of {2} for order {1} has been processed.");
        email(copy, NotificationTemplate.PAYMENT_PARTIALLY_REFUNDED,
                "A partial refund of {2} for order {1} has been processed.");
        push(copy, NotificationTemplate.REFUND_FAILED,
                "We could not process the refund of {2} for order {1}. Our support team is looking "
                        + "into it and will contact you.");
        email(copy, NotificationTemplate.REFUND_FAILED,
                "We could not process the refund of {2} for order {1}. Our support team is looking "
                        + "into it and will contact you.");

        // -- Not the order lifecycle. These carry a payload, not positional parameters. ----------
        push(copy, NotificationTemplate.NEW_ORDER_DISPATCH,
                "Order {1} is available for you. Open the app to accept it.");
        put(copy, NotificationTemplate.OTP_LOGIN, ChannelType.SMS,
                "Your OTP is {1}. It is valid for 5 minutes.");
        put(copy, NotificationTemplate.OTP_LOGIN, ChannelType.EMAIL,
                "Your OTP is {1}. It is valid for 5 minutes.");
        email(copy, NotificationTemplate.AD_CAMPAIGN_PAUSED,
                "Your campaign has been paused. Sign in to review its budget and resume it.");
        email(copy, NotificationTemplate.BUDGET_RUNNING_LOW,
                "Your campaign has under 20% of its budget left. Sign in to top it up before it "
                        + "stops serving.");

        return copy;
    }

    private static void push(Map<NotificationTemplate, Map<ChannelType, String>> copy,
                             NotificationTemplate code, String content) {
        put(copy, code, ChannelType.PUSH, content);
    }

    private static void email(Map<NotificationTemplate, Map<ChannelType, String>> copy,
                              NotificationTemplate code, String content) {
        put(copy, code, ChannelType.EMAIL, content);
    }

    private static void put(Map<NotificationTemplate, Map<ChannelType, String>> copy,
                            NotificationTemplate code, ChannelType channel, String content) {
        copy.computeIfAbsent(code, k -> new EnumMap<>(ChannelType.class)).put(channel, content);
    }

    @Override
    public void run(String... args) {
        List<NotificationTemplate> uncovered = java.util.Arrays.stream(NotificationTemplate.values())
                .filter(code -> !COPY.containsKey(code))
                .toList();
        if (!uncovered.isEmpty()) {
            // Fail startup rather than the first customer who needs one. A missing template is a
            // configuration fault and there is no useful degraded mode: the notification is simply
            // never delivered, and the only trace is a line in an audit table nobody watches.
            throw new IllegalStateException(
                    "No notification copy defined for " + uncovered + ". Every NotificationTemplate "
                            + "value must have at least one channel in NotificationTemplateSeeder.COPY.");
        }

        int written = 0;
        for (Map.Entry<NotificationTemplate, Map<ChannelType, String>> byCode : COPY.entrySet()) {
            for (Map.Entry<ChannelType, String> byChannel : byCode.getValue().entrySet()) {
                written += seedTemplate(byCode.getKey().name(), byChannel.getKey(), byChannel.getValue());
            }
        }
        log.info("Notification templates ready: {} codes, {} rows written or updated.",
                COPY.size(), written);
    }

    /** @return 1 if a row was written or its content changed, 0 if it was already correct. */
    private int seedTemplate(String eventName, ChannelType channel, String content) {
        Optional<com.fooddelivery.notification.domain.NotificationTemplate> existing =
                repository.findByEventNameAndChannelAndIsActiveTrue(eventName, channel);
        if (existing.isEmpty()) {
            com.fooddelivery.notification.domain.NotificationTemplate template =
                    new com.fooddelivery.notification.domain.NotificationTemplate();
            template.setEventName(eventName);
            template.setChannel(channel);
            template.setContent(content);
            template.setIsActive(true);
            repository.save(template);
            log.info("Seeded template for {} on {}", eventName, channel);
            return 1;
        }
        if (!existing.get().getContent().equals(content)) {
            com.fooddelivery.notification.domain.NotificationTemplate template = existing.get();
            template.setContent(content);
            repository.save(template);
            log.info("Updated template content for {} on {}", eventName, channel);
            return 1;
        }
        return 0;
    }
}
