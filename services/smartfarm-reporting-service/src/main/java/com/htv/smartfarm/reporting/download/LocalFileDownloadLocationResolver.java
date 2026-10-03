package com.htv.smartfarm.reporting.download;

import com.htv.smartfarm.reporting.domain.ExportJobEntity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default {@link DownloadLocationResolver}: returns a local {@code file://} reference to the
 * artifact the worker wrote under {@code smartfarm.reporting.output-dir}. This preserves the
 * original single-instance behaviour and is the fallback when no production resolver bean
 * (e.g. an object-storage presigned-URL resolver) is registered.
 *
 * NOT horizontally scalable: a {@code file://} URL is only reachable on the instance that
 * produced it. A multi-instance / durable deployment MUST provide its own resolver bean.
 */
public class LocalFileDownloadLocationResolver implements DownloadLocationResolver {

    @Override
    public Location resolve(ExportJobEntity job, long ttlSeconds) {
        long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000;
        String contentType = job.getContentType() == null
                ? "application/octet-stream"
                : job.getContentType();
        return new Location("file://" + job.getFilePath(), contentType, expiresAt);
    }

    @Configuration(proxyBeanMethods = false)
    public static class DefaultResolverConfiguration {
        /** Registered only when no other DownloadLocationResolver bean exists (e.g. prod S3). */
        @Bean
        @ConditionalOnMissingBean(DownloadLocationResolver.class)
        public DownloadLocationResolver localFileDownloadLocationResolver() {
            return new LocalFileDownloadLocationResolver();
        }
    }
}
