package com.openclaw.manager.openclawserversmanager.deployment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "deploymentExecutor")
    public ThreadPoolTaskExecutor deploymentExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("deploy-");
        exec.initialize();
        return exec;
    }

    @Bean(name = "provisioningExecutor")
    public ThreadPoolTaskExecutor provisioningExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(5);
        exec.setQueueCapacity(20);
        exec.setThreadNamePrefix("ssl-provision-");
        exec.initialize();
        return exec;
    }

    /**
     * Isolated pool for async domain-assignment jobs. Kept separate from
     * {@link #provisioningExecutor()} so that slow DNS propagation checks can't starve
     * SSL provisioning (and vice versa).
     */
    @Bean(name = "domainAssignmentExecutor")
    public ThreadPoolTaskExecutor domainAssignmentExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(5);
        exec.setQueueCapacity(20);
        exec.setThreadNamePrefix("dns-assign-");
        exec.initialize();
        return exec;
    }
}
