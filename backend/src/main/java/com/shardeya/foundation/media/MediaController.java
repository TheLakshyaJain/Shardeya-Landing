package com.shardeya.foundation.media;

import com.shardeya.foundation.media.dto.MediaResponse;
import com.shardeya.foundation.media.dto.UploadIntentRequest;
import com.shardeya.foundation.media.dto.UploadIntentResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @PostMapping("/upload-intent")
    public UploadIntentResponse uploadIntent(@Valid @RequestBody UploadIntentRequest request) {
        return mediaService.createUploadIntent(request);
    }

    @PostMapping("/{id}/complete")
    public MediaResponse complete(@PathVariable UUID id) {
        return mediaService.complete(id);
    }

    @GetMapping("/{id}")
    public MediaResponse get(@PathVariable UUID id) {
        return mediaService.get(id);
    }
}
