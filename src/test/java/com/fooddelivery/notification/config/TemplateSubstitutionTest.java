package com.fooddelivery.notification.config;

import com.fooddelivery.common.constants.NotificationTemplate;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.notification.service.strategy.NotificationChannelStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * No template asks for more parameters than its emitter passes.
 *
 * <p>`hydrateTemplate` substitutes `{1}..{n}` positionally from the emitter's `templateParams` and
 * leaves anything it has no value for exactly as written. A body with more placeholders than the
 * emitter supplies is therefore sent to a customer with the literal text `{2}` in it.
 *
 * <p>Two shipped that way and this test is what found them: `OTP_LOGIN` read
 * "Your OTP is {1}." while IdentityService passed the code only in the FCM `payload`, which SMS and
 * EMAIL never read — so every login SMS said "Your OTP is {1}." `NEW_ORDER_DISPATCH` had the same
 * shape. Both emitters now pass a template parameter.
 *
 * <p>The expected counts come from `tools/collect_emitted_codes.py`, which scans every emitter in
 * every service. They are restated here rather than read from that file so this test needs nothing
 * outside its own module — and `emittedCountsMatchTheScan` is the check that they have not drifted.
 */
class TemplateSubstitutionTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");

    /** code -> how many positional parameters its emitters actually pass. */
    private static final Map<NotificationTemplate, Integer> EMITTED_PARAMS = Map.ofEntries(
            Map.entry(NotificationTemplate.ORDER_PAID, 1),
            Map.entry(NotificationTemplate.ORDER_READY_FOR_PICKUP, 1),
            Map.entry(NotificationTemplate.DRIVER_ON_THE_WAY, 1),
            Map.entry(NotificationTemplate.DELAY_APPROVAL_REQUESTED, 1),
            Map.entry(NotificationTemplate.ORDER_DELAY_REJECTED, 1),
            Map.entry(NotificationTemplate.ORDER_DELIVERED, 1),
            Map.entry(NotificationTemplate.ORDER_CANCELLED_BY_RESTAURANT, 1),
            Map.entry(NotificationTemplate.ORDER_CANCELLED_BY_ADMIN, 1),
            Map.entry(NotificationTemplate.DISPATCH_FAILED, 1),
            Map.entry(NotificationTemplate.DELIVERY_FAILED, 1),
            Map.entry(NotificationTemplate.REFUND_REQUESTED, 3),
            Map.entry(NotificationTemplate.PAYMENT_REFUNDED, 2),
            Map.entry(NotificationTemplate.PAYMENT_PARTIALLY_REFUNDED, 2),
            Map.entry(NotificationTemplate.REFUND_FAILED, 2),
            Map.entry(NotificationTemplate.NEW_ORDER_DISPATCH, 1),
            Map.entry(NotificationTemplate.OTP_LOGIN, 1),
            Map.entry(NotificationTemplate.AD_CAMPAIGN_PAUSED, 0),
            Map.entry(NotificationTemplate.BUDGET_RUNNING_LOW, 0));

    private static int highestPlaceholder(String content) {
        int highest = 0;
        Matcher m = PLACEHOLDER.matcher(content);
        while (m.find()) {
            highest = Math.max(highest, Integer.parseInt(m.group(1)));
        }
        return highest;
    }

    @Test
    void everyCodeHasAnExpectedParameterCount() {
        // Without this the map could quietly omit a code and the check below would skip it.
        for (NotificationTemplate code : NotificationTemplate.values()) {
            assertTrue(EMITTED_PARAMS.containsKey(code),
                    code + " has no expected parameter count. Re-run "
                            + "tools/collect_emitted_codes.py and add it.");
        }
    }

    @Test
    void noTemplateUsesMoreParametersThanItsEmitterPasses() {
        NotificationTemplateSeeder.COPY.forEach((code, byChannel) -> {
            int available = EMITTED_PARAMS.get(code);
            byChannel.forEach((channel, content) -> {
                int highest = highestPlaceholder(content);
                assertTrue(highest <= available,
                        code + " on " + channel + " uses {" + highest + "} but its emitter passes "
                                + available + " parameter(s). The customer receives the literal "
                                + "text \"{" + highest + "}\". Copy: " + content);
            });
        });
    }

    /** Hydration really does leave an unmatched placeholder in the text — the reason this matters. */
    @Test
    void anUnmatchedPlaceholderReachesTheCustomerVerbatim() {
        NotificationChannelStrategy strategy = new NotificationChannelStrategy() {
            @Override
            public ChannelType getSupportedChannel() {
                return ChannelType.SMS;
            }

            @Override
            public String dispatch(com.fooddelivery.common.event.NotificationRequestEvent event,
                                   com.fooddelivery.notification.domain.NotificationTemplate template) {
                return null;
            }
        };

        assertEquals("Your OTP is {1}.", strategy.hydrateTemplate("Your OTP is {1}.", null),
                "no parameters means the placeholder is sent as written -- this is what every "
                        + "login SMS said before the emitter was fixed");
        assertEquals("Your OTP is {1}.", strategy.hydrateTemplate("Your OTP is {1}.", List.of()));
        assertEquals("Your OTP is 4821.", strategy.hydrateTemplate("Your OTP is {1}.", List.of("4821")));
        assertEquals("Refund 50 for 9 to {3}.",
                strategy.hydrateTemplate("Refund {2} for {1} to {3}.", List.of("9", "50")),
                "a body with three placeholders and two parameters leaks {3}");
    }

    @Test
    void theRefundCopyUsesAllThreeOfItsParameters() {
        // REFUND_REQUESTED is the only three-parameter code. Copy that ignores the destination
        // would tell a customer money is coming back without saying where to.
        String content = NotificationTemplateSeeder.COPY
                .get(NotificationTemplate.REFUND_REQUESTED).get(ChannelType.PUSH);
        assertEquals(3, highestPlaceholder(content), content);
    }
}
