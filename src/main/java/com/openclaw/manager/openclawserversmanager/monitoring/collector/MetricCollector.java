package com.openclaw.manager.openclawserversmanager.monitoring.collector;

import com.openclaw.manager.openclawserversmanager.servers.entity.Server;

public interface MetricCollector {

    CollectionResult collect(Server server);

    String getCollectorType();
}
