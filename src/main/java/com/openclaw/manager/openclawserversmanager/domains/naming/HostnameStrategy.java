package com.openclaw.manager.openclawserversmanager.domains.naming;

public interface HostnameStrategy {

    String generateServerHostname(String serverName, String zoneName);

    String generateAgentHostname(String agentName, String serverName, String zoneName);
}
