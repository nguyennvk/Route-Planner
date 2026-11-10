package com.planner.route_planner_api.common.websocket;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.companies.CompanyMember;
import com.planner.route_planner_api.feature.companies.CompanyMemberId;
import com.planner.route_planner_api.feature.companies.CompanyMemberRepository;
import com.planner.route_planner_api.feature.companies.CompanyRepository;
import com.planner.route_planner_api.feature.companies.MemberRole;
import com.planner.route_planner_api.feature.route.Route;
import com.planner.route_planner_api.feature.route.RouteRepository;

@Component
public class TopicAccess {

    private static final Pattern ROUTE_POSITION = Pattern.compile("/topic/routes/(\\d{1,18})/position");

    private final RouteRepository routeRepository;
    private final CompanyAccess companyAccess;

    public TopicAccess(RouteRepository routeRepository, CompanyAccess companyAccess) {
        this.routeRepository = routeRepository;
        this.companyAccess = companyAccess;
    }

    // Runs on the WebSocket thread: no open-in-view, so the transaction is what lets plan/company load
    @Transactional(readOnly = true)
    public boolean canSubscribeRoute(Long userId, String destination) {
        if (destination == null) return false;
        Matcher route = ROUTE_POSITION.matcher(destination);
        if (route.matches()) {                                   // whole string, so no "/**" suffix tricks
            return canWatchRoute(userId, Long.valueOf(route.group(1)));
        }
        return false;
    }

    private boolean canWatchRoute(Long userId, Long routeId) {
        return routeRepository.findById(routeId)
            .map(route -> companyAccess.findActiveMember(route.getPlan().getCompany().getId(), userId)
                .map(member -> member.getRole() == MemberRole.owner || member.getRole() == MemberRole.dispatcher)
                .orElse(false))
            .orElse(false);
    }
}
