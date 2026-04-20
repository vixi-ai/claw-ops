package com.openclaw.manager.openclawserversmanager.apps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Points the backend at a pinned release of claw-ops-chat. The release asset
 * installer.tar.gz + its SHA256 are fetched by bootstrap.sh on the target
 * server; this bean carries the pin so operators can bump versions via env
 * vars without a redeploy-and-rebuild.
 */
@Configuration
@ConfigurationProperties(prefix = "claw.chat.installer")
public class ChatInstallerProperties {

    private String repo = "pejovicvuk/claw-ops-chat";
    private String tag = "latest";
    private String sha256 = "skip";
    private String bootstrapUrl =
            "https://raw.githubusercontent.com/pejovicvuk/claw-ops-chat/main/prod-example/bootstrap.sh";

    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }

    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public String getBootstrapUrl() { return bootstrapUrl; }
    public void setBootstrapUrl(String bootstrapUrl) { this.bootstrapUrl = bootstrapUrl; }
}
