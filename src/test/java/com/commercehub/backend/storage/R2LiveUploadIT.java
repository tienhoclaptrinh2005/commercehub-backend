package com.commercehub.backend.storage;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in smoke test against the real development R2 bucket.
 * It never runs in the normal unit-test suite and never logs credentials or a signed URL.
 */
@Tag("live-r2")
@EnabledIfEnvironmentVariable(named = "R2_LIVE_TEST_ENABLED", matches = "(?i)true")
class R2LiveUploadIT {

    private static final String LOCAL_ORIGIN = "http://localhost:3000";
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    );

    @Test
    void uploadsReadsAndDeletesARealObjectThroughPresignedUrl() throws Exception {
        Properties environment = loadLocalEnvironment();
        URI endpoint = URI.create(required(environment, "R2_ENDPOINT"));
        String accessKeyId = required(environment, "R2_ACCESS_KEY_ID");
        String secretAccessKey = required(environment, "R2_SECRET_ACCESS_KEY");
        String bucket = required(environment, "R2_BUCKET");
        String publicBaseUrl = required(environment, "MEDIA_PUBLIC_BASE_URL").replaceAll("/+$", "");
        assertThat(accessKeyId)
                .as("R2_ACCESS_KEY_ID must be the 32-character alphanumeric S3 Access Key ID")
                .matches("[A-Za-z0-9]{32}");
        assertThat(secretAccessKey)
                .as("R2_SECRET_ACCESS_KEY has an invalid length")
                .hasSizeGreaterThanOrEqualTo(32);
        assertThat(bucket)
                .as("Live smoke test must never write to the production bucket")
                .isNotEqualTo("commercehub-prod");

        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build();
        String objectKey = "integration-tests/r2/" + UUID.randomUUID() + ".png";
        boolean uploaded = false;

        try (S3Client s3 = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .serviceConfiguration(s3Configuration)
                .build();
             S3Presigner presigner = S3Presigner.builder()
                     .endpointOverride(endpoint)
                     .region(Region.of("auto"))
                     .credentialsProvider(StaticCredentialsProvider.create(credentials))
                     .serviceConfiguration(s3Configuration)
                     .build()) {
            try {
                PutObjectRequest putObject = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType("image/png")
                        .cacheControl(CACHE_CONTROL)
                        .build();
                URI signedPutUrl = presigner.presignPutObject(PutObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(5))
                                .putObjectRequest(putObject)
                                .build())
                        .url()
                        .toURI();

                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(15))
                        .build();
                assertPreflightAllowsLocalFrontend(http, signedPutUrl);

                HttpRequest upload = HttpRequest.newBuilder(signedPutUrl)
                        .timeout(Duration.ofSeconds(30))
                        .header("Origin", LOCAL_ORIGIN)
                        .header("Content-Type", "image/png")
                        .header("Cache-Control", CACHE_CONTROL)
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(ONE_PIXEL_PNG))
                        .build();
                HttpResponse<String> uploadResponse = http.send(upload, HttpResponse.BodyHandlers.ofString());
                assertThat(uploadResponse.statusCode())
                        .withFailMessage(
                                "R2 PUT returned HTTP %s: %s",
                                uploadResponse.statusCode(),
                                sanitizeR2Error(uploadResponse.body())
                        )
                        .isBetween(200, 299);
                uploaded = true;

                ResponseBytes<GetObjectResponse> downloaded = s3.getObject(
                        builder -> builder.bucket(bucket).key(objectKey),
                        ResponseTransformer.toBytes()
                );
                assertThat(downloaded.asByteArray()).isEqualTo(ONE_PIXEL_PNG);

                HttpRequest publicRead = HttpRequest.newBuilder(URI.create(publicBaseUrl + "/" + objectKey))
                        .timeout(Duration.ofSeconds(30))
                        .header("Origin", LOCAL_ORIGIN)
                        .GET()
                        .build();
                HttpResponse<byte[]> publicResponse = http.send(publicRead, HttpResponse.BodyHandlers.ofByteArray());
                assertThat(publicResponse.statusCode()).isEqualTo(200);
                assertThat(publicResponse.body()).isEqualTo(ONE_PIXEL_PNG);
                assertThat(publicResponse.headers().firstValue("Access-Control-Allow-Origin"))
                        .contains(LOCAL_ORIGIN);
            } finally {
                if (uploaded) {
                    s3.deleteObject(builder -> builder.bucket(bucket).key(objectKey));
                }
            }
        }
    }

    private void assertPreflightAllowsLocalFrontend(HttpClient http, URI signedPutUrl)
            throws IOException, InterruptedException {
        HttpRequest preflight = HttpRequest.newBuilder(signedPutUrl)
                .timeout(Duration.ofSeconds(30))
                .header("Origin", LOCAL_ORIGIN)
                .header("Access-Control-Request-Method", "PUT")
                .header("Access-Control-Request-Headers", "cache-control,content-type")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = http.send(preflight, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .withFailMessage(
                        "R2 CORS preflight returned HTTP %s: %s. Verify the bucket allows origin %s, "
                                + "method PUT, and headers Content-Type plus Cache-Control.",
                        response.statusCode(),
                        sanitizeR2Error(response.body()),
                        LOCAL_ORIGIN
                )
                .isBetween(200, 299);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains(LOCAL_ORIGIN);
    }

    private Properties loadLocalEnvironment() throws IOException {
        Properties properties = new Properties();
        Path file = Path.of(".env");
        if (Files.isRegularFile(file)) {
            try (var reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            }
        }
        return properties;
    }

    private String sanitizeR2Error(String body) {
        if (body == null || body.isBlank()) {
            return "empty response body";
        }
        return body
                .replaceAll("(?s)<RequestId>.*?</RequestId>", "<RequestId>redacted</RequestId>")
                .replaceAll("(?s)<HostId>.*?</HostId>", "<HostId>redacted</HostId>")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String required(Properties properties, String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = properties.getProperty(name);
        }
        assertThat(value)
                .as("Missing required live-test setting: " + name)
                .isNotBlank()
                .doesNotContain("YOUR_ACCOUNT_ID")
                .doesNotContain("replace-with");
        return value.trim();
    }
}
