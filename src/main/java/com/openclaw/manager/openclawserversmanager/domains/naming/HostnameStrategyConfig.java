package com.openclaw.manager.openclawserversmanager.domains.naming;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HostnameStrategyConfig {

    @Bean
    public HostnameStrategy hostnameStrategy() {
        return new SlugBasedHostnameStrategy();
    }
}
