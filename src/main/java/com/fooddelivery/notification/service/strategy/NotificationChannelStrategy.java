package com.fooddelivery.notification.service.strategy;

import com.fooddelivery.common.enums.ChannelType;
import com.fooddelivery.common.event.NotificationRequestEvent;
import com.fooddelivery.notification.domain.NotificationTemplate;

public interface NotificationChannelStrategy {
    
    /**
     * @return The channel type this strategy supports.
     */
    ChannelType getSupportedChannel();

    /**
     * Dispatches the notification using the appropriate channel logic.
     *
     * @param event The notification request event.
     * @param template The template for the notification.
     * @return The provider message ID if successful.
     * @throws Exception If dispatch fails.
     */
    String dispatch(NotificationRequestEvent event, NotificationTemplate template) throws Exception;

    /**
     * Replaces placeholders in the template with actual parameters.
     */
    default String hydrateTemplate(String templateContent, java.util.List<String> params) {
        if (params == null || params.isEmpty()) {
            return templateContent;
        }
        String hydrated = templateContent;
        for (int i = 0; i < params.size(); i++) {
            hydrated = hydrated.replace("{" + (i + 1) + "}", params.get(i));
        }
        return hydrated;
    }
}
