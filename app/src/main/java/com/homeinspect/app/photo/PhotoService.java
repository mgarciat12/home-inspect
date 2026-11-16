package com.homeinspect.app.photo;

import com.homeinspect.app.photo.ImageProcessor.ProcessedImage;
import com.homeinspect.app.report.Report;
import com.homeinspect.app.report.ReportAccessGuard;
import com.homeinspect.app.report.ReportItem;
import com.homeinspect.app.report.ReportItemRepository;
import com.homeinspect.app.report.ReportNotFoundException;
import com.homeinspect.app.report.ReportService.ReportDeleted;
import com.homeinspect.app.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Photos of the report elements: upload (validated and re-encoded), caption, order, cover photo,
 * deletion and protected download. Files are removed from disk only after the transaction commits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photos;
    private final ReportItemRepository items;
    private final ReportAccessGuard guard;
    private final ImageProcessor processor;
    private final FileStorage storage;
    private final MessageSource messages;

    public enum Variant { FULL, THUMB }

    public record UploadError(String file, String message) {
    }

    public record UploadResult(List<PhotoView> photos, List<UploadError> errors) {
    }

    /** A file to send to the browser. */
    public record PhotoFile(Path path, long lastModified) {
    }

    @Transactional
    public UploadResult upload(long reportId, long itemId, List<MultipartFile> files, CurrentUser user) {
        ReportItem item = items.findInReport(reportId, itemId).orElseThrow(ReportNotFoundException::new);
        Report report = item.getSection().getReport();
        guard.checkEdit(report, user);

        List<PhotoView> uploaded = new ArrayList<>();
        List<UploadError> errors = new ArrayList<>();
        int position = photos.maxPosition(itemId);
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            String name = displayName(file.getOriginalFilename());
            try {
                ProcessedImage image = processor.process(file.getBytes());
                Photo photo = new Photo();
                photo.setItem(item);
                photo.setFileKey(UUID.randomUUID().toString());
                photo.setPosition(++position);
                photo.setWidth(image.width());
                photo.setHeight(image.height());
                photo.setSizeBytes(image.optimized().length);
                photo.setOriginalName(name);
                Path optimized = storage.photo(reportId, photo.optimizedFile());
                Path thumbnail = storage.photo(reportId, photo.thumbnailFile());
                storage.write(optimized, image.optimized());
                storage.write(thumbnail, image.thumbnail());
                deleteOnRollback(optimized, thumbnail);
                photos.save(photo);
                uploaded.add(PhotoView.of(photo, reportId, report.getCoverPhotoId()));
            } catch (ImageRejectedException e) {
                errors.add(new UploadError(name, message(e.code())));
            } catch (IOException e) {
                errors.add(new UploadError(name, message("photo.error.invalid")));
            }
        }
        report.touch();
        return new UploadResult(uploaded, errors);
    }

    @Transactional
    public PhotoView updateCaption(long reportId, long photoId, String caption, CurrentUser user) {
        Photo photo = load(reportId, photoId);
        Report report = photo.getItem().getSection().getReport();
        guard.checkEdit(report, user);
        String clean = StringUtils.hasText(caption) ? caption.trim() : null;
        photo.setCaption(clean != null && clean.length() > 500 ? clean.substring(0, 500) : clean);
        return PhotoView.of(photo, reportId, report.getCoverPhotoId());
    }

    /** New order of the photos of an element (ids in display order). */
    @Transactional
    public void reorder(long reportId, long itemId, List<Long> photoIds, CurrentUser user) {
        ReportItem item = items.findInReport(reportId, itemId).orElseThrow(ReportNotFoundException::new);
        guard.checkEdit(item.getSection().getReport(), user);
        List<Photo> current = photos.findByItemId(itemId);
        Map<Long, Photo> byId = current.stream().collect(Collectors.toMap(Photo::getId, p -> p));
        int position = 1;
        for (Long id : photoIds) {
            Photo photo = byId.remove(id);
            if (photo != null) {
                photo.setPosition(position++);
            }
        }
        // Photos not listed keep their relative order at the end
        for (Photo photo : current) {
            if (byId.containsKey(photo.getId())) {
                photo.setPosition(position++);
            }
        }
    }

    /** Marks the photo as the cover of the report (or unmarks it if it already was). */
    @Transactional
    public boolean toggleCover(long reportId, long photoId, CurrentUser user) {
        Photo photo = load(reportId, photoId);
        Report report = photo.getItem().getSection().getReport();
        guard.checkEdit(report, user);
        boolean cover = !photo.getId().equals(report.getCoverPhotoId());
        report.setCoverPhotoId(cover ? photo.getId() : null);
        return cover;
    }

    @Transactional
    public void delete(long reportId, long photoId, CurrentUser user) {
        Photo photo = load(reportId, photoId);
        Report report = photo.getItem().getSection().getReport();
        guard.checkEdit(report, user);
        if (photo.getId().equals(report.getCoverPhotoId())) {
            report.setCoverPhotoId(null);
        }
        photos.delete(photo);
        deleteAfterCommit(storage.photo(reportId, photo.optimizedFile()), storage.photo(reportId, photo.thumbnailFile()));
    }

    /** File of a photo for a user allowed to see the report. */
    @Transactional(readOnly = true)
    public PhotoFile file(long reportId, long photoId, Variant variant, CurrentUser user) {
        Photo photo = load(reportId, photoId);
        guard.checkView(photo.getItem().getSection().getReport(), user);
        Path path = storage.photo(reportId, variant == Variant.THUMB ? photo.thumbnailFile() : photo.optimizedFile());
        if (!path.toFile().isFile()) {
            throw new ReportNotFoundException();
        }
        return new PhotoFile(path, path.toFile().lastModified());
    }

    /** Photos of several elements, for the editor and the PDF (access already checked by the caller). */
    @Transactional(readOnly = true)
    public Map<Long, List<PhotoView>> byItems(long reportId, Collection<Long> itemIds, Long coverPhotoId) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<PhotoView>> result = new HashMap<>();
        for (Photo photo : photos.findByItemIds(itemIds)) {
            result.computeIfAbsent(photo.getItem().getId(), id -> new ArrayList<>())
                    .add(PhotoView.of(photo, reportId, coverPhotoId));
        }
        return result;
    }

    /** Removes the photo directory once the report deletion is committed. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onReportDeleted(ReportDeleted event) {
        storage.deleteDirectory(storage.photosOf(event.reportId()));
        log.info("Photos of report {} deleted from disk", event.reportId());
    }

    // ---- helpers ----

    private Photo load(long reportId, long photoId) {
        return photos.findInReport(reportId, photoId).orElseThrow(ReportNotFoundException::new);
    }

    private void deleteAfterCommit(Path... paths) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Path path : paths) {
                    storage.delete(path);
                }
            }
        });
    }

    /** Files written for a photo whose row is rolled back are removed. */
    private void deleteOnRollback(Path... paths) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    for (Path path : paths) {
                        storage.delete(path);
                    }
                }
            }
        });
    }

    private static String displayName(String original) {
        if (!StringUtils.hasText(original)) {
            return "foto";
        }
        String name = StringUtils.getFilename(original.replace('\\', '/'));
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private String message(String code) {
        return messages.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }
}
