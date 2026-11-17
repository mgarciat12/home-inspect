package com.homeinspect.app.catalog;

import com.homeinspect.app.report.ReportType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/** Admin page of the comment library, one section at a time. */
@Controller
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
class CommentLibraryController {

    private final CommentLibraryService library;
    private final MessageSource messages;

    @GetMapping
    String page(@RequestParam(defaultValue = "FULL") ReportType type, @RequestParam(required = false) String section,
                Model model) {
        var sections = library.sections(type);
        String current = section != null ? section : (sections.isEmpty() ? null : sections.getFirst().code());
        model.addAttribute("types", library.types());
        model.addAttribute("type", type);
        model.addAttribute("sections", sections);
        model.addAttribute("current", current);
        model.addAttribute("items", library.items(type, current));
        return "admin/comments";
    }

    @PostMapping
    String add(@RequestParam long itemId, @RequestParam String text, RedirectAttributes redirect) {
        CommentLibraryService.SectionRef section = library.sectionOfItem(itemId);
        try {
            library.add(itemId, text);
            redirect.addFlashAttribute("message", message("comments.added"));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", message("comments.empty"));
        }
        return back(section, itemId);
    }

    @PostMapping("/{id}")
    String update(@PathVariable long id, @RequestParam String text, @RequestParam long itemId, RedirectAttributes redirect) {
        CommentLibraryService.SectionRef section = library.sectionOfComment(id);
        try {
            library.update(id, text);
            redirect.addFlashAttribute("message", message("comments.saved"));
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", message("comments.empty"));
        }
        return back(section, itemId);
    }

    @PostMapping("/{id}/delete")
    String delete(@PathVariable long id, @RequestParam long itemId, RedirectAttributes redirect) {
        CommentLibraryService.SectionRef section = library.sectionOfComment(id);
        library.delete(id);
        redirect.addFlashAttribute("message", message("comments.deleted"));
        return back(section, itemId);
    }

    @PostMapping("/{id}/move")
    String move(@PathVariable long id, @RequestParam boolean up, @RequestParam long itemId) {
        CommentLibraryService.SectionRef section = library.sectionOfComment(id);
        library.move(id, up);
        return back(section, itemId);
    }

    private static String back(CommentLibraryService.SectionRef section, long itemId) {
        return "redirect:" + UriComponentsBuilder.fromPath("/admin/comments")
                .queryParam("type", section.type()).queryParam("section", section.code())
                .fragment("item-" + itemId).build().toUriString();
    }

    private String message(String code) {
        return messages.getMessage(code, null, code, LocaleContextHolder.getLocale());
    }
}
