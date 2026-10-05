package com.fooddelivery.notification.service;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.notification.domain.NotificationTemplate;
import com.fooddelivery.notification.domain.UserPreference;
import com.fooddelivery.common.event.NotificationRequestEvent;
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
import com.fooddelivery.notification.service.strategy.NotificationChannelStrategy;
import com.fooddelivery.common.service.RateLimitingService;

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

    @Mock
    private NotificationChannelStrategy smsStrategy;
    @Mock
    private NotificationChannelStrategy emailStrategy;

    private NotificationRouterService notificationRouterService;

    @BeforeEach
    void setUp() {
        when(smsStrategy.getSupportedChannel()).thenReturn(ChannelType.SMS);
        when(emailStrategy.getSupportedChannel()).thenReturn(ChannelType.EMAIL);
        notificationRouterService = new NotificationRouterService(
            templateRepository, userPreferenceRepository,
            userDeviceRepository, auditLogRepository,
            java.util.List.of(smsStrategy, emailStrategy),
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );
        // rateLimitingService moved from constructor injection to @Autowired(required = false)
        // field injection, so it must be set directly here to keep the rate-limit assertion.
        org.springframework.test.util.ReflectionTestUtils.setField(
            notificationRouterService, "rateLimitingService", rateLimitingService);
    }

    @Test
    void testRouteAndDispatch_InvalidPayload() {
        NotificationRequestEvent event = new NotificationRequestEvent();
        assertThrows(InvalidPayloadException.class, () -> notificationRouterService.routeAndDispatch(event));
    }

    @Test
    void signupOtpCanBeDeliveredAndAuditedBeforeAnAccountExists() throws Exception {
        var event = NotificationRequestEvent.builder()
                .eventName(com.fooddelivery.common.constants.NotificationTemplate.OTP_LOGIN)
                .channel(ChannelType.SMS).explicitRecipient("8999123456").build();
        var template = new NotificationTemplate();
        when(templateRepository.findByEventNameAndChannelAndIsActiveTrue("OTP_LOGIN", ChannelType.SMS))
                .thenReturn(Optional.of(template));
        when(smsStrategy.dispatch(event, template)).thenReturn("mock-signup-otp");
        notificationRouterService.routeAndDispatch(event);
        verifyNoInteractions(userPreferenceRepository);
        var captor = org.mockito.ArgumentCaptor.forClass(com.fooddelivery.notification.domain.NotificationAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertNull(captor.getValue().getUserId());
        assertEquals(event.getEventId(), captor.getValue().getEventId());
        assertEquals("8999123456", captor.getValue().getRecipientAddress());
        verify(rateLimitingService).enforceRateLimit(anyString(), eq("OTP_LOGIN"));
    }

    @Test
    void organisationInvitationReachesAPhoneWithNoAccountUnderItsOwnRateLimitSubject() throws Exception {
        var event = NotificationRequestEvent.builder()
                .eventName(com.fooddelivery.common.constants.NotificationTemplate.ORGANISATION_INVITATION)
                .channel(ChannelType.SMS).explicitRecipient("8999123456").templateParams(java.util.List.of("Manager")).build();
        var template = new NotificationTemplate();
        when(templateRepository.findByEventNameAndChannelAndIsActiveTrue("ORGANISATION_INVITATION", ChannelType.SMS))
                .thenReturn(Optional.of(template));
        when(smsStrategy.dispatch(event, template)).thenReturn("mock-invitation");
        notificationRouterService.routeAndDispatch(event);
        verifyNoInteractions(userPreferenceRepository);
        var captor = org.mockito.ArgumentCaptor.forClass(com.fooddelivery.notification.domain.NotificationAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertNull(captor.getValue().getUserId());
        assertEquals("8999123456", captor.getValue().getRecipientAddress());
        var subject = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(rateLimitingService).enforceRateLimit(subject.capture(), eq("ORGANISATION_INVITATION"));
        // Invitations must not share (and so exhaust) the sign-in OTP budget for the same phone.
        org.junit.jupiter.api.Assertions.assertTrue(subject.getValue().startsWith("phone-organisation_invitation:"));
        org.junit.jupiter.api.Assertions.assertFalse(subject.getValue().contains("8999123456"));
    }

    @Test
    void missingAccountIsNotAllowedForOtherEventsOrPushOtp() {
        for (var event : java.util.List.of(
                NotificationRequestEvent.builder().eventName(com.fooddelivery.common.constants.NotificationTemplate.ORDER_PAID)
                        .channel(ChannelType.SMS).explicitRecipient("8999123456").build(),
                NotificationRequestEvent.builder().eventName(com.fooddelivery.common.constants.NotificationTemplate.OTP_LOGIN)
                        .channel(ChannelType.PUSH).explicitRecipient("8999123456").build(),
                NotificationRequestEvent.builder().eventName(com.fooddelivery.common.constants.NotificationTemplate.ORGANISATION_INVITATION)
                        .channel(ChannelType.PUSH).explicitRecipient("8999123456").build(),
                NotificationRequestEvent.builder().eventName(com.fooddelivery.common.constants.NotificationTemplate.APPLICATION_APPROVED)
                        .channel(ChannelType.SMS).explicitRecipient("8999123456").build())) {
            assertThrows(InvalidPayloadException.class, () -> notificationRouterService.routeAndDispatch(event));
        }
        verifyNoInteractions(auditLogRepository, templateRepository);
    }

    @Test
    void testRouteAndDispatch_UserOptedOut() {
        NotificationRequestEvent event = new NotificationRequestEvent();
        UUID userId = UUID.randomUUID();
        event.setUserId(userId);
        event.setEventName(com.fooddelivery.common.constants.NotificationTemplate.ORDER_PAID);
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
        event.setEventName(com.fooddelivery.common.constants.NotificationTemplate.ORDER_PAID);
        event.setChannel(ChannelType.SMS);
        event.setExplicitRecipient("1234567890");

        UserPreference prefs = new UserPreference();
        prefs.setSmsEnabled(true);
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));

        NotificationTemplate template = new NotificationTemplate();
        template.setContent("Your order is placed");
        when(templateRepository.findByEventNameAndChannelAndIsActiveTrue(com.fooddelivery.common.constants.NotificationTemplate.ORDER_PAID.name(), ChannelType.SMS))
                .thenReturn(Optional.of(template));

        when(smsStrategy.dispatch(any(), any())).thenReturn("sms-id-123");

        assertDoesNotThrow(() -> notificationRouterService.routeAndDispatch(event));
        verify(auditLogRepository, times(1)).save(any());
        verify(rateLimitingService, times(1)).enforceRateLimit(userId.toString(), com.fooddelivery.common.constants.NotificationTemplate.ORDER_PAID.name());
    }

    @Test
    void testRouteAndDispatch_EmailSuccess() throws Exception {
        NotificationRequestEvent event = new NotificationRequestEvent();
        UUID userId = UUID.randomUUID();
        event.setUserId(userId);
        event.setEventName(com.fooddelivery.common.constants.NotificationTemplate.ORDER_DELIVERED);
        event.setChannel(ChannelType.EMAIL);
        event.setExplicitRecipient("user@example.com");

        UserPreference prefs = new UserPreference();
        prefs.setEmailEnabled(true);
        when(userPreferenceRepository.findByUserId(userId)).thenReturn(Optional.of(prefs));

        NotificationTemplate template = new NotificationTemplate();
        template.setContent("Your order is delivered");
        when(templateRepository.findByEventNameAndChannelAndIsActiveTrue(com.fooddelivery.common.constants.NotificationTemplate.ORDER_DELIVERED.name(), ChannelType.EMAIL))
                .thenReturn(Optional.of(template));

        when(emailStrategy.dispatch(any(), any())).thenReturn("email-id-123");

        assertDoesNotThrow(() -> notificationRouterService.routeAndDispatch(event));
        verify(auditLogRepository, times(1)).save(any());
    }
}
