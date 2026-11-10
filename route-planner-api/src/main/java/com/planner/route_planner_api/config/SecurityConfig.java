package com.planner.route_planner_api.config;

import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.planner.route_planner_api.common.websocket.FrontendProperties;
import com.planner.route_planner_api.feature.auth.token.JwtProperties;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final FrontendProperties props;

    public SecurityConfig(FrontendProperties props) {
        this.props = props;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http){
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, 
                    "/api/v1/auth/register", 
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh", 
                    "/api/v1/auth/logout").permitAll()
                .requestMatchers("/error", "/actuator/health").permitAll()
                // Allow for websocket
                .requestMatchers("/ws").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/shared/**").permitAll()   // share links: the token is the permission
                // Only the driver's actions on their route; any other POST under /shared still needs a login
                .requestMatchers(HttpMethod.POST,
                    "/api/v1/shared/routes/*/accept",
                    "/api/v1/shared/routes/*/reject",
                    "/api/v1/shared/routes/*/start",
                    "/api/v1/shared/routes/*/finish").permitAll()
                .anyRequest().authenticated()
            ).oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Define your allowed origins
        configuration.setAllowedOrigins(List.of("http://localhost:4200", props.baseUrl()));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply to all endpoints
        return source;
    }

    @Bean 
    PasswordEncoder passwordEncoder(){
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecretKey jwtSecretKey(JwtProperties props) {
        return new SecretKeySpec(Base64.getDecoder().decode(props.secret()), "HmacSHA256");
    }

    // SIGNS tokens at login/refresh
    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    // VERIFIES tokens on every request: signature, expiry, and issuer
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey, JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
        return decoder;
    }


}
