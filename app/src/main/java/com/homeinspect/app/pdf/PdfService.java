package com.homeinspect.app.pdf;

import com.homeinspect.app.document.ReportDocument;
import com.homeinspect.app.document.ReportDocumentService;
import com.homeinspect.app.photo.PhotoService;
import com.homeinspect.app.photo.PhotoService.Variant;
import com.homeinspect.app.report.Rating;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.settings.CompanySettingsService;
import com.homeinspect.app.user.SignatureService;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * PDF of a report: the print template {@code templates/pdf/report.html} rendered with Thymeleaf
 * and converted with OpenHTMLtoPDF. Images are read from disk (optimized versions, so the file
 * stays small) and the DejaVu fonts are embedded when available.
 */
@Slf4j
@Service
public class PdfService {

    static final String FONT_FAMILY = "DejaVu Sans";
    static final String MONO_FAMILY = "DejaVu Sans Mono";

    private final ReportDocumentService documents;
    private final PhotoService photos;
    private final SignatureService signatures;
    private final CompanySettingsService company;
    private final ITemplateEngine templates;
    private final Path fontDir;

    PdfService(ReportDocumentService documents, PhotoService photos, SignatureService signatures,
               CompanySettingsService company, ITemplateEngine templates,
               @Value("${home-inspect.pdf.font-dir:}") Path fontDir) {
        this.documents = documents;
        this.photos = photos;
        this.signatures = signatures;
        this.company = company;
        this.templates = templates;
        this.fontDir = fontDir;
    }

    public record Pdf(String fileName, byte[] content) {
    }

    public Pdf render(long reportId, CurrentUser user) {
        ReportDocument doc = documents.build(reportId, user); // checks access
        Map<String, Object> model = new HashMap<>();
        model.put("doc", doc);
        model.put("ratings", Rating.values());
        model.put("photoSrc", uris(photos.files(reportId, Variant.FULL)));
        model.put("thumbSrc", uris(photos.files(reportId, Variant.THUMB)));
        model.put("signatureSrc", signatures.file(doc.inspector().profileId()).map(PdfService::uri).orElse(null));
        model.put("logoSrc", company.logoFile().map(PdfService::uri).orElse(null));
        String html = templates.process("pdf/report", new Context(LocaleContextHolder.getLocale(), model));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withHtmlContent(html, fontDir == null ? null : fontDir.toUri().toString());
            builder.toStream(out);
            builder.run();
            return new Pdf(fileName(doc), out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot generate the PDF of report " + reportId, e);
        }
    }

    /** {@code informe-2026-0001-calle-mayor-1.pdf}. */
    static String fileName(ReportDocument doc) {
        String street = Normalizer.normalize(doc.property().address(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (street.length() > 50) {
            street = street.substring(0, 50).replaceAll("-+$", "");
        }
        return "informe-" + doc.number() + (street.isEmpty() ? "" : "-" + street) + ".pdf";
    }

    private void registerFonts(PdfRendererBuilder builder) {
        if (fontDir == null || !Files.isDirectory(fontDir)) {
            return; // the template falls back to Helvetica
        }
        font(builder, FONT_FAMILY, "DejaVuSans.ttf", 400, FontStyle.NORMAL);
        font(builder, FONT_FAMILY, "DejaVuSans-Bold.ttf", 700, FontStyle.NORMAL);
        font(builder, FONT_FAMILY, "DejaVuSans-Oblique.ttf", 400, FontStyle.ITALIC);
        font(builder, FONT_FAMILY, "DejaVuSans-BoldOblique.ttf", 700, FontStyle.ITALIC);
        font(builder, MONO_FAMILY, "DejaVuSansMono.ttf", 400, FontStyle.NORMAL);
        font(builder, MONO_FAMILY, "DejaVuSansMono-Bold.ttf", 700, FontStyle.NORMAL);
    }

    private void font(PdfRendererBuilder builder, String family, String file, int weight, FontStyle style) {
        Path path = fontDir.resolve(file);
        if (Files.isRegularFile(path)) {
            builder.useFont(path.toFile(), family, weight, style, true);
        }
    }

    private static Map<Long, String> uris(Map<Long, Path> files) {
        Map<Long, String> result = new HashMap<>();
        files.forEach((id, path) -> result.put(id, uri(path)));
        return result;
    }

    private static String uri(Path path) {
        return path.toAbsolutePath().toUri().toString();
    }
}
