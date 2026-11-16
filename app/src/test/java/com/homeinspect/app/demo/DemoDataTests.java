package com.homeinspect.app.demo;

import com.homeinspect.app.TestUsers;
import com.homeinspect.app.TestcontainersConfiguration;
import com.homeinspect.app.document.ReportDocument;
import com.homeinspect.app.document.ReportDocumentService;
import com.homeinspect.app.report.ReportService;
import com.homeinspect.app.report.dto.ReportFilter;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("demo")
@Import({TestcontainersConfiguration.class, TestUsers.class})
class DemoDataTests {

    @Autowired
    ReportService reports;
    @Autowired
    ReportDocumentService documents;
    @Autowired
    TestUsers users;

    @Test
    void demoProfileCreatesASampleReportVisibleToAdmins() {
        CurrentUser admin = users.current("demo.admin", Role.ADMIN);
        var rows = reports.search(ReportFilter.empty(), PageRequest.of(0, 10), admin).getContent();
        assertThat(rows).hasSize(1);
        ReportDocument doc = documents.build(rows.getFirst().id(), admin);
        assertThat(doc.cover()).isNotNull();
        assertThat(doc.findingCount()).isEqualTo(5);
        assertThat(doc.sections().getFirst().items().get(1).photos()).hasSize(2);
        assertThat(doc.sections().getLast().notApplicable()).isTrue();
    }
}
