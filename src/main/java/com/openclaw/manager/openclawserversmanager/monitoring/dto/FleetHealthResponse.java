package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import java.util.List;

public record FleetHealthResponse(
    int totalServers,
    int healthy,
    int warning,
    int critical,
    int unreachable,
    int unknown,
    int maintenance,
    List<ServerHealthSummary> servers
) {}
