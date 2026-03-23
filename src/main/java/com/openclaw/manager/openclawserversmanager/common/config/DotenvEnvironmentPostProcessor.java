package com.openclaw.manager.openclawserversmanager.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads variables from a {@code .env} file in the working directory into
 * Spring's Environment so that {@code ${VAR}} placeholders in
 * {@code application.properties} and {@code @Value} annotations resolve
 * correctly without requiring exported shell variables.
 *
 * <p>Existing system/environment variables take precedence — the .env file
 * only fills in values that are not already set.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String PROPERTY_SOURCE_NAME = "dotenvProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dotenv = Path.of(".env");
        if (!Files.exists(dotenv)) {
            return;
        }

        try {
            Map<String, Object> props = new HashMap<>();
            for (String line : Files.readAllLines(dotenv)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                // Strip surrounding quotes if present
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                props.put(key, value);
            }

            // Add with lowest precedence so real env vars and system properties win
            environment.getPropertySources()
                    .addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        } catch (IOException e) {
            // Silently ignore — .env loading is best-effort
        }
    }
}
