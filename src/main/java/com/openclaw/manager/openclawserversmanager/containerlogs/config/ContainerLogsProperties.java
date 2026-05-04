package com.openclaw.manager.openclawserversmanager.containerlogs.config;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

@ConfigurationProperties(prefix = "container-logs")
public class ContainerLogsProperties {

    private String dockerHost = "unix:///var/run/docker.sock";
    private final Containers containers = new Containers();
    private int queueCapacity = 5000;
    private final Batch batch = new Batch();
    private int maxLineLength = 8192;
    private int replayOnConnect = 200;
    private final Ws ws = new Ws();
    private int ticketExpirySeconds = 60;
    private final Retention retention = new Retention();

    public String getDockerHost() {
        return dockerHost;
    }

    public void setDockerHost(String dockerHost) {
        this.dockerHost = dockerHost;
    }

    public Containers getContainers() {
        return containers;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public Batch getBatch() {
        return batch;
    }

    public int getMaxLineLength() {
        return maxLineLength;
    }

    public void setMaxLineLength(int maxLineLength) {
        this.maxLineLength = maxLineLength;
    }

    public int getReplayOnConnect() {
        return replayOnConnect;
    }

    public void setReplayOnConnect(int replayOnConnect) {
        this.replayOnConnect = replayOnConnect;
    }

    public Ws getWs() {
        return ws;
    }

    public int getTicketExpirySeconds() {
        return ticketExpirySeconds;
    }

    public void setTicketExpirySeconds(int ticketExpirySeconds) {
        this.ticketExpirySeconds = ticketExpirySeconds;
    }

    public Retention getRetention() {
        return retention;
    }

    /**
     * Resolves the configured container name for the given service.
     */
    public String containerNameFor(ContainerService service) {
        return switch (service) {
            case BACKEND -> containers.getBackend();
            case FRONTEND -> containers.getFrontend();
            case NGINX -> containers.getNginx();
            case POSTGRES -> containers.getPostgres();
        };
    }

    public Map<ContainerService, String> containerNames() {
        Map<ContainerService, String> map = new EnumMap<>(ContainerService.class);
        for (ContainerService s : ContainerService.values()) {
            map.put(s, containerNameFor(s));
        }
        return map;
    }

    public static class Containers {
        private String backend = "claw-ops";
        private String frontend = "clawops-fe";
        private String nginx = "clawops-nginx";
        private String postgres = "claw-ops-postgres";

        public String getBackend() { return backend; }
        public void setBackend(String backend) { this.backend = backend; }
        public String getFrontend() { return frontend; }
        public void setFrontend(String frontend) { this.frontend = frontend; }
        public String getNginx() { return nginx; }
        public void setNginx(String nginx) { this.nginx = nginx; }
        public String getPostgres() { return postgres; }
        public void setPostgres(String postgres) { this.postgres = postgres; }
    }

    public static class Batch {
        private int size = 200;
        private long flushIntervalMs = 500;

        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        public long getFlushIntervalMs() { return flushIntervalMs; }
        public void setFlushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }
    }

    public static class Ws {
        private int maxSubscribersPerService = 50;
        private int slowConsumerBuffer = 1000;

        public int getMaxSubscribersPerService() { return maxSubscribersPerService; }
        public void setMaxSubscribersPerService(int maxSubscribersPerService) { this.maxSubscribersPerService = maxSubscribersPerService; }
        public int getSlowConsumerBuffer() { return slowConsumerBuffer; }
        public void setSlowConsumerBuffer(int slowConsumerBuffer) { this.slowConsumerBuffer = slowConsumerBuffer; }
    }

    public static class Retention {
        private String cron = "0 30 3 * * *";

        public String getCron() { return cron; }
        public void setCron(String cron) { this.cron = cron; }
    }
}
