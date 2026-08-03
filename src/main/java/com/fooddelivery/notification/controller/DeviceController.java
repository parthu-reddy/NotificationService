package com.fooddelivery.notification.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.notification.domain.UserDevice;
import com.fooddelivery.notification.repository.UserDeviceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class DeviceController {

    private final UserDeviceRepository userDeviceRepository;

    @Data
    public static class DeviceRegistrationRequest {
        @NotBlank
        private String fcmToken;
        @NotBlank
        private String platform;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> registerDevice(
            java.security.Principal principal,
            @Valid @RequestBody DeviceRegistrationRequest request) {
            
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
    public ResponseEntity<ApiResponse<Void>> unregisterDevice(
            java.security.Principal principal,
            @PathVariable String fcmToken) {
            
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
}
