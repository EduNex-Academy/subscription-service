package com.edu.subscription_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Completely disable CORS - API Gateway handles all CORS
            .cors(AbstractHttpConfigurer::disable)
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers(
                    "/api/v1/subscription-plans/**",
                    "/api/v1/webhooks/**",
                    "/actuator/health",
                    "/actuator/info",
                    // Swagger endpoints
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // Protected endpoints
                .requestMatchers("/api/v1/subscriptions/**").hasAnyRole("STUDENT", "INSTRUCTOR", "ADMIN")
                .requestMatchers("/api/v1/points/**").hasAnyRole("STUDENT", "INSTRUCTOR", "ADMIN")
                // Admin only endpoints
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            // Try to get roles from realm_access first (Keycloak format)
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            Collection<String> roles = null;
            
            if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?>) {
                @SuppressWarnings("unchecked")
                Collection<String> realmRoles = (Collection<String>) realmAccess.get("roles");
                roles = realmRoles;
            }
            
            // Fallback to direct roles claim
            if (roles == null) {
                roles = jwt.getClaimAsStringList("roles");
            }
            
            // Final fallback to authorities claim
            if (roles == null) {
                roles = jwt.getClaimAsStringList("authorities");
            }
            
            if (roles == null) {
                return List.of();
            }
            
            return roles.stream()
                    .filter(role -> role.startsWith("ROLE_") ||
                           role.equals("STUDENT") ||
                           role.equals("INSTRUCTOR") ||
                           role.equals("ADMIN"))
                    .map(role -> role.startsWith("ROLE_") ?
                         new SimpleGrantedAuthority(role) :
                         new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());
        });
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Simple decoder that trusts pre-validated JWTs from API Gateway
        // The API Gateway has already validated the JWT signature and expiration
        return token -> {
            try {
                // Parse the JWT without signature validation since API Gateway already validated it
                String[] chunks = token.split("\\.");
                if (chunks.length != 3) {
                    throw new IllegalArgumentException("Invalid JWT format");
                }
                
                // Decode payload
                byte[] payloadBytes = Base64.getUrlDecoder().decode(chunks[1]);
                String payloadJson = new String(payloadBytes);
                
                // Parse claims using Jackson
                @SuppressWarnings("unchecked")
                Map<String, Object> claims = objectMapper.readValue(payloadJson, Map.class);
                
                // Convert numeric timestamps to Instant objects
                convertTimestampClaims(claims);
                
                // Create JWT object with parsed claims
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .header("typ", "JWT")
                        .claims(claimsMap -> claimsMap.putAll(claims))
                        .build();
                        
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to decode JWT: " + e.getMessage(), e);
            }
        };
    }
    
    private void convertTimestampClaims(Map<String, Object> claims) {
        // Convert common timestamp claims from seconds to Instant
        String[] timestampClaims = {"exp", "iat", "nbf"};
        
        for (String claimName : timestampClaims) {
            Object value = claims.get(claimName);
            if (value instanceof Number) {
                long timestamp = ((Number) value).longValue();
                claims.put(claimName, Instant.ofEpochSecond(timestamp));
            }
        }
        
        // Ensure required claims exist with default values if missing
        if (!claims.containsKey("exp")) {
            claims.put("exp", Instant.now().plusSeconds(3600)); // Default 1 hour
        }
        if (!claims.containsKey("iat")) {
            claims.put("iat", Instant.now());
        }
        if (!claims.containsKey("sub")) {
            claims.put("sub", "unknown");
        }
    }
}
