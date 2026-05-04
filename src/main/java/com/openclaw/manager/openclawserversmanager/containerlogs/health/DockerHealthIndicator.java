package com.openclaw.manager.openclawserversmanager.containerlogs.health;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InfoCmd;
import com.github.dockerjava.api.model.Info;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("docker")
public class DockerHealthIndicator implements HealthIndicator {

    private final DockerClient dockerClient;

    public DockerHealthIndicator(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public Health health() {
        try {
            dockerClient.pingCmd().exec();
            try {
                InfoCmd cmd = dockerClient.infoCmd();
                Info info = cmd.exec();
                return Health.up()
                        .withDetail("serverVersion", info.getServerVersion())
                        .withDetail("containers", info.getContainers())
                        .build();
            } catch (Exception ignored) {
                return Health.up().build();
            }
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
