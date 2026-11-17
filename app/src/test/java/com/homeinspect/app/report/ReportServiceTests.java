package com.homeinspect.app.report;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.report.dto.ItemUpdate;
import com.homeinspect.app.report.dto.ReportEditorView;
import com.homeinspect.app.report.dto.ReportEditorView.ItemView;
import com.homeinspect.app.report.dto.ReportFilter;
import com.homeinspect.app.report.dto.SaveResult;
import com.homeinspect.app.report.dto.SectionUpdate;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;

import static com.homeinspect.app.report.ReportFixtures.header;
import static com.homeinspect.app.report.ReportFixtures.rated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import({TestcontainersConfiguration.class, TestUsers.class})
class ReportServiceTests {

    @Autowired
    ReportService reports;
    @Autowired
    TestUsers users;

    @Test
    void newReportFollowsTheCatalogAndGetsAYearlyNumber() {
        CurrentUser inspector = users.current("rs.create", Role.INSPECTOR);
        long id = reports.create(header("Calle Mayor 1"), inspector);
        long second = reports.create(header("Calle Mayor 2"), inspector);

        ReportEditorView editor = reports.editor(id, null, inspector);
        assertThat(editor.report().number()).matches("2026-\\d{4}");
        assertThat(reports.number(second, inspector)).isNotEqualTo(editor.report().number());
        assertThat(editor.sections()).hasSize(8);
        assertThat(editor.sections().getFirst().title()).isEqualTo("Roof Covering");
        assertThat(editor.sections().getLast().title()).isEqualTo("Interior Components");
        assertThat(editor.current().methods()).contains("With a drone");
        assertThat(editor.current().items()).extracting(ItemView::reference).startsWith("1.1", "1.2");
        ItemView covering = editor.current().items().getFirst();
        assertThat(covering.title()).isEqualTo("Roof covering");
        assertThat(covering.materialOptions()).contains("Concrete tile", "Metal - 5V crimp");
        assertThat(covering.limitationOptions()).contains("Stored items", "Excluded by written agreement with the client");
        assertThat(covering.comments()).isNotEmpty();
        assertThat(editor.report().completed()).isZero();
        assertThat(editor.editable()).isTrue();
    }

    @Test
    void autosaveUpdatesAnItemAndItsProgress() {
        CurrentUser inspector = users.current("rs.autosave", Role.INSPECTOR);
        long id = reports.create(header("Avenida Sol 3"), inspector);
        ItemView first = reports.editor(id, "roof", inspector).current().items().getFirst();

        SaveResult result = reports.updateItem(id, first.id(), rated(Rating.REPAIR, "  Tejas rotas  "), inspector);
        assertThat(result.itemCompleted()).isTrue();
        assertThat(result.sectionCompleted()).isEqualTo(1);
        assertThat(result.sectionTotal()).isEqualTo(7);

        ItemView saved = reports.editor(id, "roof", inspector).current().items().getFirst();
        assertThat(saved.rating()).isEqualTo(Rating.REPAIR);
        assertThat(saved.observations()).isEqualTo("Tejas rotas");
        assertThat(saved.materials()).containsExactly("Concrete tile");
        assertThat(saved.limitations()).containsExactly("Roof slope / height unsafe");

        // A rating is dropped when the element is not inspected
        reports.updateItem(id, first.id(), new ItemUpdate(ItemStatus.NOT_PRESENT, Rating.REPAIR, List.of(), null,
                List.of(), null, null, null, null), inspector);
        ItemView notPresent = reports.editor(id, "roof", inspector).current().items().getFirst();
        assertThat(notPresent.rating()).isNull();
        assertThat(notPresent.completed()).isTrue();
    }

    @Test
    void sectionGeneralFieldsAndNotApplicableOnlyForOptionalSections() {
        CurrentUser inspector = users.current("rs.section", Role.INSPECTOR);
        long id = reports.create(header("Plaza Luna 4"), inspector);
        // Condominium unit: the roof is a common element maintained by the association
        ReportEditorView roof = reports.editor(id, "roof", inspector);
        reports.updateSection(id, roof.current().id(), new SectionUpdate(true, null, "Common element", null), inspector);
        ReportEditorView structure = reports.editor(id, "structure", inspector);
        reports.updateSection(id, structure.current().id(), new SectionUpdate(true, "Attic entered", null, "Low clearance"), inspector);

        assertThat(reports.editor(id, "roof", inspector).current().notApplicable()).isTrue();
        ReportEditorView structureAfter = reports.editor(id, "structure", inspector);
        assertThat(structureAfter.current().notApplicable()).isFalse();
        assertThat(structureAfter.current().inspectionMethod()).isEqualTo("Attic entered");
        assertThat(structureAfter.current().generalLimitations()).isEqualTo("Low clearance");
        // The not-applicable roof does not count in the global progress
        assertThat(structureAfter.report().total()).isEqualTo(structureAfter.sections().stream()
                .filter(s -> !s.notApplicable()).mapToInt(ReportEditorView.SectionNav::total).sum());
    }

