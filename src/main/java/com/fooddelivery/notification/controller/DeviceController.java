package com.fooddelivery.notification.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.notification.domain.UserDevice;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications/devices")
@lombok.extern.slf4j.Slf4j
public class DeviceController {
    @java.lang.SuppressWarnings("all")

    private final UserDeviceRepository userDeviceRepository;


    public static class DeviceRegistrationRequest {
        @NotBlank
        private String fcmToken;
        @NotBlank
        private String platform;

        @java.lang.SuppressWarnings("all")
        public DeviceRegistrationRequest() {
        }

        @java.lang.SuppressWarnings("all")
        public String getFcmToken() {
            return this.fcmToken;
        }

        @java.lang.SuppressWarnings("all")
        public String getPlatform() {
            return this.platform;
        }

        @java.lang.SuppressWarnings("all")
        public void setFcmToken(final String fcmToken) {
            this.fcmToken = fcmToken;
        }

        @java.lang.SuppressWarnings("all")
        public void setPlatform(final String platform) {
            this.platform = platform;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public boolean equals(final java.lang.Object o) {
            if (o == this) return true;
            if (!(o instanceof DeviceController.DeviceRegistrationRequest)) return false;
            final DeviceController.DeviceRegistrationRequest other = (DeviceController.DeviceRegistrationRequest) o;
            if (!other.canEqual((java.lang.Object) this)) return false;
            final java.lang.Object this$fcmToken = this.getFcmToken();
            final java.lang.Object other$fcmToken = other.getFcmToken();
            if (this$fcmToken == null ? other$fcmToken != null : !this$fcmToken.equals(other$fcmToken)) return false;
            final java.lang.Object this$platform = this.getPlatform();
            final java.lang.Object other$platform = other.getPlatform();
            if (this$platform == null ? other$platform != null : !this$platform.equals(other$platform)) return false;
            return true;
        }

        @java.lang.SuppressWarnings("all")
        protected boolean canEqual(final java.lang.Object other) {
            return other instanceof DeviceController.DeviceRegistrationRequest;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final java.lang.Object $fcmToken = this.getFcmToken();
            result = result * PRIME + ($fcmToken == null ? 43 : $fcmToken.hashCode());
            final java.lang.Object $platform = this.getPlatform();
            result = result * PRIME + ($platform == null ? 43 : $platform.hashCode());
            return result;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
            return "DeviceController.DeviceRegistrationRequest(fcmToken=" + this.getFcmToken() + ", platform=" + this.getPlatform() + ")";
        }
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> registerDevice(java.security.Principal principal, @Valid @RequestBody DeviceRegistrationRequest request) {
        UUID userId = UUID.fromString(principal.getName());
        Optional<UserDevice> existing = userDeviceRepository.findByFcmToken(request.getFcmToken());
        if (existing.isPresent()) {
            UserDevice device = existing.get();
            device.setUserId(userId);
            device.setIsActive(true);
            device.setPlatform(request.getPlatform());
            device.setLastUpdatedAt(OffsetDateTime.now());
            userDeviceRepository.save(device);
        } else {
            UserDevice device = new UserDevice();
            device.setUserId(userId);
            device.setFcmToken(request.getFcmToken());
            device.setPlatform(request.getPlatform());
            device.setIsActive(true);
            device.setLastUpdatedAt(OffsetDateTime.now());
            userDeviceRepository.save(device);
        }
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Device registered successfully").build());
    }

    @DeleteMapping("/{fcmToken}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> unregisterDevice(java.security.Principal principal, @PathVariable String fcmToken) {
        UUID userId = UUID.fromString(principal.getName());
        Optional<UserDevice> existing = userDeviceRepository.findByFcmToken(fcmToken);
        if (existing.isPresent() && existing.get().getUserId().equals(userId)) {
            UserDevice device = existing.get();
            device.setIsActive(false);
            device.setLastUpdatedAt(OffsetDateTime.now());
            userDeviceRepository.save(device);
        }
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message("Device unregistered successfully").build());
    }

    @java.lang.SuppressWarnings("all")
    public DeviceController(final UserDeviceRepository userDeviceRepository) {
        this.userDeviceRepository = userDeviceRepository;
    }
}
