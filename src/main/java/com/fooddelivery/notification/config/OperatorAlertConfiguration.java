package com.fooddelivery.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link OperatorAlertProperties}. Not profile-gated: operator alerts are real in every profile. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OperatorAlertProperties.class)
public class OperatorAlertConfiguration {
}
