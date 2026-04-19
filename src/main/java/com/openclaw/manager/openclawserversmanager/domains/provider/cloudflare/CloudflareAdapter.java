package com.openclaw.manager.openclawserversmanager.domains.provider.cloudflare;

import com.openclaw.manager.openclawserversmanager.domains.dto.ValidateCredentialsResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.VerifyZoneResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.DnsProviderType;
import com.openclaw.manager.openclawserversmanager.domains.entity.DnsRecordType;
import com.openclaw.manager.openclawserversmanager.domains.exception.DnsProviderException;
import com.openclaw.manager.openclawserversmanager.domains.provider.DiscoveredDomain;
import com.openclaw.manager.openclawserversmanager.domains.provider.DnsProviderAdapter;
import com.openclaw.manager.openclawserversmanager.domains.provider.DnsRecord;
import com.openclaw.manager.openclawserversmanager.domains.provider.ProviderCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CloudflareAdapter implements DnsProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(CloudflareAdapter.class);
    private static final String BASE_URL = "https://api.cloudflare.com/client/v4";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final RestClient restClient;

    public CloudflareAdapter() {
        // Explicit timeouts prevent a slow/unresponsive provider from wedging the caller.
        // The runner tolerates timeouts via its retry policy.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Content-Type", "application/json")
                .requestFactory(factory)
                .build();
    }

    @Override
    public DnsProviderType getProviderType() {
        return DnsProviderType.CLOUDFLARE;
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        return new ProviderCapabilities(
                true,
                true,
                true,
                1200,
                List.of(DnsRecordType.A, DnsRecordType.AAAA, DnsRecordType.CNAME,
                        DnsRecordType.TXT, DnsRecordType.MX, DnsRecordType.NS)
        );
    }

    @Override
    public ValidateCredentialsResponse validateCredentials(String decryptedToken, Map<String, Object> settings) {
        try {
            var response = restClient.get()
                    .uri("/user/tokens/verify")
                    .header("Authorization", "Bearer " + decryptedToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<CloudflareModels.CloudflareResponse<CloudflareModels.TokenVerifyResult>>() {});

            if (response != null && response.success() && response.result() != null) {
                String status = response.result().status();
                if ("active".equals(status)) {
                    return new ValidateCredentialsResponse(true, "Token is valid and active");
                }
                return new ValidateCredentialsResponse(false, "Token status: " + status);
            }

            String errorMsg = extractErrors(response != null ? response.errors() : null);
            return new ValidateCredentialsResponse(false, "Validation failed: " + errorMsg);
        } catch (RestClientException e) {
            log.debug("Cloudflare credential validation failed: {}", e.getMessage());
            return new ValidateCredentialsResponse(false, "Connection failed: " + e.getMessage());
        }
    }

    @Override
    public VerifyZoneResponse verifyZoneManageable(String zoneName, String decryptedToken, Map<String, Object> settings) {
        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/zones").queryParam("name", zoneName).build())
                    .header("Authorization", "Bearer " + decryptedToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<CloudflareModels.CloudflareListResponse<CloudflareModels.CloudflareZone>>() {});

            if (response == null || !response.success()) {
                String errorMsg = extractErrors(response != null ? response.errors() : null);
                return new VerifyZoneResponse(false, "API error: " + errorMsg, List.of());
            }

            if (response.result() == null || response.result().isEmpty()) {
                return new VerifyZoneResponse(false,
                        "Zone '%s' not found in this Cloudflare account".formatted(zoneName), List.of());
            }

            CloudflareModels.CloudflareZone zone = response.result().getFirst();
            List<String> warnings = new java.util.ArrayList<>();

            if (!"active".equals(zone.status())) {
                warnings.add("Zone status is '%s' (expected 'active')".formatted(zone.status()));
            }

            return new VerifyZoneResponse(true,
                    "Zone '%s' is manageable (ID: %s)".formatted(zoneName, zone.id()), warnings);
        } catch (RestClientException e) {
            log.debug("Cloudflare zone verification failed for {}: {}", zoneName, e.getMessage());
            return new VerifyZoneResponse(false, "Connection failed: " + e.getMessage(), List.of());
        }
    }

    @Override
    public String resolveZoneId(String zoneName, String decryptedToken, Map<String, Object> settings) {
        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/zones").queryParam("name", zoneName).build())
                    .header("Authorization", "Bearer " + decryptedToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<CloudflareModels.CloudflareListResponse<CloudflareModels.CloudflareZone>>() {});

            if (response != null && response.success() && response.result() != null && !response.result().isEmpty()) {
                return response.result().getFirst().id();
            }

            throw new DnsProviderException(
                    "Could not resolve zone ID for '%s'".formatted(zoneName), null);
        } catch (RestClientException e) {
            throw new DnsProviderException(
                    "Failed to resolve zone ID for '%s': %s".formatted(zoneName, e.getMessage()), null, e);
        }
    }

    @Override
    public DnsRecord createOrUpsertRecord(String zoneName, String providerZoneId, DnsRecord record,
                                           String decryptedToken, Map<String, Object> settings) {
        try {
            // Check if record already exists
            var existing = findExistingRecord(providerZoneId, record.hostname(), record.type().name(), decryptedToken);

            var requestBody = new CloudflareModels.CreateDnsRecordRequest(
                    record.type().name(),
                    record.hostname(),
                    record.value(),
                    record.ttl(),
                    record.proxied()
            );

            CloudflareModels.CloudflareResponse<CloudflareModels.CloudflareDnsRecord> response;

            if (existing != null) {
                // Update existing record
                response = restClient.put()
                        .uri("/zones/{zoneId}/dns_records/{recordId}", providerZoneId, existing.id())
                        .header("Authorization", "Bearer " + decryptedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
            } else {
                // Create new record
                response = restClient.post()
                        .uri("/zones/{zoneId}/dns_records", providerZoneId)
                        .header("Authorization", "Bearer " + decryptedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(new ParameterizedTypeReference<>() {});
            }

            if (response == null || !response.success() || response.result() == null) {
                String errorMsg = extractErrors(response != null ? response.errors() : null);
                throw new DnsProviderException("Failed to create/update DNS record: " + errorMsg, null);
            }

            var result = response.result();
            return new DnsRecord(
                    result.id(),
                    result.name(),
                    DnsRecordType.valueOf(result.type()),
                    result.content(),
                    result.ttl(),
                    result.proxied()
            );
        } catch (DnsProviderException e) {
            throw e;
        } catch (RestClientException e) {
            throw new DnsProviderException(
                    "Cloudflare API error creating record '%s': %s".formatted(record.hostname(), e.getMessage()),
                    null, e);
        }
    }

    @Override
    public void deleteRecord(String providerZoneId, String providerRecordId,
                             String decryptedToken, Map<String, Object> settings) {
        try {
            restClient.delete()
                    .uri("/zones/{zoneId}/dns_records/{recordId}", providerZoneId, providerRecordId)
                    .header("Authorization", "Bearer " + decryptedToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new DnsProviderException(
                    "Failed to delete DNS record '%s': %s".formatted(providerRecordId, e.getMessage()),
                    providerRecordId, e);
        }
    }

    @Override
    public List<DnsRecord> listRecords(String providerZoneId, String decryptedToken, Map<String, Object> settings) {
        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/zones/{zoneId}/dns_records")
                            .queryParam("per_page", 100)
                            .build(providerZoneId))
                    .header("Authorization", "Bearer " + decryptedToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<CloudflareModels.CloudflareListResponse<CloudflareModels.CloudflareDnsRecord>>() {});

            if (response == null || !response.success() || response.result() == null) {
                return Collections.emptyList();
            }

            return response.result().stream()
                    .map(r -> {
                        DnsRecordType type;
                        try {
                            type = DnsRecordType.valueOf(r.type());
                        } catch (IllegalArgumentException e) {
                            return null; // Skip unsupported record types
                        }
                        return new DnsRecord(r.id(), r.name(), type, r.content(), r.ttl(), r.proxied());
                    })
                    .filter(r -> r != null)
                    .toList();
        } catch (RestClientException e) {
            throw new DnsProviderException(
                    "Failed to list DNS records: " + e.getMessage(), null, e);
        }
    }

    @Override
    public List<DiscoveredDomain> listDomains(String decryptedToken, Map<String, Object> settings) {
        try {
            List<DiscoveredDomain> allDomains = new ArrayList<>();
            int page = 1;
            int totalPages = 1;

            do {
                final int currentPage = page;
                var response = restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/zones")
                                .queryParam("page", currentPage)
                                .queryParam("per_page", 50)
                                .build())
                        .header("Authorization", "Bearer " + decryptedToken)
                        .retrieve()
                        .body(new ParameterizedTypeReference<CloudflareModels.CloudflareListResponse<CloudflareModels.CloudflareZone>>() {});

                if (response == null || !response.success() || response.result() == null) {
                    break;
                }

                for (CloudflareModels.CloudflareZone zone : response.result()) {
                    allDomains.add(new DiscoveredDomain(
                            zone.name(),
                            zone.id(),
                            "active".equals(zone.status())
                    ));
                }

                if (response.resultInfo() != null) {
                    totalPages = response.resultInfo().totalPages();
                }
                page++;
            } while (page <= totalPages);

            return allDomains;
        } catch (RestClientException e) {
            throw new DnsProviderException("Failed to list Cloudflare zones: " + e.getMessage(), null, e);
        }
    }

    private CloudflareModels.CloudflareDnsRecord findExistingRecord(String zoneId, String name, String type,
                                                                      String token) {
        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/zones/{zoneId}/dns_records")
                            .queryParam("name", name)
                            .queryParam("type", type)
                            .build(zoneId))
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(new ParameterizedTypeReference<CloudflareModels.CloudflareListResponse<CloudflareModels.CloudflareDnsRecord>>() {});

            if (response != null && response.success() && response.result() != null && !response.result().isEmpty()) {
                return response.result().getFirst();
            }
            return null;
        } catch (RestClientException e) {
            log.debug("Failed to check existing record {}/{}: {}", name, type, e.getMessage());
            return null;
        }
    }

    private String extractErrors(List<CloudflareModels.CloudflareError> errors) {
        if (errors == null || errors.isEmpty()) return "unknown error";
        return errors.stream()
                .map(e -> "[%d] %s".formatted(e.code(), e.message()))
                .reduce((a, b) -> a + "; " + b)
                .orElse("unknown error");
    }
}
