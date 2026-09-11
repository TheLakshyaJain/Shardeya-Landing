package com.shardeya.foundation.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shardeya.media.s3")
public class MediaProperties {

    private String endpoint;
    // M7: the SDK's own in-container calls (s3Client -- direct reads/writes,
    // e.g. MediaService.complete()'s magic-byte sniff, derivative writes,
    // storeGenerated()) and the presigned URLs handed to a browser
    // (s3Presigner) need genuinely different hostnames the moment the
    // backend itself runs inside docker-compose: "minio" only resolves on
    // the compose network the backend container is on, while a browser on
    // the host can only reach MinIO via its published host port
    // ("localhost:9000"). Defaults to `endpoint` when unset, which is
    // exactly right for the "mvn spring-boot:run directly on the host"
    // dev shape (both audiences are the same host, same hostname) -- only
    // a genuinely containerized backend needs to actually diverge these.
    // Found live: a fresh, from-scratch docker-compose stack's very first
    // in-container S3 write (this milestone's own storeGenerated() call)
    // failed with "Connection refused" to localhost:9000 -- unmasking a
    // latent bug that predates this milestone (MediaService.complete()'s
    // own s3Client.getObject() call was equally exposed, just apparently
    // never actually exercised against a truly fresh, no-override
    // docker-compose stack before now).
    private String publicEndpoint;
    private String region;
    private String accessKey;
    private String secretKey;
    private String bucketStandard;
    private String bucketSensitive;
    private long uploadPresignSeconds = 900;
    private long readPresignSeconds = 3600;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /** Falls back to {@link #getEndpoint()} when not explicitly configured -- see the field's own javadoc. */
    public String getPublicEndpoint() {
        return publicEndpoint != null ? publicEndpoint : endpoint;
    }

    public void setPublicEndpoint(String publicEndpoint) {
        this.publicEndpoint = publicEndpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucketStandard() {
        return bucketStandard;
    }

    public void setBucketStandard(String bucketStandard) {
        this.bucketStandard = bucketStandard;
    }

    public String getBucketSensitive() {
        return bucketSensitive;
    }

    public void setBucketSensitive(String bucketSensitive) {
        this.bucketSensitive = bucketSensitive;
    }

    public long getUploadPresignSeconds() {
        return uploadPresignSeconds;
    }

    public void setUploadPresignSeconds(long uploadPresignSeconds) {
        this.uploadPresignSeconds = uploadPresignSeconds;
    }

    public long getReadPresignSeconds() {
        return readPresignSeconds;
    }

    public void setReadPresignSeconds(long readPresignSeconds) {
        this.readPresignSeconds = readPresignSeconds;
    }
}
