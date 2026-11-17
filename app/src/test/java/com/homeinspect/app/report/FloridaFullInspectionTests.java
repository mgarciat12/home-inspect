package com.homeinspect.app.report;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.catalog.CatalogService;
import com.homeinspect.app.document.ReportDocument;
import com.homeinspect.app.document.ReportDocumentService;
import com.homeinspect.app.report.dto.ItemUpdate;
import com.homeinspect.app.report.dto.ReportEditorView;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.homeinspect.app.report.ReportFixtures.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;

/** Full inspection under the Florida Standards of Practice and section 468.8323, F.S. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestUsers.class})
class FloridaFullInspectionTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestUsers users;
    @Autowired
    ReportService reports;
    @Autowired
    ReportDocumentService documents;
    @Autowired
    CatalogService catalog;

    @Test
    void catalogFollowsTheFloridaRules() {
        assertThat(catalog.activeSections(ReportType.FULL)).extracting(s -> s.getCode())
                .containsExactly("roof", "exterior", "site", "structure", "electrical", "hvac", "plumbing", "interior");
        assertThat(catalog.activeSections(ReportType.FULL)).filteredOn(s -> s.isOptional())
                .extracting(s -> s.getCode()).containsExactly("roof", "exterior", "site");
        assertThat(catalog.commonLimitations()).contains("Excluded by written agreement with the client")
                .noneMatch(l -> l.equals("Nieve o hielo"));
    }

    @Test
    void notInspectedItemsNeedAReasonBeforeFinalizing() throws Exception {
        CurrentUser user = users.current("fl.reason", Role.INSPECTOR);
        long id = reports.create(header("10 Reason St"), user);
        ReportEditorView plumbing = reports.editor(id, "plumbing", user);
        long pumps = plumbing.current().items().get(6).id();
        reports.updateItem(id, pumps, new ItemUpdate(ItemStatus.NOT_INSPECTED, null, List.of(), null, List.of(),
                null, null, null, null), user);

        assertThatThrownBy(() -> reports.finalizeReport(id, user))
                .isInstanceOfSatisfying(ReportIncompleteException.class,
                        e -> assertThat(e.problems()).containsExactly(new ReportIncompleteException.Problem(
                                "7.7 Drainage sumps, sump pumps and related piping", "report.problem.reason")));
        mvc.perform(post("/reports/{id}/finalize", id).with(users.login("fl.reason", Role.INSPECTOR)).with(csrf()))
                .andExpect(flash().attribute("error", containsString("7.7 Drainage sumps")));

        reports.updateItem(id, pumps, new ItemUpdate(ItemStatus.NOT_INSPECTED, null, List.of(), null,
                List.of("No access"), "Pit covered by stored items", null, null, null), user);
        ReportDocument doc = documents.build(id, user);
        assertThat(doc.notInspected()).singleElement().satisfies(n -> {
            assertThat(n.label()).isEqualTo("7.7 Plumbing System › Drainage sumps, sump pumps and related piping");
            assertThat(n.reasons()).containsExactly("No access", "Pit covered by stored items");
        });
        reports.finalizeReport(id, user);
    }

    @Test
    void nearEndOfServiceLifeIsAFindingBeforeMaintenance() {
        CurrentUser user = users.current("fl.eol", Role.INSPECTOR);
        long id = reports.create(header("20 Service Life Rd"), user);
        ReportEditorView hvac = reports.editor(id, "hvac", user);
        rate(id, hvac.current().items().get(1).id(), Rating.END_OF_LIFE, user);
        rate(id, hvac.current().items().get(6).id(), Rating.MAINTENANCE, user);
        rate(id, hvac.current().items().get(3).id(), Rating.SPECIALIST, user);

        assertThat(documents.build(id, user).findings()).extracting(ReportDocument.FindingGroup::rating)
                .containsExactly(Rating.SPECIALIST, Rating.END_OF_LIFE, Rating.MAINTENANCE);
    }

    private void rate(long id, long itemId, Rating rating, CurrentUser user) {
        reports.updateItem(id, itemId, new ItemUpdate(ItemStatus.INSPECTED, rating, List.of(), null, List.of(),
                null, "Observation", Recommendation.MONITOR, null), user);
    }
}
