package com.openclaw.manager.openclawserversmanager.domains.provider;

import com.openclaw.manager.openclawserversmanager.domains.dto.ValidateCredentialsResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.VerifyZoneResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.DnsProviderType;

import java.util.List;
import java.util.Map;

public interface DnsProviderAdapter {

    DnsProviderType getProviderType();

    ProviderCapabilities getCapabilities();

    ValidateCredentialsResponse validateCredentials(String decryptedToken, Map<String, Object> settings);

    VerifyZoneResponse verifyZoneManageable(String zoneName, String decryptedToken, Map<String, Object> settings);

    String resolveZoneId(String zoneName, String decryptedToken, Map<String, Object> settings);

    DnsRecord createOrUpsertRecord(String zoneName, String providerZoneId, DnsRecord record,
                                    String decryptedToken, Map<String, Object> settings);

    void deleteRecord(String providerZoneId, String providerRecordId,
                      String decryptedToken, Map<String, Object> settings);

    List<DnsRecord> listRecords(String providerZoneId, String decryptedToken, Map<String, Object> settings);

    List<DiscoveredDomain> listDomains(String decryptedToken, Map<String, Object> settings);
}
