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
     * Replaces {@code {1}..{n}} with {@code params} for a plain-text channel (SMS, push): values are inserted as given.
     */
    default String hydrateTemplate(String templateContent, java.util.List<String> params) {
        return hydrateTemplate(templateContent, params, java.util.function.UnaryOperator.identity());
    }

    /**
     * Replaces {@code {1}..{n}} with {@code params}, each passed through {@code escape} first (an HTML email escapes
     * them). One pass over the template: a value is never scanned again, so a parameter that itself contains "{2}"
     * is sent as written, not replaced by the second parameter. A placeholder with no parameter stays as written.
     */
    default String hydrateTemplate(String templateContent, java.util.List<String> params,
                                   java.util.function.UnaryOperator<String> escape) {
        if (params == null || params.isEmpty()) {
            return templateContent;
        }
        return PLACEHOLDER.matcher(templateContent).replaceAll(match -> {
            int index = Integer.parseInt(match.group(1)) - 1;
            return index >= 0 && index < params.size()
                    ? java.util.regex.Matcher.quoteReplacement(escape.apply(params.get(index)))
                    : java.util.regex.Matcher.quoteReplacement(match.group());
        });
    }

    java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("\\{(\\d+)\\}");
}
