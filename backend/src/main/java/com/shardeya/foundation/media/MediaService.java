package com.shardeya.foundation.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shardeya.foundation.media.dto.MediaResponse;
import com.shardeya.foundation.media.dto.UploadIntentRequest;
import com.shardeya.foundation.media.dto.UploadIntentResponse;
import com.shardeya.platform.BadRequestException;
import com.shardeya.platform.ForbiddenException;
import com.shardeya.platform.ResourceNotFoundException;
import com.shardeya.platform.TenantContextBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * M-05 slice for M2: standard bucket only (sensitive/gov-ID bucket is M3),
 * synchronous derivative generation (not outbox/async — see this class's
 * "generateDerivatives" javadoc for why), no AV scan (ClamAV isn't in
 * docker-compose locally; PENDING goes straight to READY or REJECTED,
 * skipping SCANNING — a real gap, not silently pretended away).
 */
@Service
public class MediaService {

    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private static final int THUMB_WIDTH = 200;
    private static final int CARD_WIDTH = 600;
    private static final int FULL_WIDTH = 1600;
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // server hard cap (M-05 §10 "if the browser can't compress, reject >10MB")

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MediaProperties props;
    private final MediaAssetRepository repository;
    private final ObjectMapper objectMapper;
    private final TenantContextBinder tenantContextBinder;
    private final AtomicBoolean bucketEnsured = new AtomicBoolean(false);

