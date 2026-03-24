package com.openclaw.manager.openclawserversmanager.auth.config;

import com.openclaw.manager.openclawserversmanager.auth.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    @Value("${springdoc.swagger-ui.enabled:true}")
    private boolean swaggerEnabled;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .headers(headers -> {
                    headers.frameOptions(frame -> frame.deny());
                    headers.contentTypeOptions(contentType -> {});
                    headers.httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000));
                    headers.referrerPolicy(referrer -> referrer
                            .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.permissionsPolicy(permissions -> permissions
                            .policy("camera=(), microphone=(), geolocation=()"));
                    headers.cacheControl(cache -> {});
                })
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                        // Public auth endpoints
                        auth.requestMatchers("/api/v1/auth/**").permitAll();
                        // Swagger UI (only when enabled)
                        if (swaggerEnabled) {
                            auth.requestMatchers(
                                    "/swagger-ui/**",
                                    "/swagger-ui.html",
                                    "/v3/api-docs/**",
                                    "/v3/api-docs"
                            ).permitAll();
                        }
                        // Dev admin pages (static resources)
                        auth.requestMatchers("/dev/**").permitAll();
                        // WebSocket (auth handled by handshake interceptor)
                        auth.requestMatchers("/ws/**").permitAll();
                        // ADMIN only
                        auth.requestMatchers("/api/v1/users/**").hasAuthority("ROLE_ADMIN");
                        auth.requestMatchers("/api/v1/audit/**").hasAuthority("ROLE_ADMIN");
                        // Deployment scripts: create/update/delete ADMIN only
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/deployment-scripts").hasAuthority("ROLE_ADMIN");
                        auth.requestMatchers(HttpMethod.PATCH, "/api/v1/deployment-scripts/**").hasAuthority("ROLE_ADMIN");
                        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/deployment-scripts/**").hasAuthority("ROLE_ADMIN");
                        // Agent templates: create/update/delete ADMIN only
                        auth.requestMatchers(HttpMethod.POST, "/api/v1/agent-templates").hasAuthority("ROLE_ADMIN");
                        auth.requestMatchers(HttpMethod.PATCH, "/api/v1/agent-templates/**").hasAuthority("ROLE_ADMIN");
                        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/agent-templates/**").hasAuthority("ROLE_ADMIN");
                        // DELETE is ADMIN only for secrets, servers, and domain resources
                        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/secrets/**").hasAuthority("ROLE_ADMIN");
                        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/servers/**").hasAuthority("ROLE_ADMIN");
                        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/provider-accounts/**").hasAuthority("ROLE_ADMIN");
                        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/zones/**").hasAuthority("ROLE_ADMIN");
                        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/domain-assignments/**").hasAuthority("ROLE_ADMIN");
                        auth.requestMatchers(HttpMethod.DELETE, "/api/v1/ssl-certificates/**").hasAuthority("ROLE_ADMIN");
                        // Everything else requires authentication
                        auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