    @Test
    void inspectorsOnlySeeAndEditTheirOwnReports() {
        CurrentUser owner = users.current("rs.owner", Role.INSPECTOR);
        CurrentUser other = users.current("rs.other", Role.INSPECTOR);
        CurrentUser admin = users.current("rs.admin", Role.ADMIN);
        long id = reports.create(header("Calle Privada 5"), owner);
        long itemId = reports.editor(id, null, owner).current().items().getFirst().id();

        assertThatThrownBy(() -> reports.editor(id, null, other)).isInstanceOf(ReportNotFoundException.class);
        assertThatThrownBy(() -> reports.headerForm(id, other)).isInstanceOf(ReportNotFoundException.class);
        assertThatThrownBy(() -> reports.updateItem(id, itemId, rated(Rating.SATISFACTORY, "x"), other))
                .isInstanceOf(ReportNotFoundException.class);
        assertThatThrownBy(() -> reports.duplicate(id, other)).isInstanceOf(ReportNotFoundException.class);
        assertThatThrownBy(() -> reports.delete(id, other)).isInstanceOf(ReportNotFoundException.class);
        assertThat(reports.search(ReportFilter.empty(), PageRequest.of(0, 50), other).getContent())
                .noneMatch(row -> row.id() == id);

        // The admin sees every report
        assertThat(reports.editor(id, null, admin).report().address()).isEqualTo("Calle Privada 5");
        assertThat(reports.search(ReportFilter.empty(), PageRequest.of(0, 500), admin).getContent())
                .anyMatch(row -> row.id() == id);
    }

    @Test
    void itemIdsFromAnotherReportAreRejected() {
        CurrentUser inspector = users.current("rs.mixed", Role.INSPECTOR);
        long first = reports.create(header("Uno"), inspector);
        long second = reports.create(header("Dos"), inspector);
        long itemOfFirst = reports.editor(first, null, inspector).current().items().getFirst().id();
        assertThatThrownBy(() -> reports.updateItem(second, itemOfFirst, rated(Rating.REPAIR, "x"), inspector))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void finalReportsAreLockedUntilAnAdminReopensThem() {
        CurrentUser inspector = users.current("rs.final", Role.INSPECTOR);
        CurrentUser admin = users.current("rs.final.admin", Role.ADMIN);
        long id = reports.create(header("Camino Final 6"), inspector);
        long itemId = reports.editor(id, null, inspector).current().items().getFirst().id();
        reports.finalizeReport(id, inspector);

        ReportEditorView locked = reports.editor(id, null, inspector);
        assertThat(locked.editable()).isFalse();
        assertThat(locked.canReopen()).isFalse();
        assertThatThrownBy(() -> reports.updateItem(id, itemId, rated(Rating.REPAIR, "x"), inspector))
                .isInstanceOf(ReportLockedException.class);
        assertThatThrownBy(() -> reports.updateHeader(id, header("Otra"), inspector))
                .isInstanceOf(ReportLockedException.class);
        assertThatThrownBy(() -> reports.delete(id, inspector)).isInstanceOf(ReportLockedException.class);
        assertThatThrownBy(() -> reports.reopen(id, inspector)).isInstanceOf(AccessDeniedException.class);

        assertThat(reports.editor(id, null, admin).canReopen()).isTrue();
        reports.reopen(id, admin);
        assertThat(reports.editor(id, null, inspector).editable()).isTrue();
    }

    @Test
    void duplicateCopiesTheContentButNotTheClient() {
        CurrentUser inspector = users.current("rs.dup", Role.INSPECTOR);
        long id = reports.create(header("Origen 7"), inspector);
        long itemId = reports.editor(id, null, inspector).current().items().getFirst().id();
        reports.updateItem(id, itemId, rated(Rating.MAINTENANCE, "Limpiar canalones"), inspector);
        reports.finalizeReport(id, inspector);

        long copy = reports.duplicate(id, inspector);
        ReportEditorView view = reports.editor(copy, null, inspector);
        assertThat(view.report().status()).isEqualTo(ReportStatus.DRAFT);
        assertThat(view.report().clientName()).isNull();
        assertThat(view.report().number()).isNotEqualTo(reports.number(id, inspector));
        assertThat(view.current().items().getFirst().observations()).isEqualTo("Limpiar canalones");
        assertThat(view.current().items().getFirst().rating()).isEqualTo(Rating.MAINTENANCE);
    }

    @Test
    void searchByTextStatusAndDate() {
        CurrentUser inspector = users.current("rs.search", Role.INSPECTOR);
        long id = reports.create(header("Calle Búsqueda Única 8"), inspector);
        reports.create(header("Otra calle 9"), inspector);
        String number = reports.number(id, inspector);

        assertThat(reports.search(new ReportFilter("búsqueda única", null, null, null, null), PageRequest.of(0, 10), inspector)
                .getContent()).extracting(r -> r.id()).containsExactly(id);
        assertThat(reports.search(new ReportFilter(number, null, null, null, null), PageRequest.of(0, 10), inspector)
                .getContent()).extracting(r -> r.id()).containsExactly(id);
        assertThat(reports.search(new ReportFilter("CLIENTE DE CALLE BÚSQUEDA", null, null, null, null), PageRequest.of(0, 10), inspector)
                .getTotalElements()).isEqualTo(1);
        assertThat(reports.search(new ReportFilter("Única", null, ReportStatus.FINAL, null, null), PageRequest.of(0, 10), inspector)
                .getTotalElements()).isZero();
        assertThat(reports.search(new ReportFilter("Única", null, null, LocalDate.of(2026, 3, 14), LocalDate.of(2026, 3, 14)),
                PageRequest.of(0, 10), inspector).getTotalElements()).isEqualTo(1);
        assertThat(reports.search(new ReportFilter("Única", null, null, LocalDate.of(2026, 3, 15), null),
                PageRequest.of(0, 10), inspector).getTotalElements()).isZero();
        assertThat(reports.search(new ReportFilter("100%_", null, null, null, null), PageRequest.of(0, 10), inspector)
                .getTotalElements()).isZero();
    }
}
