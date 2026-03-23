package com.openclaw.manager.openclawserversmanager.domains.provider.namecheap;

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
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class NamecheapAdapter implements DnsProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(NamecheapAdapter.class);
    private static final String BASE_URL = "https://api.namecheap.com/xml.response";
    private static final String IP_DETECT_URL = "https://api.ipify.org";

    private final HttpClient httpClient;
    private volatile String cachedPublicIp;

    public NamecheapAdapter() {
        this.httpClient = HttpClient.newBuilder().build();
    }

    @Override
    public DnsProviderType getProviderType() {
        return DnsProviderType.NAMECHEAP;
    }

    @Override
    public ProviderCapabilities getCapabilities() {
        return new ProviderCapabilities(
                true,
                false,
                false,
                120,
                List.of(DnsRecordType.A, DnsRecordType.AAAA, DnsRecordType.CNAME,
                        DnsRecordType.TXT, DnsRecordType.MX)
        );
    }

    @Override
    public ValidateCredentialsResponse validateCredentials(String decryptedToken, Map<String, Object> settings) {
        try {
            String apiUser = requireSetting(settings, "apiUser");
            String clientIp = resolveClientIp();

            // Call domains.getList as a lightweight credential check
            String url = buildBaseUrl("namecheap.domains.getList", apiUser, decryptedToken, clientIp)
                    + "&PageSize=10";

            Document doc = executeRequest(url);
            String status = doc.getDocumentElement().getAttribute("Status");

            if ("OK".equalsIgnoreCase(status)) {
                return new ValidateCredentialsResponse(true, "Namecheap API credentials are valid");
            }

            String error = extractErrors(doc);
            return new ValidateCredentialsResponse(false, "Namecheap API error: " + error);
        } catch (Exception e) {
            log.debug("Namecheap credential validation failed: {}", e.getMessage());
            return new ValidateCredentialsResponse(false, "Validation failed: " + e.getMessage());
        }
    }

    @Override
    public VerifyZoneResponse verifyZoneManageable(String zoneName, String decryptedToken, Map<String, Object> settings) {
        try {
            String apiUser = requireSetting(settings, "apiUser");
            String clientIp = resolveClientIp();
            NamecheapModels.DomainParts parts = NamecheapModels.DomainParts.from(zoneName);

            String url = buildBaseUrl("namecheap.domains.dns.getHosts", apiUser, decryptedToken, clientIp)
                    + "&SLD=" + enc(parts.sld()) + "&TLD=" + enc(parts.tld());

            Document doc = executeRequest(url);
            String status = doc.getDocumentElement().getAttribute("Status");

            if ("OK".equalsIgnoreCase(status)) {
                // Check if using Namecheap DNS
                NodeList resultNodes = doc.getElementsByTagName("DomainDNSGetHostsResult");
                boolean usingOurDns = true;
                if (resultNodes.getLength() > 0) {
                    String isUsing = ((Element) resultNodes.item(0)).getAttribute("IsUsingOurDNS");
                    usingOurDns = "true".equalsIgnoreCase(isUsing);
                }

                List<String> warnings = new ArrayList<>();
                if (!usingOurDns) {
                    warnings.add("Domain is not using Namecheap's DNS servers. DNS changes may not take effect.");
                }

                return new VerifyZoneResponse(true,
                        "Domain '%s' is accessible via Namecheap API".formatted(zoneName), warnings);
            }

            String error = extractErrors(doc);
            return new VerifyZoneResponse(false, "Cannot manage domain: " + error, List.of());
        } catch (Exception e) {
            log.debug("Namecheap zone verification failed for {}: {}", zoneName, e.getMessage());
            return new VerifyZoneResponse(false, "Verification failed: " + e.getMessage(), List.of());
        }
    }

    @Override
    public String resolveZoneId(String zoneName, String decryptedToken, Map<String, Object> settings) {
        // Namecheap doesn't have zone IDs — use the domain name itself
        return zoneName.toLowerCase();
    }

    @Override
    public DnsRecord createOrUpsertRecord(String zoneName, String providerZoneId, DnsRecord record,
                                           String decryptedToken, Map<String, Object> settings) {
        try {
            String apiUser = requireSetting(settings, "apiUser");
            String clientIp = resolveClientIp();
            NamecheapModels.DomainParts parts = NamecheapModels.DomainParts.from(zoneName);

            // Step 1: Get all existing hosts (read-modify-write pattern)
            List<NamecheapModels.HostRecord> existingHosts = getHosts(parts, apiUser, decryptedToken, clientIp);

            // Step 2: Build new host list — replace matching record or add new one
            String recordName = extractSubdomain(record.hostname(), zoneName);
            String recordType = record.type().name();

            List<NamecheapModels.HostRecord> newHosts = new ArrayList<>();
            boolean replaced = false;

            for (NamecheapModels.HostRecord host : existingHosts) {
                if (host.name().equalsIgnoreCase(recordName) && host.type().equalsIgnoreCase(recordType)) {
                    // Replace this record with updated values
                    newHosts.add(new NamecheapModels.HostRecord(
                            host.hostId(), recordName, recordType, record.value(),
                            record.type() == DnsRecordType.MX ? 10 : 0, record.ttl()));
                    replaced = true;
                } else {
                    newHosts.add(host);
                }
            }

            if (!replaced) {
                newHosts.add(new NamecheapModels.HostRecord(
                        null, recordName, recordType, record.value(),
                        record.type() == DnsRecordType.MX ? 10 : 0, record.ttl()));
            }

            // Step 3: Set all hosts
            setHosts(parts, newHosts, apiUser, decryptedToken, clientIp);

            // Step 4: Read back to get the HostId
            List<NamecheapModels.HostRecord> updated = getHosts(parts, apiUser, decryptedToken, clientIp);
            String hostId = updated.stream()
                    .filter(h -> h.name().equalsIgnoreCase(recordName) && h.type().equalsIgnoreCase(recordType))
                    .map(NamecheapModels.HostRecord::hostId)
                    .findFirst()
                    .orElse(recordName + ":" + recordType);

            return new DnsRecord(hostId, record.hostname(), record.type(), record.value(), record.ttl(), false);
        } catch (DnsProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new DnsProviderException(
                    "Namecheap API error creating record '%s': %s".formatted(record.hostname(), e.getMessage()),
                    null, e);
        }
    }

    @Override
    public void deleteRecord(String providerZoneId, String providerRecordId,
                             String decryptedToken, Map<String, Object> settings) {
        // providerRecordId is "name:type" or a HostId
        // providerZoneId is the domain name
        // We can't delete by ID alone — we need read-modify-write
        // The caller should use releaseAssignment which has full context
        // For now, this is best-effort: we'd need the zone name in settings
        log.warn("Namecheap deleteRecord called for record '{}' in zone '{}'. " +
                "Namecheap requires read-modify-write; standalone deletion not fully supported.",
                providerRecordId, providerZoneId);

        try {
            String apiUser = requireSetting(settings, "apiUser");
            String clientIp = resolveClientIp();
            NamecheapModels.DomainParts parts = NamecheapModels.DomainParts.from(providerZoneId);

            List<NamecheapModels.HostRecord> existingHosts = getHosts(parts, apiUser, decryptedToken, clientIp);

            // Try to match by hostId or by name:type composite key
            List<NamecheapModels.HostRecord> filtered = existingHosts.stream()
                    .filter(h -> !providerRecordId.equals(h.hostId())
                            && !providerRecordId.equals(h.name() + ":" + h.type()))
                    .toList();

            if (filtered.size() == existingHosts.size()) {
                log.warn("Record '{}' not found in Namecheap hosts, nothing to delete", providerRecordId);
                return;
            }

            setHosts(parts, filtered, apiUser, decryptedToken, clientIp);
        } catch (DnsProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new DnsProviderException(
                    "Failed to delete Namecheap DNS record '%s': %s".formatted(providerRecordId, e.getMessage()),
                    providerRecordId, e);
        }
    }

    @Override
    public List<DnsRecord> listRecords(String providerZoneId, String decryptedToken, Map<String, Object> settings) {
        try {
            String apiUser = requireSetting(settings, "apiUser");
            String clientIp = resolveClientIp();
            NamecheapModels.DomainParts parts = NamecheapModels.DomainParts.from(providerZoneId);

            List<NamecheapModels.HostRecord> hosts = getHosts(parts, apiUser, decryptedToken, clientIp);
            String zoneName = providerZoneId.toLowerCase();

            return hosts.stream()
                    .map(h -> {
                        DnsRecordType type;
                        try {
                            type = DnsRecordType.valueOf(h.type().toUpperCase());
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                        // Reconstruct full hostname: "@" → zoneName, "www" → "www.zoneName"
                        String hostname = "@".equals(h.name()) ? zoneName : h.name() + "." + zoneName;
                        return new DnsRecord(h.hostId(), hostname, type, h.address(), h.ttl(), false);
                    })
                    .filter(r -> r != null)
                    .toList();
        } catch (Exception e) {
            throw new DnsProviderException(
                    "Failed to list Namecheap DNS records: " + e.getMessage(), null, e);
        }
    }

    @Override
    public List<DiscoveredDomain> listDomains(String decryptedToken, Map<String, Object> settings) {
        try {
            String apiUser = requireSetting(settings, "apiUser");
            String clientIp = resolveClientIp();
            List<DiscoveredDomain> allDomains = new ArrayList<>();
            int page = 1;
            int totalPages = 1;

            do {
                String url = buildBaseUrl("namecheap.domains.getList", apiUser, decryptedToken, clientIp)
                        + "&PageSize=100&Page=" + page;

                Document doc = executeRequest(url);
                assertOk(doc);

                // Parse paging info
                NodeList pagingNodes = doc.getElementsByTagName("Paging");
                if (pagingNodes.getLength() > 0) {
                    Element paging = (Element) pagingNodes.item(0);
                    int totalItems = parseIntSafe(getChildText(paging, "TotalItems"), 0);
                    int pageSize = parseIntSafe(getChildText(paging, "PageSize"), 100);
                    totalPages = (totalItems + pageSize - 1) / pageSize;
                }

                // Parse domain elements
                NodeList domainNodes = doc.getElementsByTagName("Domain");
                for (int i = 0; i < domainNodes.getLength(); i++) {
                    Element el = (Element) domainNodes.item(i);
                    String name = el.getAttribute("Name");
                    boolean isOurDns = !"false".equalsIgnoreCase(el.getAttribute("IsOurDNS"));
                    allDomains.add(new DiscoveredDomain(name, name.toLowerCase(), isOurDns));
                }

                page++;
            } while (page <= totalPages);

            return allDomains;
        } catch (DnsProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new DnsProviderException("Failed to list Namecheap domains: " + e.getMessage(), null, e);
        }
    }

    // ── Internal helpers ──

    private List<NamecheapModels.HostRecord> getHosts(NamecheapModels.DomainParts parts,
                                                       String apiUser, String apiKey, String clientIp) {
        String url = buildBaseUrl("namecheap.domains.dns.getHosts", apiUser, apiKey, clientIp)
                + "&SLD=" + enc(parts.sld()) + "&TLD=" + enc(parts.tld());

        Document doc = executeRequest(url);
        assertOk(doc);

        List<NamecheapModels.HostRecord> hosts = new ArrayList<>();
        NodeList hostNodes = doc.getElementsByTagName("host");
        for (int i = 0; i < hostNodes.getLength(); i++) {
            Element el = (Element) hostNodes.item(i);
            hosts.add(new NamecheapModels.HostRecord(
                    el.getAttribute("HostId"),
                    el.getAttribute("Name"),
                    el.getAttribute("Type"),
                    el.getAttribute("Address"),
                    parseIntSafe(el.getAttribute("MXPref"), 0),
                    parseIntSafe(el.getAttribute("TTL"), 1800)
            ));
        }
        return hosts;
    }

    private void setHosts(NamecheapModels.DomainParts parts, List<NamecheapModels.HostRecord> hosts,
                          String apiUser, String apiKey, String clientIp) {
        StringBuilder url = new StringBuilder(
                buildBaseUrl("namecheap.domains.dns.setHosts", apiUser, apiKey, clientIp));
        url.append("&SLD=").append(enc(parts.sld()));
        url.append("&TLD=").append(enc(parts.tld()));

        for (int i = 0; i < hosts.size(); i++) {
            int idx = i + 1;
            NamecheapModels.HostRecord h = hosts.get(i);
            url.append("&HostName").append(idx).append("=").append(enc(h.name()));
            url.append("&RecordType").append(idx).append("=").append(enc(h.type()));
            url.append("&Address").append(idx).append("=").append(enc(h.address()));
            url.append("&TTL").append(idx).append("=").append(h.ttl());
            if ("MX".equalsIgnoreCase(h.type())) {
                url.append("&MXPref").append(idx).append("=").append(h.mxPref());
            }
        }

        Document doc = executeRequest(url.toString());
        assertOk(doc);
    }

    private Document executeRequest(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disable external entities for security
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(body)));
        } catch (Exception e) {
            throw new DnsProviderException("Namecheap API request failed: " + e.getMessage(), null, e);
        }
    }

    private void assertOk(Document doc) {
        String status = doc.getDocumentElement().getAttribute("Status");
        if (!"OK".equalsIgnoreCase(status)) {
            String error = extractErrors(doc);
            throw new DnsProviderException("Namecheap API error: " + error, null);
        }
    }

    private String extractErrors(Document doc) {
        NodeList errorNodes = doc.getElementsByTagName("Error");
        if (errorNodes.getLength() == 0) return "unknown error";

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < errorNodes.getLength(); i++) {
            Element el = (Element) errorNodes.item(i);
            String num = el.getAttribute("Number");
            String msg = el.getTextContent();
            if (!sb.isEmpty()) sb.append("; ");
            sb.append("[").append(num).append("] ").append(msg);
        }
        return sb.toString();
    }

    private String buildBaseUrl(String command, String apiUser, String apiKey, String clientIp) {
        return BASE_URL + "?ApiUser=" + enc(apiUser)
                + "&ApiKey=" + enc(apiKey)
                + "&UserName=" + enc(apiUser)
                + "&ClientIp=" + enc(clientIp)
                + "&Command=" + enc(command);
    }

    /**
     * Extracts the subdomain part from a full hostname relative to the zone.
     * e.g. "www.example.com" with zone "example.com" → "www"
     *      "example.com" with zone "example.com" → "@"
     */
    private String extractSubdomain(String hostname, String zoneName) {
        String h = hostname.toLowerCase();
        String z = zoneName.toLowerCase();
        if (h.equals(z)) return "@";
        if (h.endsWith("." + z)) {
            return h.substring(0, h.length() - z.length() - 1);
        }
        return h;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolveClientIp() {
        String cached = cachedPublicIp;
        if (cached != null) {
            return cached;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(IP_DETECT_URL))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String ip = response.body().trim();
            if (ip.isBlank()) {
                throw new DnsProviderException("Empty response from IP detection service", null);
            }
            cachedPublicIp = ip;
            log.info("Resolved server public IP for Namecheap: {}", ip);
            return ip;
        } catch (DnsProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new DnsProviderException(
                    "Failed to detect server public IP for Namecheap API: " + e.getMessage(), null, e);
        }
    }

    private String requireSetting(Map<String, Object> settings, String key) {
        if (settings == null || !settings.containsKey(key) || settings.get(key) == null) {
            throw new DnsProviderException(
                    "Missing required Namecheap setting: '%s'. Configure it in provider account settings.".formatted(key), null);
        }
        String value = settings.get(key).toString().trim();
        if (value.isBlank()) {
            throw new DnsProviderException(
                    "Namecheap setting '%s' must not be blank".formatted(key), null);
        }
        return value;
    }

    private static String getChildText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }

    private static int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
