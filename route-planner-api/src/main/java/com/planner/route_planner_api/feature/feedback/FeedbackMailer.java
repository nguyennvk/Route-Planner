package com.planner.route_planner_api.feature.feedback;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

// Sends one feedback entry to the owner over SMTP with Jakarta Mail. Plain text only, so nothing a visitor
// types can turn into HTML in the inbox
@Component
public class FeedbackMailer {
    private static final Logger log = LoggerFactory.getLogger(FeedbackMailer.class);

    private final FeedbackMailProperties props;

    public FeedbackMailer(FeedbackMailProperties props) {
        this.props = props;
    }

    // true when it went out. Never throws: a mail problem mustn't lose the feedback or fail the request
    public boolean send(Feedback feedback) {
        if (!props.configured()) {
            log.info("Feedback {} saved; emailing is off (set FEEDBACK_TO and FEEDBACK_SMTP_* to turn it on)", feedback.getId());
            return false;
        }
        try {
            MimeMessage message = new MimeMessage(session());
            message.setFrom(new InternetAddress(props.from() == null || props.from().isBlank() ? props.username() : props.from()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(props.to()));
            // Reply goes straight to the person, when they left an email
            if (feedback.getEmail() != null && !feedback.getEmail().isBlank()) {
                message.setReplyTo(InternetAddress.parse(feedback.getEmail()));
            }
            message.setSubject("Route Scheduler feedback #" + feedback.getId() + ": " + feedback.getPriceBand().label(), "UTF-8");
            message.setText(body(feedback), "UTF-8");
            Transport.send(message);
            return true;
        } catch (MessagingException ex) {
            log.warn("Couldn't email feedback {}; it's saved in the feedback table", feedback.getId(), ex);
            return false;
        }
    }

    private Session session() {
        Properties smtp = new Properties();
        smtp.put("mail.smtp.host", props.host());
        smtp.put("mail.smtp.port", String.valueOf(props.port()));
        smtp.put("mail.smtp.auth", "true");
        smtp.put("mail.smtp.starttls.enable", "true");
        smtp.put("mail.smtp.starttls.required", "true");
        smtp.put("mail.smtp.connectiontimeout", "10000");
        smtp.put("mail.smtp.timeout", "10000");
        return Session.getInstance(smtp, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(props.username(), props.password());
            }
        });
    }

    private static String body(Feedback feedback) {
        String amount = feedback.getPriceAmount() == null ? "not given" : "$" + feedback.getPriceAmount().toPlainString() + " a month";
        return """
            New feedback from the Route Scheduler website.

            Name: %s
            Email: %s
            Would pay: %s
            Exact amount: %s
            Sent: %s UTC

            Message:
            %s
            """.formatted(
                orDash(feedback.getName()),
                orDash(feedback.getEmail()),
                feedback.getPriceBand().label(),
                amount,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(feedback.getCreatedAt().atOffset(ZoneOffset.UTC)),
                feedback.getMessage());
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
