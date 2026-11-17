package com.homeinspect.app.report;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.catalog.CatalogService;
import com.homeinspect.app.report.dto.ReportEditorView;
import com.homeinspect.app.report.dto.ReportFilter;
import com.homeinspect.app.report.dto.ReportHeaderForm;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static com.homeinspect.app.report.ReportFixtures.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Report types (Florida): numbering per type, available types and the US header fields. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class ReportTypeTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestUsers users;
    @Autowired
    ReportService reports;
    @Autowired
    ReportNumberGenerator numbers;
    @Autowired
    CatalogService catalog;
    @Autowired
    ReportRepository repository;

    @Test
    void eachTypeHasItsOwnNumbering() {
        String full = numbers.next(ReportType.FULL, 2031);
        String fourPoint = numbers.next(ReportType.FOUR_POINT, 2031);
        String wind = numbers.next(ReportType.WIND_MITIGATION, 2031);
        assertThat(full).isEqualTo("2031-0001");
        assertThat(fourPoint).isEqualTo("4P-2031-0001");
        assertThat(wind).isEqualTo("WM-2031-0001");
        assertThat(numbers.next(ReportType.FOUR_POINT, 2031)).isEqualTo("4P-2031-0002");
        assertThat(numbers.next(ReportType.FULL, 2031)).isEqualTo("2031-0002");
    }

    @Test
    void unavailableTypesAreRejected() throws Exception {
        // Every type has a catalog now: a type without one would be shown as "coming soon" and rejected
        assertThat(catalog.availableTypes()).containsExactlyInAnyOrder(ReportType.values());
        var login = users.login("type.web", Role.INSPECTOR);
        mvc.perform(get("/reports/new").with(login))
                .andExpect(content().string(containsString("Full home inspection")))
                .andExpect(content().string(containsString("Wind Mitigation")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Coming soon"))));
        mvc.perform(post("/reports").with(login).with(csrf())
                        .param("type", "UNKNOWN")
                        .param("inspectedAt", "2026-05-02T09:15")
                        .param("address", "1 Four Point Rd"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Please check the highlighted fields")));
    }

    @Test
    void floridaHeaderFieldsAndTypeAreStored() {
        CurrentUser user = users.current("type.header", Role.INSPECTOR);
        ReportHeaderForm form = header("100 Ocean Dr");
        form.setCity("Miami Beach");
        form.setCounty("Miami-Dade");
        form.setState("FL");
        form.setPostalCode("33139");
        form.setInsuranceCompany("Citizens Property Insurance");
        form.setPolicyNumber("PR-123456");
        form.setAreaSqft(2150);
        form.setTemperatureF(new BigDecimal("88"));
        long id = reports.create(form, user);

        Report report = repository.findById(id).orElseThrow();
        assertThat(report.getType()).isEqualTo(ReportType.FULL);
        assertThat(report.getNumber()).matches("\\d{4}-\\d{4}");

        ReportHeaderForm stored = reports.headerForm(id, user);
        assertThat(stored.getCounty()).isEqualTo("Miami-Dade");
        assertThat(stored.getState()).isEqualTo("FL");
        assertThat(stored.getInsuranceCompany()).isEqualTo("Citizens Property Insurance");
        assertThat(stored.getPolicyNumber()).isEqualTo("PR-123456");
        assertThat(stored.getAreaSqft()).isEqualTo(2150);
        assertThat(stored.getTemperatureF()).isEqualByComparingTo("88");

        // The type cannot be changed by editing the header
        stored.setType(ReportType.WIND_MITIGATION);
        reports.updateHeader(id, stored, user);
        assertThat(repository.findById(id).orElseThrow().getType()).isEqualTo(ReportType.FULL);
    }

    @Test
    void oneVisitCreatesSeveralReportsThatShareTheHeader() {
        CurrentUser user = users.current("type.visit", Role.INSPECTOR);
        ReportHeaderForm form = header("77 Visit Ln");
        form.setAlsoCreate(java.util.EnumSet.of(ReportType.FOUR_POINT, ReportType.WIND_MITIGATION, ReportType.FULL));
        long id = reports.create(form, user);

        var visit = reports.visit(id, user);
        assertThat(visit).extracting(ReportEditorView.Sibling::type)
                .containsExactly(ReportType.FOUR_POINT, ReportType.WIND_MITIGATION);
        assertThat(visit.getFirst().number()).startsWith("4P-");
        long fourPoint = visit.getFirst().id();
        long wind = visit.get(1).id();
        assertThat(reports.visit(fourPoint, user)).extracting(ReportEditorView.Sibling::id).containsExactly(id, wind);

        // Header changes go to the drafts of the same visit, but not to a final report
        Report finalWind = repository.findById(wind).orElseThrow();
        finalWind.setStatus(ReportStatus.FINAL);
        repository.save(finalWind);
        ReportHeaderForm changed = reports.headerForm(id, user);
        changed.setPolicyNumber("NEW-POLICY-1");
        reports.updateHeader(id, changed, user);
        assertThat(reports.headerForm(fourPoint, user).getPolicyNumber()).isEqualTo("NEW-POLICY-1");
        assertThat(reports.headerForm(wind, user).getPolicyNumber()).isNull();
        assertThat(repository.findById(fourPoint).orElseThrow().getType()).isEqualTo(ReportType.FOUR_POINT);

        // Other users do not see the visit
        assertThatThrownBy(() -> reports.visit(id, users.current("type.visit.other", Role.INSPECTOR)))
                .isInstanceOf(ReportNotFoundException.class);
        assertThat(reports.visit(reports.create(header("Single St"), user), user)).isEmpty();
    }

    @Test
    void listCanBeFilteredByType() {
        CurrentUser user = users.current("type.filter", Role.INSPECTOR);
        reports.create(header("Filter Ave 1"), user);
        assertThat(reports.search(new ReportFilter("Filter Ave", ReportType.FULL, null, null, null), PageRequest.of(0, 10), user)
                .getTotalElements()).isEqualTo(1);
        assertThat(reports.search(new ReportFilter("Filter Ave", ReportType.FOUR_POINT, null, null, null), PageRequest.of(0, 10), user)
                .getTotalElements()).isZero();
    }
}
