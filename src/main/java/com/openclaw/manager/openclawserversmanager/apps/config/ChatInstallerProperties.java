package com.openclaw.manager.openclawserversmanager.apps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Points the backend at a git ref of claw-ops-chat whose prod-example/
 * bootstrap.sh + install.sh + nginx templates we execute on user servers.
 * Fetched directly via raw.githubusercontent.com — no GitHub Release
 * needed. Bump {@code ref} to a tag or SHA for reproducibility.
 */
@Configuration
@ConfigurationProperties(prefix = "claw.chat.installer")
public class ChatInstallerProperties {

    private String repo = "pejovicvuk/claw-ops-chat";
    private String ref = "main";
    private String bootstrapUrl =
            "https://raw.githubusercontent.com/pejovicvuk/claw-ops-chat/main/prod-example/bootstrap.sh";

    public String getRepo() { return repo; }
    public void setRepo(String repo) { this.repo = repo; }

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }

    public String getBootstrapUrl() { return bootstrapUrl; }
    public void setBootstrapUrl(String bootstrapUrl) { this.bootstrapUrl = bootstrapUrl; }
}
