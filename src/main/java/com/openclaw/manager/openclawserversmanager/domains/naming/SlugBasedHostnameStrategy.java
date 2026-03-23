package com.openclaw.manager.openclawserversmanager.domains.naming;

public class SlugBasedHostnameStrategy implements HostnameStrategy {

    @Override
    public String generateServerHostname(String serverName, String zoneName) {
        return slugify(serverName) + "." + zoneName;
    }

    @Override
    public String generateAgentHostname(String agentName, String serverName, String zoneName) {
        return slugify(agentName) + "." + slugify(serverName) + "." + zoneName;
    }

    private String slugify(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Cannot slugify blank input");
        }
        return input.toLowerCase()
                .replaceAll("[\\s_]+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }
}
