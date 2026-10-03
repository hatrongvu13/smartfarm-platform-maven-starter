package com.htv.smartfarm.reporting.download;

import com.htv.smartfarm.reporting.domain.ExportJobEntity;

/**
 * Resolves a completed export job into a client-usable download location (a URL + the
 * content type), decoupling the gRPC surface from WHERE artifacts live. The default
 * implementation ({@link LocalFileDownloadLocationResolver}) returns a local {@code file://}
 * reference, suitable for a single-instance dev/starter deployment. A production deployment
 * supplies its own bean (e.g. an S3 presigned-URL resolver) WITHOUT changing the gRPC service
 * or the response contract — the API still returns {url, contentType, expiresAt}.
 */
public interface DownloadLocationResolver {

    record Location(String url, String contentType, long expiresAtEpochMillis) {
    }

    /**
     * @param job a job that is COMPLETED and has a non-null file path (validated by the caller).
     * @param ttlSeconds how long the returned location should be considered valid.
     */
    Location resolve(ExportJobEntity job, long ttlSeconds);
}