    public MediaService(S3Client s3Client, S3Presigner s3Presigner, MediaProperties props,
                        MediaAssetRepository repository, ObjectMapper objectMapper, TenantContextBinder tenantContextBinder) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.props = props;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.tenantContextBinder = tenantContextBinder;
    }

    public UploadIntentResponse createUploadIntent(UploadIntentRequest req) {
        if (req.sizeBytes() > MAX_SIZE_BYTES) {
            throw new BadRequestException("sizeBytes", "MEDIA_TOO_LARGE", "error.media.tooLarge");
        }
        ensureBucket();

        UUID orgId = tenantContextBinder.currentOrgId();
        UUID mediaId = UUID.randomUUID();
        String storageKey = "org/%s/%s/%s/%s".formatted(orgId, req.purpose(), mediaId, req.filename());
        MediaAsset.BucketClass bucketClass = req.sensitive() ? MediaAsset.BucketClass.SENSITIVE : MediaAsset.BucketClass.STANDARD;

        MediaAsset asset = new MediaAsset(mediaId, orgId, storageKey, bucketClass,
                req.filename(), req.mimeType(), req.sizeBytes(), tenantContextBinder.current().userId());
        repository.save(asset);

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getUploadPresignSeconds()))
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucketFor(bucketClass))
                        .key(storageKey)
                        .contentType(req.mimeType())
                        .build())
                .build();
        String url = s3Presigner.presignPutObject(presignRequest).url().toString();

        return new UploadIntentResponse(mediaId, url, props.getUploadPresignSeconds());
    }

    public MediaResponse complete(UUID mediaId) {
        MediaAsset asset = repository.findById(mediaId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        String bucket = bucketFor(asset.getBucketClass());

        byte[] bytes;
        try (var stream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket).key(asset.getStorageKey()).build())) {
            bytes = stream.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read uploaded object from storage", e);
        }

        String detectedMime = MagicBytes.detect(bytes);
        if (detectedMime == null) {
            asset.setStatus(MediaAsset.Status.REJECTED);
            asset.setRejectReason("error.media.unrecognizedType");
            repository.save(asset);
            return toResponse(asset);
        }
        asset.setMimeType(detectedMime);
        asset.setSizeBytes(bytes.length);

        try {
            // Sensitive assets (gov ID scans) are viewed full-size via the
            // audited reveal endpoint only, never as a gallery/thumbnail --
            // no derivatives needed, and generating them would mean writing
            // resized copies of a government ID into the bucket too.
            if (asset.getBucketClass() == MediaAsset.BucketClass.STANDARD && detectedMime.startsWith("image/")) {
                generateDerivatives(asset, bytes);
            }
            asset.setStatus(MediaAsset.Status.READY);
        } catch (Exception e) {
            log.warn("Derivative generation failed for media {}", mediaId, e);
            asset.setStatus(MediaAsset.Status.REJECTED);
            asset.setRejectReason("error.media.processingFailed");
        }
        repository.save(asset);
        return toResponse(asset);
    }

    // M7: server-generated files (PDFs from DocumentGenerationService) skip
    // the upload-intent/presigned-PUT/complete dance entirely -- there is no
    // client uploading bytes we don't already have in memory, so this
    // writes directly and creates an already-READY row in one call. No
    // MagicBytes sniff (we generated these bytes ourselves; trusting our
    // own renderer's output here is not the same risk MagicBytes guards
    // against for client uploads) and no derivatives (a PDF has none).
    // Reassigns and returns the managed `save()` result per this
    // codebase's own documented merge()-vs-persist() rule for a
    // manually-assigned @Id (CLAUDE.md M2/M4 notes) -- callers must use
    // the returned id, not assume the entity they'd have built is managed.
    //
    // `uploadedBy` is an explicit parameter, NOT read from
    // tenantContextBinder.current().userId() the way every other method in
    // this class reads it -- a background-job caller (auto-receipt
    // generation via the outbox) binds TenantContext with a placeholder
    // SYSTEM_ACTOR_ID that was only ever meant for the RLS-context GUC,
    // never for writing into an FK-constrained column (see
    // DocumentGenerationService.autoGenerateReceipt's own comment for the
    // real ConstraintViolationException this caused before the caller
    // started passing a real app_user id explicitly here).
    public UUID storeGenerated(byte[] bytes, String filename, String mimeType, String purpose, UUID uploadedBy) {
        ensureBucket();
        UUID orgId = tenantContextBinder.currentOrgId();
        UUID mediaId = UUID.randomUUID();
        String storageKey = "org/%s/%s/%s/%s".formatted(orgId, purpose, mediaId, filename);

        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(props.getBucketStandard()).key(storageKey).contentType(mimeType).build(),
                RequestBody.fromBytes(bytes));

        MediaAsset asset = new MediaAsset(mediaId, orgId, storageKey, MediaAsset.BucketClass.STANDARD,
                filename, mimeType, bytes.length, uploadedBy);
        asset.setStatus(MediaAsset.Status.READY);
        asset = repository.save(asset);
        return asset.getId();
    }

    // M7: raw bytes for server-side use (document download endpoint, bulk
    // demand-letter ZIP assembly) -- distinct from get()'s presigned-URL
    // response, which is for a browser to fetch directly.
    public byte[] downloadBytes(UUID mediaId) {
        MediaAsset asset = repository.findById(mediaId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        try (var stream = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucketFor(asset.getBucketClass())).key(asset.getStorageKey()).build())) {
            return stream.readAllBytes();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to read object from storage", e);
        }
    }

    public MediaResponse get(UUID mediaId) {
        MediaAsset asset = repository.findById(mediaId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        // CLAUDE.md rule #6: sensitive files are never served through the
        // standard pipeline. There is no unaudited path to a presigned URL
        // for these -- callers must go through the specific audited reveal
        // endpoint for the entity that owns this document (e.g. plot_sale's
        // gov-id endpoint), which calls presignSensitiveGet directly.
        if (asset.getBucketClass() == MediaAsset.BucketClass.SENSITIVE) {
            throw new ForbiddenException("error.media.sensitiveRequiresAuditedEndpoint");
        }
        return toResponse(asset);
    }

    // Called only from an already-permission-gated, already-audited caller
    // (e.g. PlotSaleService's gov-id reveal) -- never exposed directly as a
    // MediaController endpoint. Skips the standard get()'s own block since
    // this IS the audited path that block exists to force callers through.
    public String presignSensitiveGet(UUID mediaId) {
        MediaAsset asset = repository.findById(mediaId).orElseThrow(() -> new ResourceNotFoundException("error.notFound"));
        if (asset.getBucketClass() != MediaAsset.BucketClass.SENSITIVE) {
            throw new BadRequestException("mediaId", "MEDIA_NOT_SENSITIVE", "error.media.notSensitive");
        }
        return presignGet(asset.getStorageKey(), props.getBucketSensitive());
    }

    // Synchronous, not outbox/async: async would need the background poller
    // to bind a tenant context correctly before touching this RLS-enforced
    // table — exactly the class of bug M1 spent real time on for a different
    // reason (eager connection checkout fixing the RLS GUC before bind ran).
    // A user-uploaded photo is already small (M-05 validation: <=2MB
    // post-compression) and three in-memory resizes are fast; genuinely
    // heavy async work (video transcode) isn't in scope this milestone.
    private void generateDerivatives(MediaAsset asset, byte[] original) throws Exception {
        int[] dims = ImageResizer.dimensions(original);
        asset.setWidth(dims[0]);
        asset.setHeight(dims[1]);

        Map<String, String> derivatives = new HashMap<>();
        derivatives.put("thumb", uploadDerivative(asset, original, "thumb", THUMB_WIDTH));
        derivatives.put("card", uploadDerivative(asset, original, "card", CARD_WIDTH));
        derivatives.put("full", uploadDerivative(asset, original, "full", FULL_WIDTH));
        asset.setDerivatives(objectMapper.writeValueAsString(derivatives));
    }

    private String uploadDerivative(MediaAsset asset, byte[] original, String label, int width) throws Exception {
        byte[] resized = ImageResizer.resizeToWidth(original, width);
        String key = asset.getStorageKey() + ".derivative-" + label + ".jpg";
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(props.getBucketStandard()).key(key).contentType("image/jpeg").build(),
                RequestBody.fromBytes(resized));
        return key;
    }

    private MediaResponse toResponse(MediaAsset asset) {
        // Sensitive assets never get a presigned URL here, including right
        // after complete() -- even the uploader's own immediate post-upload
        // response has no unaudited path to view the file. The audited
        // reveal endpoint (presignSensitiveGet) is the only way to see it.
        if (asset.getBucketClass() == MediaAsset.BucketClass.SENSITIVE) {
            return new MediaResponse(asset.getId(), asset.getStatus().name(), null, Map.of(),
                    asset.getWidth(), asset.getHeight(), asset.getMimeType(), asset.getSizeBytes());
        }
        String url = presignGet(asset.getStorageKey(), props.getBucketStandard());
        Map<String, String> derivativeUrls = new HashMap<>();
        if (asset.getDerivatives() != null) {
            try {
                Map<String, String> keys = objectMapper.readValue(asset.getDerivatives(), Map.class);
                keys.forEach((label, key) -> derivativeUrls.put(label, presignGet(key, props.getBucketStandard())));
            } catch (Exception e) {
                log.warn("Could not parse derivatives for media {}", asset.getId(), e);
            }
        }
        return new MediaResponse(asset.getId(), asset.getStatus().name(), url, derivativeUrls,
                asset.getWidth(), asset.getHeight(), asset.getMimeType(), asset.getSizeBytes());
    }

    private String bucketFor(MediaAsset.BucketClass bucketClass) {
        return bucketClass == MediaAsset.BucketClass.SENSITIVE ? props.getBucketSensitive() : props.getBucketStandard();
    }

    private String presignGet(String key, String bucket) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getReadPresignSeconds()))
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    // Lazy, not an ApplicationRunner at startup: an eager startup check would
    // make every test that boots the Spring context (including ones with no
    // MinIO container, like AuthFlowIntegrationTest) depend on MinIO being
    // reachable. Only the first real upload-intent call pays this cost.
    private void ensureBucket() {
        if (bucketEnsured.get()) return;
        ensureBucketExists(props.getBucketStandard());
        ensureBucketExists(props.getBucketSensitive());
        bucketEnsured.set(true);
    }

    private void ensureBucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (Exception notFound) {
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (BucketAlreadyOwnedByYouException alreadyExists) {
                // fine — another request/instance created it first
            }
        }
    }
}
