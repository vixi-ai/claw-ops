package com.openclaw.manager.openclawserversmanager.ssh.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ssh")
public class SshConfig {

    private int connectionTimeout = 10000;
    private int commandTimeout = 60;
    private boolean strictHostKeyChecking = false;
    private int maxOutputSize = 1048576;

    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }

    public int getCommandTimeout() { return commandTimeout; }
    public void setCommandTimeout(int commandTimeout) { this.commandTimeout = commandTimeout; }

    public boolean isStrictHostKeyChecking() { return strictHostKeyChecking; }
    public void setStrictHostKeyChecking(boolean strictHostKeyChecking) { this.strictHostKeyChecking = strictHostKeyChecking; }

    public int getMaxOutputSize() { return maxOutputSize; }
    public void setMaxOutputSize(int maxOutputSize) { this.maxOutputSize = maxOutputSize; }
}
