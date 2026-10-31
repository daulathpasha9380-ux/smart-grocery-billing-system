package com.billingsystem.billing_system;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Ensures PostgreSQL JDBC connectivity for Neon and Render environments:
 * 1. Converts postgres:// and postgresql:// URIs to standard jdbc:postgresql:// syntax.
 * 2. Extracts embedded credentials if username/password are not explicitly set.
 * 3. Enforces sslmode=require for Neon and remote PostgreSQL hosts.
 */
@Component
@Profile("prod")
public class NeonDataSourcePostProcessor implements BeanPostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(NeonDataSourcePostProcessor.class);

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof DataSourceProperties properties) {
            normalizeDataSourceProperties(properties);
        }
        return bean;
    }

    public static void normalizeDataSourceProperties(DataSourceProperties properties) {
        String rawUrl = properties.getUrl();
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }

        String url = rawUrl.trim();

        // Convert URI scheme if starting with postgres:// or postgresql://
        if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
            try {
                URI uri = URI.create(url);
                String userInfo = uri.getUserInfo();
                if (userInfo != null) {
                    String[] parts = userInfo.split(":", 2);
                    if (properties.getUsername() == null || properties.getUsername().isBlank()) {
                        properties.setUsername(parts[0]);
                    }
                    if (parts.length > 1 && (properties.getPassword() == null || properties.getPassword().isBlank())) {
                        properties.setPassword(parts[1]);
                    }
                }
                int port = uri.getPort() > 0 ? uri.getPort() : 5432;
                String path = uri.getPath() != null && !uri.getPath().isBlank() ? uri.getPath() : "/freshledger";
                String query = uri.getQuery();
                url = "jdbc:postgresql://" + uri.getHost() + ":" + port + path + (query != null && !query.isBlank() ? "?" + query : "");
            } catch (Exception e) {
                if (url.startsWith("postgres://")) {
                    url = "jdbc:postgresql://" + url.substring("postgres://".length());
                } else {
                    url = "jdbc:" + url;
                }
            }
        }

        // Enforce SSL for remote PostgreSQL hosts (e.g., Neon endpoint)
        if (url.startsWith("jdbc:postgresql://")) {
            boolean isLocal = url.contains("localhost") || url.contains("127.0.0.1");
            if (!isLocal && !url.contains("sslmode=") && !url.contains("ssl=true")) {
                url = url.contains("?") ? url + "&sslmode=require" : url + "?sslmode=require";
                LOGGER.info("Configured sslmode=require for remote PostgreSQL connection.");
            }
        }

        properties.setUrl(url);
    }
}
