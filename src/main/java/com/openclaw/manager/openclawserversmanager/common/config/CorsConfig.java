package com.openclaw.manager.openclawserversmanager.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOriginsList());
        config.setAllowedMethods(corsProperties.getAllowedMethodsList());
        config.setAllowedHeaders(corsProperties.getAllowedHeadersList());
        config.setExposedHeaders(corsProperties.getExposeHeadersList());
        config.setAllowCredentials(corsProperties.isAllowCredentials());
        config.setMaxAge(corsProperties.getMaxAge());

        if (corsProperties.isAllowCredentials()
                && corsProperties.getAllowedOriginsList().contains("*")) {
            log.warn("CORS: allowCredentials=true with allowedOrigins=* is invalid per spec. "
                    + "Browsers will reject this. Use specific origins or set CORS_ALLOW_CREDENTIALS=false.");
        }

        log.info("CORS configured — allowed origins: {}", corsProperties.getAllowedOrigins());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
