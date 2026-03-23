package com.openclaw.manager.openclawserversmanager.domains.repository;

import com.openclaw.manager.openclawserversmanager.domains.entity.DnsProviderType;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProviderAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProviderAccountRepository extends JpaRepository<ProviderAccount, UUID> {

    boolean existsByDisplayName(String displayName);

    List<ProviderAccount> findByProviderType(DnsProviderType providerType);

    List<ProviderAccount> findByEnabled(boolean enabled);
}
