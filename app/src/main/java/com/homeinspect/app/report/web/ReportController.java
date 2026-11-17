package com.homeinspect.app.report.web;

import com.homeinspect.app.catalog.CatalogService;
import com.homeinspect.app.report.ItemStatus;
import com.homeinspect.app.report.Occupancy;
import com.homeinspect.app.report.PropertyType;
import com.homeinspect.app.report.Rating;
import com.homeinspect.app.report.Recommendation;
import com.homeinspect.app.report.ReportIncompleteException;
import com.homeinspect.app.report.ReportService;
import com.homeinspect.app.report.ReportStatus;
import com.homeinspect.app.report.ReportType;
import com.homeinspect.app.report.SoilCondition;
import com.homeinspect.app.report.Weather;
import com.homeinspect.app.report.dto.ReportFilter;
import com.homeinspect.app.report.dto.ReportHeaderForm;
import com.homeinspect.app.security.CurrentUser;
import com.homeinspect.app.security.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/** Pages of the reports: list, header form, editor and lifecycle actions. */
@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
class ReportController {

    static final int PAGE_SIZE = 20;

    private final ReportService reports;
    private final CatalogService catalog;
    private final CurrentUserProvider currentUser;
    private final MessageSource messages;
    private final Clock clock;

    @ModelAttribute
    void options(Model model) {
        model.addAttribute("reportTypes", ReportType.values());
        model.addAttribute("propertyTypes", PropertyType.values());
        model.addAttribute("occupancies", Occupancy.values());
        model.addAttribute("weathers", Weather.values());
        model.addAttribute("soilConditions", SoilCondition.values());
        model.addAttribute("statuses", ReportStatus.values());
        model.addAttribute("itemStatuses", ItemStatus.values());
        model.addAttribute("ratings", Rating.values());
        model.addAttribute("recommendations", Recommendation.values());
    }

    @GetMapping
    String list(@ModelAttribute("filter") ReportFilter filter, @RequestParam(defaultValue = "0") int page, Model model) {
        CurrentUser user = currentUser.get();
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("inspectedAt"), Sort.Order.desc("id")));
        model.addAttribute("page", reports.search(filter, pageable, user));
        model.addAttribute("isAdmin", user.isAdmin());
        return "reports/list";
    }

    @GetMapping("/new")
    String newForm(@RequestParam(required = false) ReportType type, Model model) {
        ReportHeaderForm form = new ReportHeaderForm();
        if (type != null) {
            form.setType(type);
        }
        form.setInspectedAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES));
        model.addAttribute("form", form);
        model.addAttribute("availableTypes", catalog.availableTypes());
        return "reports/header-form";
    }

    @PostMapping
    String create(@Valid @ModelAttribute("form") ReportHeaderForm form, BindingResult result, Model model) {
        Set<ReportType> available = catalog.availableTypes();
        if (form.getType() != null && !available.contains(form.getType())) {
            result.rejectValue("type", "report.type.unavailable");
        }
        if (result.hasErrors()) {
            model.addAttribute("availableTypes", available);
            return "reports/header-form";
        }
        long id = reports.create(form, currentUser.get());
        return "redirect:/reports/" + id + "/edit";
    }

    @GetMapping("/{id}")
    String show(@PathVariable long id) {
        return "redirect:/reports/" + id + "/edit";
    }

    @GetMapping("/{id}/edit")
    String editor(@PathVariable long id, @RequestParam(required = false) String section, Model model) {
        model.addAttribute("editor", reports.editor(id, section, currentUser.get()));
        model.addAttribute("visit", reports.visit(id, currentUser.get()));
        return "reports/editor";
    }

    @GetMapping("/{id}/header")
    String headerForm(@PathVariable long id, Model model) {
        model.addAttribute("form", reports.headerForm(id, currentUser.get()));
        model.addAttribute("visit", reports.visit(id, currentUser.get()));
        model.addAttribute("reportId", id);
        model.addAttribute("reportNumber", reports.number(id, currentUser.get()));
        return "reports/header-form";
    }

    @PostMapping("/{id}/header")
    String updateHeader(@PathVariable long id, @Valid @ModelAttribute("form") ReportHeaderForm form,
                        BindingResult result, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("visit", reports.visit(id, currentUser.get()));
            model.addAttribute("reportId", id);
            model.addAttribute("reportNumber", reports.number(id, currentUser.get()));
            return "reports/header-form";
        }
        reports.updateHeader(id, form, currentUser.get());
        redirect.addFlashAttribute("message", message("report.header.saved"));
        return "redirect:/reports/" + id + "/edit";
    }

    @PostMapping("/{id}/finalize")
    String finalizeReport(@PathVariable long id, RedirectAttributes redirect) {
        try {
            reports.finalizeReport(id, currentUser.get());
        } catch (ReportIncompleteException e) {
            String details = e.problems().stream()
                    .map(p -> p.item() + " (" + message(p.code()) + ")")
                    .collect(java.util.stream.Collectors.joining("; "));
            redirect.addFlashAttribute("error", messages.getMessage("report.finalize.incomplete",
                    new Object[]{details}, LocaleContextHolder.getLocale()));
            return "redirect:/reports/" + id + "/edit";
        }
        redirect.addFlashAttribute("message", message("report.finalized"));
        return "redirect:/reports/" + id + "/edit";
    }

    @PostMapping("/{id}/reopen")
    String reopen(@PathVariable long id, RedirectAttributes redirect) {
        reports.reopen(id, currentUser.get());
        redirect.addFlashAttribute("message", message("report.reopened"));
        return "redirect:/reports/" + id + "/edit";
    }

    @PostMapping("/{id}/duplicate")
    String duplicate(@PathVariable long id, RedirectAttributes redirect) {
        long copy = reports.duplicate(id, currentUser.get());
        redirect.addFlashAttribute("message", message("report.duplicated"));
        return "redirect:/reports/" + copy + "/header";
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable long id, RedirectAttributes redirect) {
        reports.delete(id, currentUser.get());
        redirect.addFlashAttribute("message", message("report.deleted"));
        return "redirect:/reports";
    }

    private String message(String code) {
        return messages.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }
}
