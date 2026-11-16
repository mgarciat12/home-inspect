package com.homeinspect.app.demo;

import com.homeinspect.app.photo.PhotoService;
import com.homeinspect.app.photo.PhotoView;
import com.homeinspect.app.report.ItemStatus;
import com.homeinspect.app.report.Occupancy;
import com.homeinspect.app.report.PropertyType;
import com.homeinspect.app.report.Rating;
import com.homeinspect.app.report.Recommendation;
import com.homeinspect.app.report.ReportRepository;
import com.homeinspect.app.report.ReportService;
import com.homeinspect.app.report.SoilCondition;
import com.homeinspect.app.report.Weather;
import com.homeinspect.app.report.dto.ItemUpdate;
import com.homeinspect.app.report.dto.ReportEditorView;
import com.homeinspect.app.report.dto.ReportHeaderForm;
import com.homeinspect.app.report.dto.SectionUpdate;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import com.homeinspect.app.user.UserProfile;
import com.homeinspect.app.user.UserProfileService;
import com.homeinspect.app.user.UserProfileService.LoginData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Profile {@code demo}: when there are no reports yet, creates a sample report (owned by a demo
 * inspector, visible to administrators) with ratings, observations and generated test photos.
 */
@Slf4j
@Component
@Profile("demo")
@Order(20)
@RequiredArgsConstructor
class DemoDataLoader implements ApplicationRunner {

    private final ReportRepository reportRepository;
    private final ReportService reports;
    private final PhotoService photos;
    private final UserProfileService profiles;

    @Override
    public void run(ApplicationArguments args) {
        if (reportRepository.count() > 0) {
            return;
        }
        UserProfile profile = profiles.syncFromLogin(new LoginData("demo-inspector", "demo",
                "Inspector de demostración", "demo@example.com", Role.INSPECTOR));
        CurrentUser inspector = new CurrentUser(profile.getId(), profile.getSubject(), profile.getDisplayName(), Role.INSPECTOR);

        ReportHeaderForm header = new ReportHeaderForm();
        header.setInspectedAt(LocalDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0));
        header.setClientName("Cliente de demostración");
        header.setClientEmail("cliente@example.com");
        header.setClientPhone("600 000 000");
        header.setAddress("Calle Ejemplo 12, 2º A");
        header.setPostalCode("28001");
        header.setCity("Madrid");
        header.setProvince("Madrid");
        header.setPropertyType(PropertyType.APARTMENT);
        header.setYearBuilt(1978);
        header.setAreaM2(new BigDecimal("94"));
        header.setFloors(1);
        header.setOccupancy(Occupancy.OWNER);
        header.setPeoplePresent("Cliente y agente inmobiliario");
        header.setWeather(Weather.PARTLY_CLOUDY);
        header.setTemperatureC(new BigDecimal("16"));
        header.setSoilCondition(SoilCondition.DRY);
        long id = reports.create(header, inspector);

        ReportEditorView roof = reports.editor(id, "roof", inspector);
        reports.updateSection(id, roof.current().id(), new SectionUpdate(false, "Con dron",
                "Cubierta inclinada a dos aguas en estado general aceptable para su antigüedad.", null), inspector);
        rate(id, roof, 0, Rating.MAINTENANCE, List.of("Teja cerámica"),
                "Presencia de musgo y suciedad acumulada en la cubierta; se recomienda limpieza periódica.",
                Recommendation.MONITOR, inspector, color(196, 92, 60));
        rate(id, roof, 1, Rating.SAFETY_HAZARD, List.of("Zinc"),
                "Canalón desprendido sobre la puerta de entrada. Riesgo de caída de elementos sobre las personas.",
                Recommendation.REPAIR, inspector, color(120, 120, 130), color(90, 90, 100));
        rate(id, roof, 2, Rating.SATISFACTORY, List.of("Zinc"), null, null, inspector);

        ReportEditorView exterior = reports.editor(id, "exterior", inspector);
        rate(id, exterior, 0, Rating.REPAIR, List.of("Revoco o mortero monocapa"),
                "Fisuras en el revestimiento de fachada junto a la ventana del dormitorio.",
                Recommendation.CONSULT_PROFESSIONAL, inspector, color(220, 205, 170));
        rate(id, exterior, 5, Rating.SATISFACTORY, List.of("Pendiente hacia fuera de la vivienda"), null, null, inspector);

        ReportEditorView electrical = reports.editor(id, "electrical", inspector);
        rate(id, electrical, 2, Rating.SAFETY_HAZARD, List.of("Fusibles"),
                "No existe protección diferencial. Se recomienda su instalación por un instalador autorizado.",
                Recommendation.CONSULT_PROFESSIONAL, inspector, color(60, 60, 70));
        rate(id, electrical, 5, Rating.SPECIALIST, List.of("Ninguno"),
                "No se han observado detectores de humo ni de monóxido de carbono.", Recommendation.NONE, inspector);

        ReportEditorView garage = reports.editor(id, "garage", inspector);
        reports.updateSection(id, garage.current().id(), new SectionUpdate(true, null, "La vivienda no dispone de garaje.", null), inspector);

        // First photo of the roof as cover
        List<PhotoView> roofPhotos = reports.editor(id, "roof", inspector).current().items().getFirst().photos();
        if (!roofPhotos.isEmpty()) {
            photos.toggleCover(id, roofPhotos.getFirst().id(), inspector);
        }
        log.info("Demo report created (id {})", id);
    }

    private void rate(long reportId, ReportEditorView section, int index, Rating rating, List<String> materials,
                      String observations, Recommendation recommendation, CurrentUser user, Color... photoColors) {
        long itemId = section.current().items().get(index).id();
        reports.updateItem(reportId, itemId, new ItemUpdate(ItemStatus.INSPECTED, rating, materials, null, List.of(),
                null, observations, recommendation, null), user);
        for (int i = 0; i < photoColors.length; i++) {
            photos.upload(reportId, itemId, List.of(new BytesFile("demo-" + itemId + "-" + i + ".jpg",
                    sketch(photoColors[i], i))), user);
        }
    }

    private static Color color(int r, int g, int b) {
        return new Color(r, g, b);
    }

    /** Simple drawing of a house front, so the demo photos are not blank. */
    private static byte[] sketch(Color accent, int variant) {
        int w = 1600;
        int h = 1200;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(150, 195, 235), 0, h / 2f, new Color(225, 238, 248)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(110, 150, 90));
        g.fillRect(0, h * 3 / 4, w, h / 4);
        g.setColor(new Color(235, 225, 205));
        g.fillRect(400, 520, 800, 380);
        g.setColor(accent);
        g.fillPolygon(new int[]{340, 800, 1260}, new int[]{540, 260 + variant * 30, 540}, 3);
        g.setColor(new Color(90, 120, 150));
        g.fillRect(500, 620, 160, 140);
        g.fillRect(940, 620, 160, 140);
        g.setColor(new Color(120, 80, 50));
        g.fillRect(740, 700, 120, 200);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** In-memory MultipartFile for the generated photos. */
    private record BytesFile(String name, byte[] bytes) implements MultipartFile {
        @Override public String getName() { return "files"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "image/jpeg"; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException { Files.write(dest.toPath(), bytes); }
        @Override public void transferTo(Path dest) throws IOException { Files.write(dest, bytes); }
    }
}
