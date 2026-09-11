package com.shardeya.foundation.media;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * MinIO locally (S3-compatible). Two things MinIO needs that a real AWS S3
 * bucket wouldn't: {@code endpointOverride} (it isn't at *.amazonaws.com) and
 * {@code pathStyleAccessEnabled} (MinIO doesn't support virtual-hosted-style
 * bucket addressing by default the way AWS S3 does).
 */
@Configuration
@EnableConfigurationProperties(MediaProperties.class)
public class S3Config {

    @Bean
    public S3Client s3Client(MediaProperties props) {
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(MediaProperties props) {
        // The browser is the one that actually fetches this URL -- it needs
        // the host-reachable endpoint, not the in-container one s3Client
        // uses (see MediaProperties.publicEndpoint's own javadoc).
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getPublicEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
