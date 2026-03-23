package com.openclaw.manager.openclawserversmanager.domains.provider.cloudflare;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class CloudflareModels {

    private CloudflareModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudflareResponse<T>(
            boolean success,
            List<CloudflareError> errors,
            List<CloudflareMessage> messages,
            T result,
            @JsonProperty("result_info") ResultInfo resultInfo
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudflareListResponse<T>(
            boolean success,
            List<CloudflareError> errors,
            List<T> result,
            @JsonProperty("result_info") ResultInfo resultInfo
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudflareError(
            int code,
            String message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudflareMessage(
            int code,
            String message
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultInfo(
            int page,
            @JsonProperty("per_page") int perPage,
            int count,
            @JsonProperty("total_count") int totalCount,
            @JsonProperty("total_pages") int totalPages
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudflareZone(
            String id,
            String name,
            String status,
            @JsonProperty("name_servers") List<String> nameServers
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CloudflareDnsRecord(
            String id,
            String type,
            String name,
            String content,
            int ttl,
            boolean proxied,
            @JsonProperty("zone_id") String zoneId
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenVerifyResult(
            String id,
            String status
    ) {
    }

    public record CreateDnsRecordRequest(
            String type,
            String name,
            String content,
            int ttl,
            boolean proxied
    ) {
    }
}
