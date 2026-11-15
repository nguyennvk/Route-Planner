package com.planner.route_planner_api.feature.feedback;

import org.springframework.boot.context.properties.ConfigurationProperties;

// feedback.mail.* in application.properties, filled from .env. Empty "to" or "host" turns emailing off:
// feedback is still saved, and you can read it from the feedback table
@ConfigurationProperties(prefix = "feedback.mail")
public record FeedbackMailProperties(
    String to,          // where feedback goes: your inbox
    String from,        // the sender; for Gmail, the same account as username
    String host,        // e.g. smtp.gmail.com
    int port,           // 587 with STARTTLS
    String username,
    String password     // for Gmail, an app password, not the account password
) {
    public boolean configured() {
        return notBlank(to) && notBlank(host) && notBlank(username) && notBlank(password);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
