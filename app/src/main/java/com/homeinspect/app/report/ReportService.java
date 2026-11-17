package com.homeinspect.app.report;

import com.homeinspect.app.catalog.CatalogService;
import com.homeinspect.app.catalog.FieldType;
import com.homeinspect.app.catalog.ItemDef;
import com.homeinspect.app.catalog.SectionDef;
import com.homeinspect.app.photo.PhotoRepository;
import com.homeinspect.app.report.dto.ItemUpdate;
import com.homeinspect.app.report.dto.ReportEditorView;
import com.homeinspect.app.report.dto.ReportFilter;
import com.homeinspect.app.report.dto.ReportHeaderForm;
import com.homeinspect.app.report.dto.ReportListRow;
import com.homeinspect.app.report.dto.SaveResult;
import com.homeinspect.app.report.dto.SectionUpdate;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.user.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reports: creation from the catalog, list, header, autosave of elements and sections,
 * finalization, duplication and deletion. Every method checks access with {@link ReportAccessGuard}.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reports;
    private final ReportItemRepository items;
    private final ReportSectionRepository sections;
    private final UserProfileRepository profiles;
    private final CatalogService catalog;
    private final ReportNumberGenerator numbers;
    private final PhotoRepository photos;
    private final ReportAccessGuard guard;
    private final ReportEditorMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Published before a report is deleted, so that its photos are removed from disk. */
    public record ReportDeleted(long reportId) {
    }

    // ---- list ----

    @Transactional(readOnly = true)
    public Page<ReportListRow> search(ReportFilter filter, Pageable pageable, CurrentUser user) {
        ZoneId zone = clock.getZone();
        Specification<Report> spec = Specification.allOf(
                ReportSpecifications.inspector(user.isAdmin() ? null : user.profileId()),
                ReportSpecifications.type(filter.type()),
                ReportSpecifications.status(filter.status()),
                ReportSpecifications.inspectedFrom(filter.from() == null ? null : filter.from().atStartOfDay(zone).toInstant()),
                ReportSpecifications.inspectedBefore(filter.to() == null ? null : filter.to().plusDays(1).atStartOfDay(zone).toInstant()),
                ReportSpecifications.text(filter.q()));
        return reports.findAll(spec, pageable).map(report -> new ReportListRow(report.getId(), report.getNumber(), report.getType(),
                report.getAddress(), report.getCity(), report.getClientName(),
                report.getInspector().getDisplayName(), report.getInspectedAt(), report.getStatus()));
    }

    // ---- creation and header ----

    @Transactional
    public long create(ReportHeaderForm form, CurrentUser user) {
        Set<ReportType> available = catalog.availableTypes();
        if (!available.contains(form.getType())) {
            throw new IllegalArgumentException("Report type not available yet: " + form.getType());
        }
        List<ReportType> others = form.getAlsoCreate() == null ? List.of() : form.getAlsoCreate().stream()
                .filter(t -> t != form.getType() && available.contains(t)).sorted().toList();
        UUID visitId = others.isEmpty() ? null : UUID.randomUUID();
        long id = createOne(form.getType(), form, visitId, user);
        for (ReportType other : others) {
            createOne(other, form, visitId, user);
        }
        return id;
    }

    private long createOne(ReportType type, ReportHeaderForm form, UUID visitId, CurrentUser user) {
        Report report = new Report();
        report.setVisitId(visitId);
        report.setInspector(profiles.getReferenceById(user.profileId()));
        report.setType(type);
        applyHeader(report, form);
        report.setNumber(numbers.next(report.getType(),
                LocalDateTime.ofInstant(report.getInspectedAt(), clock.getZone()).getYear()));
        for (SectionDef sectionDef : catalog.activeSections(report.getType())) {
            ReportSection section = new ReportSection(sectionDef);
            catalog.activeItems(sectionDef).forEach(itemDef -> section.addItem(new ReportItem(itemDef)));
            report.addSection(section);
        }
        return reports.save(report).getId();
    }

    @Transactional(readOnly = true)
    public ReportHeaderForm headerForm(long reportId, CurrentUser user) {
        Report report = load(reportId);
        guard.checkView(report, user);
        ReportHeaderForm form = new ReportHeaderForm();
        form.setType(report.getType());
        form.setInspectedAt(LocalDateTime.ofInstant(report.getInspectedAt(), clock.getZone()));
        form.setClientName(report.getClientName());
        form.setClientEmail(report.getClientEmail());
        form.setClientPhone(report.getClientPhone());
        form.setInsuranceCompany(report.getInsuranceCompany());
        form.setPolicyNumber(report.getPolicyNumber());
        form.setAddress(report.getAddress());
        form.setPostalCode(report.getPostalCode());
        form.setCity(report.getCity());
        form.setCounty(report.getCounty());
        form.setState(report.getState());
        form.setPropertyType(report.getPropertyType());
        form.setYearBuilt(report.getYearBuilt());
        form.setAreaSqft(report.getAreaSqft());
        form.setFloors(report.getFloors());
        form.setOccupancy(report.getOccupancy());
        form.setPeoplePresent(report.getPeoplePresent());
        form.setWeather(report.getWeather());
        form.setTemperatureF(report.getTemperatureF());
        form.setSoilCondition(report.getSoilCondition());
        return form;
    }

    @Transactional
    public void updateHeader(long reportId, ReportHeaderForm form, CurrentUser user) {
        Report report = load(reportId);
        guard.checkEdit(report, user);
        applyHeader(report, form);
        // Same visit: the other drafts the user can edit get the same header
        for (Report sibling : siblings(report)) {
            if (guard.canEdit(sibling, user)) {
                applyHeader(sibling, form);
            }
        }
    }

    /** Other reports of the same visit that the user can see (number and type, for the editor). */
    @Transactional(readOnly = true)
    public List<ReportEditorView.Sibling> visit(long reportId, CurrentUser user) {
        Report report = load(reportId);
        guard.checkView(report, user);
        return siblings(report).stream().filter(s -> guard.canView(s, user))
                .map(s -> new ReportEditorView.Sibling(s.getId(), s.getNumber(), s.getType(), s.getStatus())).toList();
    }

    private List<Report> siblings(Report report) {
        if (report.getVisitId() == null) {
            return List.of();
        }
        return reports.findByVisitIdOrderByIdAsc(report.getVisitId()).stream()
                .filter(s -> !s.getId().equals(report.getId())).toList();
    }

    /** Number of the report (for titles and file names), after checking access. */
    @Transactional(readOnly = true)
    public String number(long reportId, CurrentUser user) {
        Report report = load(reportId);
        guard.checkView(report, user);
        return report.getNumber();
    }

    // ---- editor ----

    /**
     * Data of the editor for one section ({@code sectionCode} null = first section). Draft reports
     * are completed with elements added to the catalog after their creation.
     */
    @Transactional
    public ReportEditorView editor(long reportId, String sectionCode, CurrentUser user) {
        Report report = load(reportId);
        guard.checkView(report, user);
        if (!report.isFinal()) {
            syncWithCatalog(report);
        }
        return mapper.editor(report, sectionCode, !report.isFinal(), report.isFinal() && user.isAdmin());
    }

    @Transactional
    public SaveResult updateItem(long reportId, long itemId, ItemUpdate update, CurrentUser user) {
        ReportItem item = items.findInReport(reportId, itemId).orElseThrow(ReportNotFoundException::new);
        Report report = item.getSection().getReport();
        guard.checkEdit(report, user);

        if (item.getDefinition().isQuestion()) {
            item.setAnswers(FormAnswers.clean(item.getDefinition(), update.answers()));
            item.setObservations(trimToNull(update.observations()));
            return saved(item, report);
        }
        item.setStatus(update.status());
        // A rating only makes sense for an inspected element
        item.setRating(update.status() == ItemStatus.INSPECTED ? update.rating() : null);
        item.setMaterials(clean(update.materials()));
        item.setMaterialOther(trimToNull(update.materialOther()));
        item.setLimitations(clean(update.limitations()));
        item.setLimitationText(trimToNull(update.limitationText()));
        item.setObservations(trimToNull(update.observations()));
        item.setRecommendation(update.recommendation());
        item.setRecommendationText(trimToNull(update.recommendationText()));
        return saved(item, report);
    }

    private SaveResult saved(ReportItem item, Report report) {
        Instant now = clock.instant();
        item.setUpdatedAt(now);
        report.touch();
        List<ReportItem> sectionItems = item.getSection().getItems();
        int completed = (int) sectionItems.stream().filter(ReportItem::isCompleted).count();
        return new SaveResult(now, item.isCompleted(), completed, sectionItems.size());
    }

    @Transactional
    public SaveResult updateSection(long reportId, long sectionId, SectionUpdate update, CurrentUser user) {
        ReportSection section = sections.findInReport(reportId, sectionId).orElseThrow(ReportNotFoundException::new);
        guard.checkEdit(section.getReport(), user);
        section.setNotApplicable(section.getDefinition().isOptional() && update.notApplicable());
        section.setInspectionMethod(trimToNull(update.inspectionMethod()));
        section.setGeneralComments(trimToNull(update.generalComments()));
        section.setGeneralLimitations(trimToNull(update.generalLimitations()));
        section.getReport().touch();
        int completed = (int) section.getItems().stream().filter(ReportItem::isCompleted).count();
        return new SaveResult(clock.instant(), false, completed, section.getItems().size());
    }

    // ---- lifecycle ----

    @Transactional
    public void finalizeReport(long reportId, CurrentUser user) {
        Report report = load(reportId);
        guard.checkEdit(report, user);
        List<ReportIncompleteException.Problem> problems = problems(report);
        if (!problems.isEmpty()) {
            throw new ReportIncompleteException(problems);
        }
        report.setStatus(ReportStatus.FINAL);
        report.setFinalizedAt(clock.instant());
    }

    @Transactional
    public void reopen(long reportId, CurrentUser user) {
        Report report = load(reportId);
        guard.checkReopen(report, user);
        report.setStatus(ReportStatus.DRAFT);
        report.setFinalizedAt(null);
    }

    /**
     * New draft for the current user that copies the property data and the content of every
     * section of another report (to use it as a template). Client, address, date and photos are not copied.
     */
    @Transactional
    public long duplicate(long reportId, CurrentUser user) {
        Report source = load(reportId);
        guard.checkView(source, user);

        ReportHeaderForm header = headerForm(reportId, user);
        header.setInspectedAt(LocalDateTime.now(clock).withSecond(0).withNano(0));
        header.setClientName(null);
        header.setClientEmail(null);
        header.setClientPhone(null);
        header.setPeoplePresent(null);
        header.setInsuranceCompany(null);
        header.setPolicyNumber(null);
        header.setAddress(source.getAddress() + " (copy)");
        long copyId = create(header, user);
        Report copy = load(copyId);

        for (ReportSection sourceSection : source.getSections()) {
            copy.getSections().stream()
                    .filter(s -> s.getDefinition().getId().equals(sourceSection.getDefinition().getId()))
                    .findFirst()
                    .ifPresent(target -> copySection(sourceSection, target));
        }
        return copyId;
    }

    @Transactional
    public void delete(long reportId, CurrentUser user) {
        Report report = load(reportId);
        guard.checkDelete(report, user);
        events.publishEvent(new ReportDeleted(reportId));
        reports.delete(report);
    }

    /**
     * What prevents finalizing: a present component not inspected without the reason (section 468.8323, F.S.),
     * and for the forms, required answers, explanations and photos.
     */
    private List<ReportIncompleteException.Problem> problems(Report report) {
        Set<Long> withPhotos = photos.findByReportId(report.getId()).stream()
                .map(p -> p.getItem().getId()).collect(Collectors.toSet());
        List<ReportIncompleteException.Problem> problems = new ArrayList<>();
        List<ReportSection> ordered = ReportEditorMapper.orderedSections(report);
        for (int s = 0; s < ordered.size(); s++) {
            ReportSection section = ordered.get(s);
            if (section.isNotApplicable()) {
                continue;
            }
            List<ReportItem> sectionItems = ReportEditorMapper.orderedItems(section);
            for (int i = 0; i < sectionItems.size(); i++) {
                ReportItem item = sectionItems.get(i);
                ItemDef definition = item.getDefinition();
                String label = (s + 1) + "." + (i + 1) + " " + definition.getTitle();
                if (!definition.isQuestion()) {
                    if (item.getStatus() == ItemStatus.NOT_INSPECTED && item.getLimitations().isEmpty()
                            && item.getLimitationText() == null) {
                        problems.add(new ReportIncompleteException.Problem(label, "report.problem.reason"));
                    }
                    continue;
                }
                boolean notApplicable = item.getAnswers().contains(FieldType.NOT_APPLICABLE)
                        || item.getAnswers().contains("N/A");
                if (definition.isRequired() && item.getAnswers().isEmpty()) {
                    problems.add(new ReportIncompleteException.Problem(label, "report.problem.answer"));
                }
                if (item.getObservations() == null
                        && item.getAnswers().stream().anyMatch(definition.getExplainOn()::contains)) {
                    problems.add(new ReportIncompleteException.Problem(label, "report.problem.explain"));
                }
                if (definition.isPhotoRequired() && !notApplicable && !withPhotos.contains(item.getId())) {
                    problems.add(new ReportIncompleteException.Problem(label, "report.problem.photo"));
                }
            }
        }
        return problems;
    }

    // ---- helpers ----

    private Report load(long reportId) {
        return reports.findById(reportId).orElseThrow(ReportNotFoundException::new);
    }

    private void applyHeader(Report report, ReportHeaderForm form) {
        report.setInspectedAt(form.getInspectedAt().atZone(clock.getZone()).toInstant());
        report.setClientName(trimToNull(form.getClientName()));
        report.setClientEmail(trimToNull(form.getClientEmail()));
        report.setClientPhone(trimToNull(form.getClientPhone()));
        report.setInsuranceCompany(trimToNull(form.getInsuranceCompany()));
        report.setPolicyNumber(trimToNull(form.getPolicyNumber()));
        report.setAddress(form.getAddress().trim());
        report.setPostalCode(trimToNull(form.getPostalCode()));
        report.setCity(trimToNull(form.getCity()));
        report.setCounty(trimToNull(form.getCounty()));
        report.setState(trimToNull(form.getState()));
        report.setPropertyType(form.getPropertyType());
        report.setYearBuilt(form.getYearBuilt());
        report.setAreaSqft(form.getAreaSqft());
        report.setFloors(form.getFloors());
        report.setOccupancy(form.getOccupancy());
        report.setPeoplePresent(trimToNull(form.getPeoplePresent()));
        report.setWeather(form.getWeather());
        report.setTemperatureF(form.getTemperatureF());
        report.setSoilCondition(form.getSoilCondition());
    }

    /** Adds sections and items created in the catalog after the report (drafts only). */
    private void syncWithCatalog(Report report) {
        Set<Long> presentSections = report.getSections().stream()
                .map(s -> s.getDefinition().getId()).collect(Collectors.toSet());
        for (SectionDef sectionDef : catalog.activeSections(report.getType())) {
            ReportSection section;
            if (presentSections.contains(sectionDef.getId())) {
                section = report.getSections().stream()
                        .filter(s -> s.getDefinition().getId().equals(sectionDef.getId())).findFirst().orElseThrow();
            } else {
                section = new ReportSection(sectionDef);
                report.addSection(section);
            }
            Set<Long> presentItems = section.getItems().stream()
                    .map(i -> i.getDefinition().getId()).collect(Collectors.toSet());
            for (ItemDef itemDef : catalog.activeItems(sectionDef)) {
                if (!presentItems.contains(itemDef.getId())) {
                    section.addItem(new ReportItem(itemDef));
                }
            }
        }
    }

    private static void copySection(ReportSection from, ReportSection to) {
        to.setNotApplicable(from.isNotApplicable());
        to.setInspectionMethod(from.getInspectionMethod());
        to.setGeneralComments(from.getGeneralComments());
        to.setGeneralLimitations(from.getGeneralLimitations());
        for (ReportItem source : from.getItems()) {
            to.getItems().stream()
                    .filter(i -> i.getDefinition().getId().equals(source.getDefinition().getId()))
                    .findFirst()
                    .ifPresent(target -> {
                        target.setStatus(source.getStatus());
                        target.setRating(source.getRating());
                        target.setMaterials(new ArrayList<>(source.getMaterials()));
                        target.setMaterialOther(source.getMaterialOther());
                        target.setLimitations(new ArrayList<>(source.getLimitations()));
                        target.setLimitationText(source.getLimitationText());
                        target.setObservations(source.getObservations());
                        target.setRecommendation(source.getRecommendation());
                        target.setRecommendationText(source.getRecommendationText());
                        if (target.getDefinition().getFieldType() != FieldType.PHOTO) {
                            target.setAnswers(new ArrayList<>(source.getAnswers()));
                        }
                    });
        }
    }

    private static List<String> clean(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(StringUtils::hasText)
                .distinct().collect(Collectors.toCollection(ArrayList::new));
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

}
