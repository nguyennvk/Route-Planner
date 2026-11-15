package com.planner.route_planner_api.feature.feedback;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.planner.route_planner_api.common.error.BadRequestException;
import com.planner.route_planner_api.feature.feedback.dto.FeedbackRequest;

// Anyone can send feedback without logging in, so it's guarded against bots and floods before it reaches
// the inbox: a honeypot field, and a few messages per address per 10 minutes
@Service
public class FeedbackService {
    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    private static final int LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final FeedbackRepository feedbackRepository;
    private final FeedbackMailer mailer;
    // In memory: resets on restart and isn't shared between servers, which is enough to stop a flood
    private final Map<String, Deque<Instant>> recentBySender = new ConcurrentHashMap<>();

    public FeedbackService(FeedbackRepository feedbackRepository, FeedbackMailer mailer) {
        this.feedbackRepository = feedbackRepository;
        this.mailer = mailer;
    }

    // Saved first, then emailed: a mail problem never loses feedback or fails the request
    public void submit(FeedbackRequest request, String senderAddress) {
        if (request.website() != null && !request.website().isBlank()) {
            log.info("Dropped feedback with the honeypot field filled in");
            return;                                       // looks accepted, so the bot doesn't adapt
        }
        requireUnderLimit(senderAddress);

        Feedback feedback = feedbackRepository.save(new Feedback(
            trimToNull(request.name()), trimToNull(request.email()), request.message().trim(),
            request.priceBand(), request.priceAmount()));
        if (mailer.send(feedback)) {
            feedback.markEmailed();
            feedbackRepository.save(feedback);
        }
    }

    private void requireUnderLimit(String senderAddress) {
        Instant now = Instant.now();
        Deque<Instant> recent = recentBySender.computeIfAbsent(senderAddress, key -> new ArrayDeque<>());
        synchronized (recent) {
            while (!recent.isEmpty() && recent.peekFirst().isBefore(now.minus(WINDOW))) {
                recent.pollFirst();
            }
            if (recent.size() >= LIMIT) {
                throw new BadRequestException("Thanks, we already have your messages. Please try again in a few minutes.");
            }
            recent.addLast(now);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
