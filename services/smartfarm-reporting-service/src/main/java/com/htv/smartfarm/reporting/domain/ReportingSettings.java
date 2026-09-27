package com.htv.smartfarm.reporting.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Reporting settings: where generated artifacts are written, and how long a download location
 * stays valid. In this starter the "download location" is a local file path served back as a
 * short-lived reference; a production build would swap this for object storage + a presigned URL.
 */
@ConfigurationProperties(prefix = "smartfarm.reporting")
public class ReportingSettings {

    /** Directory where export artifacts are written. */
    private String outputDir = "./.local/reports";

    /** Seconds a returned download location is considered valid. */
    private long downloadTtlSeconds = 900;

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public long getDownloadTtlSeconds() {
        return downloadTtlSeconds;
    }

    public void setDownloadTtlSeconds(long downloadTtlSeconds) {
        this.downloadTtlSeconds = downloadTtlSeconds;
    }
}
