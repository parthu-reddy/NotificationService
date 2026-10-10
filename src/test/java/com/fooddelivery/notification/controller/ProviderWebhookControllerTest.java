package com.fooddelivery.notification.controller;

import com.fooddelivery.notification.domain.DeliveryStatus;
import com.fooddelivery.notification.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ProviderWebhookControllerTest {

    private final AuditLogService audit = mock(AuditLogService.class);
    private final ProviderWebhookController controller = new ProviderWebhookController(audit);

    ProviderWebhookControllerTest() {
        ReflectionTestUtils.setField(controller, "webhookSecret", "s3cret-webhook-value");
    }

    private static Map<String, String> delivered() {
        return Map.of("SmsSid", "SM1", "Status", "delivered", "DetailedStatus", "DELIVERED_TO_HANDSET");
    }

    @Test
    void theSharedTokenUpdatesTheDeliveryStatus() {
        assertThat(controller.handleExotelCallback(delivered(), "s3cret-webhook-value").getStatusCode().value()).isEqualTo(200);
        verify(audit).updateLogStatus("SM1", DeliveryStatus.DELIVERED, "DELIVERED_TO_HANDSET");
    }

    @Test
    void aWrongOrMissingTokenIsRefusedAndChangesNothing() {
        assertThat(controller.handleExotelCallback(delivered(), "s3cret-webhook-valuX").getStatusCode().value()).isEqualTo(401);
        assertThat(controller.handleExotelCallback(delivered(), "s3cret").getStatusCode().value()).isEqualTo(401);
        assertThat(controller.handleExotelCallback(delivered(), null).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(audit);
    }
}
