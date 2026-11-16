package com.homeinspect.app.photo;

import com.homeinspect.app.report.ReportItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Metadata of a photo; the files ({@code <key>.jpg} and {@code <key>_thumb.jpg}) are on disk. */
@Entity
@Table(name = "photo")
@Getter
@Setter
@NoArgsConstructor
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_item_id", updatable = false)
    private ReportItem item;

    @Column(name = "file_key", nullable = false, unique = true, updatable = false)
    private String fileKey;

    private String caption;

    @Column(nullable = false)
    private int position;

    private int width;
    private int height;

    @Column(name = "size_bytes")
    private long sizeBytes;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public String optimizedFile() {
        return fileKey + ".jpg";
    }

    public String thumbnailFile() {
        return fileKey + "_thumb.jpg";
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
