package com.openclaw.manager.openclawserversmanager.domains.provider;

import com.openclaw.manager.openclawserversmanager.domains.entity.DnsProviderType;
import com.openclaw.manager.openclawserversmanager.domains.exception.DomainException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProviderAdapterFactory {

    private final Map<DnsProviderType, DnsProviderAdapter> adapters;

    public ProviderAdapterFactory(List<DnsProviderAdapter> adapterList) {
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(DnsProviderAdapter::getProviderType, Function.identity()));
    }

    public DnsProviderAdapter getAdapter(DnsProviderType providerType) {
        DnsProviderAdapter adapter = adapters.get(providerType);
        if (adapter == null) {
            throw new DomainException("No adapter registered for provider type: " + providerType);
        }
        return adapter;
    }
}
