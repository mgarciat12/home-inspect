package com.homeinspect.app.photo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** Image processing settings ({@code home-inspect.photos.*}). */
@ConfigurationProperties("home-inspect.photos")
public record PhotoProperties(
        @DefaultValue("15MB") DataSize maxFileSize,
        @DefaultValue("1600") int optimizedSize,
        @DefaultValue("400") int thumbnailSize,
        @DefaultValue("0.85") float jpegQuality,
        @DefaultValue("heif-thumbnailer") String heicCommand) {
}
