package com.fooddelivery.notification.service;

import com.fooddelivery.notification.domain.ChannelType;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.domain.UserPreference;
import com.fooddelivery.notification.dto.NotificationRequestEvent;
import com.fooddelivery.notification.exception.InvalidPayloadException;
import com.fooddelivery.notification.exception.UserOptedOutException;
import com.fooddelivery.notification.repository.NotificationAuditLogRepository;
import com.fooddelivery.notification.repository.NotificationTemplateRepository;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import com.fooddelivery.notification.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationRouterServiceTest {

    @Mock
    private RateLimitingService rateLimitingService;
    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private UserPreferenceRepository userPreferenceRepository;
    @Mock
    private UserDeviceRepository userDeviceRepository;
    @Mock
    private NotificationAuditLogRepository auditLogRepository;
    @Mock
    private FcmService fcmService;
    @Mock
    private ExotelSmsService exotelSmsService;
    @Mock
    private GupshupWhatsAppService gupshupWhatsAppService;
    @Mock
    private AwsSesEmailService awsSesEmailService;
    @Mock
    private BrevoEmailService brevoEmailService;
    @Mock
    private TwilioSmsService twilioSmsService;

    @InjectMocks
    private NotificationRouterService notificationRouterService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationRouterService, "activeEmailProvider", "aws");
    }

    @Test
    void testRouteAndDispatch_InvalidPayload() {
        NotificationRequestEvent event = new NotificationRequestEvent();
        assertThrows(InvalidPayloadException.class, () -> notificationRouterService.routeAndDispatch(event));
    }

    @Test
    void testRouteAndDispatch_UserOptedOut() {
        NotificationRequestEvent event = new NotificationRequestEvent();
        UUID userId = UUID.randomUUID();
        event.setUserId(userId);
        event.setEventName("ORDER_PLACED");
        event.setChannel(ChannelType.SMS);
        event.setExplicitRecipient("1234567890");

        UserPreference prefs = new UserPreference();
        prefs.setSmsEnabled(false);
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));

        assertThrows(UserOptedOutException.class, () -> notificationRouterService.routeAndDispatch(event));
    }

    @Test
    void testRouteAndDispatch_SmsSuccess() throws Exception {
        NotificationRequestEvent event = new NotificationRequestEvent();
        UUID userId = UUID.randomUUID();
        event.setUserId(userId);
        event.setEventName("ORDER_PLACED");
        event.setChannel(ChannelType.SMS);
        event.setExplicitRecipient("1234567890");

        UserPreference prefs = new UserPreference();
        prefs.setSmsEnabled(true);
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));

        NotificationTemplate template = new NotificationTemplate();
        template.setContent("Your order is placed");
        when(templateRepository.findByEventNameAndChannelAndIsActiveTrue("ORDER_PLACED", ChannelType.SMS))
                .thenReturn(Optional.of(template));

        when(exotelSmsService.dispatchSms(anyString(), anyString(), anyString(), any(), any())).thenReturn("sms-id-123");

        assertDoesNotThrow(() -> notificationRouterService.routeAndDispatch(event));
        verify(auditLogRepository, times(1)).save(any());
        verify(rateLimitingService, times(1)).enforceRateLimit(userId.toString(), "ORDER_PLACED");
    }

    @Test
    void testRouteAndDispatch_EmailSuccess() throws Exception {
        NotificationRequestEvent event = new NotificationRequestEvent();
        UUID userId = UUID.randomUUID();
        event.setUserId(userId);
        event.setEventName("ORDER_DELIVERED");
        event.setChannel(ChannelType.EMAIL);
        event.setExplicitRecipient("user@example.com");

        UserPreference prefs = new UserPreference();
        prefs.setEmailEnabled(true);
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));

        NotificationTemplate template = new NotificationTemplate();
        template.setContent("Your order is delivered");
        when(templateRepository.findByEventNameAndChannelAndIsActiveTrue("ORDER_DELIVERED", ChannelType.EMAIL))
                .thenReturn(Optional.of(template));

        when(awsSesEmailService.sendHtmlEmail(anyString(), anyString(), anyString(), anyString())).thenReturn("email-id-123");

        assertDoesNotThrow(() -> notificationRouterService.routeAndDispatch(event));
        verify(auditLogRepository, times(1)).save(any());
    }
}
