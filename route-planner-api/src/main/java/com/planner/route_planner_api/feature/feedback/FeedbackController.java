package com.planner.route_planner_api.feature.feedback;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.feedback.dto.FeedbackRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

// Public: the landing page's feedback form works without an account (SecurityConfig permits this POST)
@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void submit(@Valid @RequestBody FeedbackRequest request, HttpServletRequest http) {
        feedbackService.submit(request, http.getRemoteAddr());
    }
}
