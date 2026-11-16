package com.homeinspect.app.photo;

/** A photo as shown in the editor (URLs are protected by the report access rules). */
public record PhotoView(long id, long itemId, String caption, int position, int width, int height,
                        String url, String thumbnailUrl, boolean cover) {

    static PhotoView of(Photo photo, long reportId, Long coverPhotoId) {
        String base = "/reports/" + reportId + "/photos/" + photo.getId();
        return new PhotoView(photo.getId(), photo.getItem().getId(), photo.getCaption(), photo.getPosition(),
                photo.getWidth(), photo.getHeight(), base + "/full", base + "/thumb",
                photo.getId().equals(coverPhotoId));
    }
}
