package com.fooddelivery.notification.config;

import com.fooddelivery.common.constants.NotificationTemplate;
import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Every notification the platform can send has copy, and every piece of copy is for something the
 * platform can send.
 *
 * <p>This is the check that was missing. `NotificationTemplateSeeder` seeded 16 (code, channel)
 * rows over 15 codes while the platform emitted 19, and they were not the same set: nine emitted
 * codes had no row and failed at every customer who needed one — including DELAY_APPROVAL_REQUESTED,
 * so nobody was ever asked to approve a delay and the order was auto-cancelled ten minutes later for
 * not answering. Five seeded rows were for codes nothing emitted.
 *
 * <p>Driven off `NotificationTemplate.values()`, so a new value with no copy fails here rather than
 * in production. It needs no database: the seeder's copy table is the thing under test, and the
 * seeder itself refuses to start when the table is incomplete.
 */
class TemplateCoverageTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");

    @Test
    void everyNotificationCodeHasCopy() {
        List<NotificationTemplate> uncovered = new ArrayList<>();
        for (NotificationTemplate code : NotificationTemplate.values()) {
            Map<ChannelType, String> byChannel = NotificationTemplateSeeder.COPY.get(code);
            if (byChannel == null || byChannel.isEmpty()) {
                uncovered.add(code);
            }
        }
        assertTrue(uncovered.isEmpty(),
                "NotificationTemplate values with no copy in NotificationTemplateSeeder.COPY: "
                        + uncovered + ". Each of these throws InvalidTemplateException at the first "
                        + "customer who needs it, and the only trace is a row in notification_audit_logs.");
    }

    @Test
    void everyPieceOfCopyIsForARealCode() {
        // The reverse inclusion. Without it the map could drift into copy for codes nothing emits --
        // which is exactly what five of the sixteen original rows were.
        for (NotificationTemplate code : NotificationTemplateSeeder.COPY.keySet()) {
            assertNotNull(code, "the map is keyed by the enum, so this cannot fail by construction "
                    + "-- it is here so the assertion is not silently dropped if the key type changes");
        }
        assertEquals(NotificationTemplate.values().length, NotificationTemplateSeeder.COPY.size(),
                "every value covered, and nothing extra");
    }

    @Test
    void noCopyIsBlank() {
        NotificationTemplateSeeder.COPY.forEach((code, byChannel) ->
                byChannel.forEach((channel, content) -> {
                    assertNotNull(content, code + " on " + channel);
                    assertFalse(content.isBlank(), code + " on " + channel + " has blank copy");
                }));
    }

    @Test
    void placeholdersAreContiguousFromOne() {
        // {1},{3} would leave {3} unsubstituted forever: hydrateTemplate replaces by index from the
        // emitter's list, so a gap is a hole no emitter can fill.
        NotificationTemplateSeeder.COPY.forEach((code, byChannel) ->
                byChannel.forEach((channel, content) -> {
                    java.util.TreeSet<Integer> used = new java.util.TreeSet<>();
                    Matcher m = PLACEHOLDER.matcher(content);
                    while (m.find()) {
                        used.add(Integer.parseInt(m.group(1)));
                    }
                    int expected = 1;
                    for (int index : used) {
                        assertEquals(expected++, index,
                                code + " on " + channel + " uses placeholders " + used
                                        + "; they must run 1..n with no gaps");
                    }
                }));
    }

    @Test
    void everyOrderLifecycleCodeReachesTheCustomerOnPush() {
        // PUSH is the channel the order lifecycle emits on. A lifecycle code seeded only for EMAIL
        // would look covered and never be delivered.
        List<NotificationTemplate> lifecycle = List.of(
                NotificationTemplate.ORDER_PLACED, NotificationTemplate.ORDER_PAID,
                NotificationTemplate.ORDER_READY_FOR_PICKUP, NotificationTemplate.DRIVER_ON_THE_WAY,
                NotificationTemplate.DELAY_APPROVAL_REQUESTED, NotificationTemplate.ORDER_DELAY_REJECTED,
                NotificationTemplate.ORDER_DELIVERED, NotificationTemplate.ORDER_CANCELLED_BY_RESTAURANT,
                NotificationTemplate.ORDER_CANCELLED_BY_ADMIN, NotificationTemplate.DISPATCH_FAILED,
                NotificationTemplate.DELIVERY_FAILED);

        for (NotificationTemplate code : lifecycle) {
            assertTrue(NotificationTemplateSeeder.COPY.get(code).containsKey(ChannelType.PUSH),
                    code + " is emitted on PUSH and has no PUSH copy");
        }
    }

    @Test
    void aCashOrderIsToldItIsCash() {
        String placed = NotificationTemplateSeeder.COPY
                .get(NotificationTemplate.ORDER_PLACED).get(ChannelType.PUSH);
        assertTrue(placed.toLowerCase().contains("cash"),
                "a COD customer has to know to have the money ready; this used to be notified with "
                        + "the ORDER_PAID copy, which says the opposite: " + placed);

        String paid = NotificationTemplateSeeder.COPY
                .get(NotificationTemplate.ORDER_PAID).get(ChannelType.PUSH);
        assertFalse(paid.toLowerCase().contains("cash"), paid);
    }

    @Test
    void theSeederWritesARowForEveryPieceOfCopy() {
        NotificationTemplateRepository repository = mock(NotificationTemplateRepository.class);
        when(repository.findByEventNameAndChannelAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.empty());

        new NotificationTemplateSeeder(repository).run();

        int rows = NotificationTemplateSeeder.COPY.values().stream()
                .mapToInt(Map::size).sum();
        verify(repository, times(rows))
                .save(any(com.fooddelivery.notification.domain.NotificationTemplate.class));
    }

    @Test
    void theSeederRefusesToStartWhenACodeHasNoCopy() {
        // The last line of defence, verified by breaking it rather than by asserting a size. A
        // value with no copy is the exact shape of B-3: nine emitted codes had no row, and the
        // only trace was a row per failure in notification_audit_logs, which nobody watches.
        NotificationTemplate victim = NotificationTemplate.DELAY_APPROVAL_REQUESTED;
        Map<ChannelType, String> saved = NotificationTemplateSeeder.COPY.remove(victim);
        assertNotNull(saved, "the fixture must actually remove something, or this proves nothing");
        try {
            NotificationTemplateRepository repository = mock(NotificationTemplateRepository.class);
            when(repository.findByEventNameAndChannelAndIsActiveTrue(any(), any()))
                    .thenReturn(Optional.empty());

            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> new NotificationTemplateSeeder(repository).run());
            assertTrue(e.getMessage().contains(victim.name()), e.getMessage());
            verify(repository, never())
                    .save(any(com.fooddelivery.notification.domain.NotificationTemplate.class));
        } finally {
            NotificationTemplateSeeder.COPY.put(victim, saved);
        }
        assertEquals(saved, NotificationTemplateSeeder.COPY.get(victim), "fixture restored");
    }
}
