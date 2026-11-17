package com.homeinspect.app.catalog;

import com.homeinspect.app.report.ReportType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/** Administration of the comment library (phrases inserted with one click in the observations). */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CommentLibraryService {

    public static final int MAX_LENGTH = 2000;

    private final SectionDefRepository sections;
    private final ItemDefRepository items;
    private final CommentTemplateRepository comments;

    public record SectionOption(String code, int number, String title) {
    }

    /** Section of a catalog item or comment, to come back to the right page after a change. */
    public record SectionRef(ReportType type, String code) {
    }

    public record CommentRow(long id, String text) {
    }

    public record ItemComments(long itemId, String reference, String title, List<CommentRow> comments) {
    }

    /** Report types that have a catalog with sections. */
    @Transactional(readOnly = true)
    public List<ReportType> types() {
        return Arrays.stream(ReportType.values())
                .filter(type -> !sections.findByTypeAndActiveTrueOrderByPositionAsc(type).isEmpty()).toList();
    }

    @Transactional(readOnly = true)
    public List<SectionOption> sections(ReportType type) {
        List<SectionDef> active = sections.findByTypeAndActiveTrueOrderByPositionAsc(type);
        List<SectionOption> options = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            options.add(new SectionOption(active.get(i).getCode(), i + 1, active.get(i).getTitle()));
        }
        return options;
    }

    @Transactional(readOnly = true)
    public List<ItemComments> items(ReportType type, String sectionCode) {
        List<SectionOption> options = sections(type);
        SectionOption option = options.stream().filter(o -> o.code().equals(sectionCode)).findFirst()
                .orElse(options.isEmpty() ? null : options.getFirst());
        if (option == null) {
            return List.of();
        }
        SectionDef section = sections.findByTypeAndCode(type, option.code()).orElseThrow();
        List<ItemDef> sectionItems = items.findBySectionAndActiveTrueOrderByPositionAsc(section);
        List<ItemComments> result = new ArrayList<>();
        for (int i = 0; i < sectionItems.size(); i++) {
            ItemDef item = sectionItems.get(i);
            result.add(new ItemComments(item.getId(), option.number() + "." + (i + 1), item.getTitle(),
                    comments.findByItemOrderByPositionAscIdAsc(item).stream()
                            .map(c -> new CommentRow(c.getId(), c.getText())).toList()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SectionRef sectionOfItem(long itemId) {
        return items.findById(itemId).map(i -> ref(i.getSection())).orElseThrow(NoSuchElementException::new);
    }

    @Transactional(readOnly = true)
    public SectionRef sectionOfComment(long commentId) {
        return comments.findById(commentId).map(c -> ref(c.getItem().getSection()))
                .orElseThrow(NoSuchElementException::new);
    }

    private static SectionRef ref(SectionDef section) {
        return new SectionRef(section.getType(), section.getCode());
    }

    @Transactional
    public void add(long itemId, String text) {
        ItemDef item = items.findById(itemId).orElseThrow(NoSuchElementException::new);
        int position = comments.findByItemOrderByPositionAscIdAsc(item).stream()
                .mapToInt(CommentTemplate::getPosition).max().orElse(0) + 10;
        comments.save(new CommentTemplate(item, clean(text), position));
    }

    @Transactional
    public void update(long commentId, String text) {
        comments.findById(commentId).orElseThrow(NoSuchElementException::new).setText(clean(text));
    }

    @Transactional
    public void delete(long commentId) {
        comments.deleteById(commentId);
    }

    /** Moves a comment one place up or down within its item. */
    @Transactional
    public void move(long commentId, boolean up) {
        CommentTemplate comment = comments.findById(commentId).orElseThrow(NoSuchElementException::new);
        List<CommentTemplate> siblings = new ArrayList<>(comments.findByItemOrderByPositionAscIdAsc(comment.getItem()));
        int index = siblings.indexOf(comment);
        int target = up ? index - 1 : index + 1;
        if (index < 0 || target < 0 || target >= siblings.size()) {
            return;
        }
        siblings.remove(index);
        siblings.add(target, comment);
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).setPosition((i + 1) * 10);
        }
    }

    private static String clean(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("Empty comment");
        }
        String trimmed = text.strip();
        return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
    }
}
