package com.planner.route_planner_api.common.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    private final TopicAccess topicAccess;

    public StompAuthInterceptor(JwtDecoder jwtDecoder, TopicAccess topicAccess) {
        this.jwtDecoder = jwtDecoder;
        this.topicAccess = topicAccess;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;                                   // heartbeats have no command
        }
        switch (stomp.getCommand()) {
            case CONNECT, STOMP -> stomp.setUser(authenticate(stomp.getFirstNativeHeader("Authorization")));
            case DISCONNECT -> { }
            case SUBSCRIBE -> {
                Long userId = requireUserId(stomp);
                if (!topicAccess.canSubscribeRoute(userId, stomp.getDestination())) {
                    throw new AccessDeniedException("You can't subscribe to that destination");
                }
                
            }
            case SEND -> {
                requireUserId(stomp);
                String destination = stomp.getDestination();
                if (destination == null || !destination.startsWith("/app/")) {
                    throw new AccessDeniedException("Clients can only send to /app");   // only the server publishes to /topic
                }
            }

            default -> requireUserId(stomp);
        }
        return message;
    }

    private Authentication authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("Missing token");
        }
        try {
            return converter.convert(jwtDecoder.decode(header.substring(7)));
        } catch (JwtException ex) {
            throw new AccessDeniedException("Invalid or expired token");   // don't send back why decoding failed
        }
    }

    private Long requireUserId(StompHeaderAccessor stomp) {
        if (stomp.getUser() == null) throw new AccessDeniedException("Connect with a valid token first.");
        return Long.valueOf(stomp.getUser().getName());
    }
}
