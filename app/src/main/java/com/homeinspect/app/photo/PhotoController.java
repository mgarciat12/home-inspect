package com.homeinspect.app.photo;

import com.homeinspect.app.photo.PhotoService.PhotoFile;
import com.homeinspect.app.photo.PhotoService.UploadResult;
import com.homeinspect.app.photo.PhotoService.Variant;
import com.homeinspect.app.security.CurrentUserProvider;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Photo endpoints used by the editor (JSON) and protected image download. */
@RestController
@RequestMapping("/reports/{reportId}")
@RequiredArgsConstructor
class PhotoController {

    private final PhotoService photos;
    private final CurrentUserProvider currentUser;

    record CaptionRequest(@Size(max = 500) String caption) {
    }

    record OrderRequest(List<Long> ids) {
    }

    @PostMapping(path = "/items/{itemId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    UploadResult upload(@PathVariable long reportId, @PathVariable long itemId,
                        @RequestParam("files") List<MultipartFile> files) {
        return photos.upload(reportId, itemId, files, currentUser.get());
    }

    @PutMapping("/items/{itemId}/photos/order")
    ResponseEntity<Void> reorder(@PathVariable long reportId, @PathVariable long itemId, @RequestBody OrderRequest request) {
        photos.reorder(reportId, itemId, request.ids() == null ? List.of() : request.ids(), currentUser.get());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/photos/{photoId}")
    PhotoView caption(@PathVariable long reportId, @PathVariable long photoId, @RequestBody CaptionRequest request) {
        return photos.updateCaption(reportId, photoId, request.caption(), currentUser.get());
    }

    @PostMapping("/photos/{photoId}/cover")
    Map<String, Boolean> cover(@PathVariable long reportId, @PathVariable long photoId) {
        return Map.of("cover", photos.toggleCover(reportId, photoId, currentUser.get()));
    }

    @DeleteMapping("/photos/{photoId}")
    ResponseEntity<Void> delete(@PathVariable long reportId, @PathVariable long photoId) {
        photos.delete(reportId, photoId, currentUser.get());
        return ResponseEntity.noContent().build();
    }

    /** Image file: only for users who can see the report; cached privately by the browser. */
    @GetMapping("/photos/{photoId}/{variant:full|thumb}")
    ResponseEntity<Resource> image(@PathVariable long reportId, @PathVariable long photoId, @PathVariable String variant) {
        PhotoFile file = photos.file(reportId, photoId, Variant.valueOf(variant.toUpperCase(Locale.ROOT)), currentUser.get());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
                .lastModified(file.lastModified())
                .body(new FileSystemResource(file.path()));
    }
}
