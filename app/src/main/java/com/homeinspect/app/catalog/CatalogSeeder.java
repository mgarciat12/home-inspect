package com.homeinspect.app.catalog;

import com.homeinspect.app.report.ReportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Synchronises the catalog tables with the catalog file of each report type
 * ({@code catalog/full.yml}, {@code catalog/four-point.yml}...) at startup (by code):
 * creates new sections and items, updates texts and options, and deactivates what is no longer
 * in the file. Nothing is deleted, so existing reports keep their elements.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class CatalogSeeder implements ApplicationRunner {

    private final SectionDefRepository sections;
    private final ItemDefRepository items;
    private final LimitationDefRepository limitations;
    private final CommentTemplateRepository comments;

    private final ResourceLoader resourceLoader;

    /** Directory of the catalog files, one per report type. */
    @Value("${home-inspect.catalog-dir:classpath:catalog/}")
    private String catalogDir;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        // Limitations are shared by every type: the union of all files, in order
        Set<String> allLimitations = new LinkedHashSet<>();
        for (ReportType type : ReportType.values()) {
            Resource file = resourceLoader.getResource(catalogDir + type.catalogFile());
            Map<String, Object> root = file.exists() ? load(file) : Map.of();
            allLimitations.addAll(strings(root.get("limitations")));
            syncType(type, maps(root.get("sections")));
        }
        syncLimitations(new ArrayList<>(allLimitations));
    }

    private static Map<String, Object> load(Resource file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            Map<String, Object> root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
            return root == null ? Map.of() : root;
        }
    }

    /** A type without catalog file just has every section deactivated. */
    private void syncType(ReportType type, List<Map<String, Object>> sectionList) {
        Set<String> seenSections = new HashSet<>();
        int created = 0;
        for (int s = 0; s < sectionList.size(); s++) {
            created += syncSection(type, sectionList.get(s), (s + 1) * 10, seenSections);
        }
        for (SectionDef section : sections.findByType(type)) {
            if (!seenSections.contains(section.getCode()) && section.isActive()) {
                section.setActive(false);
                log.info("Catalog section '{}' of {} deactivated (no longer in the catalog file)", section.getCode(), type);
            }
        }
        if (!sectionList.isEmpty()) {
            log.info("Catalog {} synchronised: {} sections, {} new items", type, sectionList.size(), created);
        }
    }

    private int syncSection(ReportType type, Map<String, Object> data, int position, Set<String> seenSections) {
        String code = required(data, "code");
        seenSections.add(code);
        SectionDef section = sections.findByTypeAndCode(type, code).orElseGet(() -> {
            SectionDef fresh = new SectionDef();
            fresh.setType(type);
            fresh.setCode(code);
            return fresh;
        });
        section.setPosition(position);
        section.setTitle(required(data, "title"));
        section.setDescription((String) data.get("description"));
        section.setOptional(Boolean.TRUE.equals(data.get("optional")));
        section.setMethods(strings(data.get("methods")));
        section.setActive(true);
        sections.save(section);

        List<Map<String, Object>> itemList = maps(data.get("items"));
        Set<String> seenItems = new HashSet<>();
        int created = 0;
        for (int i = 0; i < itemList.size(); i++) {
            Map<String, Object> itemData = itemList.get(i);
            String itemCode = required(itemData, "code");
            seenItems.add(itemCode);
            ItemDef item = items.findBySectionAndCode(section, itemCode).orElse(null);
            boolean isNew = item == null;
            if (isNew) {
                item = new ItemDef();
                item.setSection(section);
                item.setCode(itemCode);
            }
            item.setPosition((i + 1) * 10);
            item.setTitle(required(itemData, "title"));
            item.setMaterials(strings(itemData.get("materials")));
            item.setLimitations(strings(itemData.get("limitations")));
            Object field = itemData.get("field");
            item.setFieldType(field == null ? null : FieldType.valueOf(field.toString().trim().toUpperCase(Locale.ROOT)));
            item.setOptions(strings(itemData.get("options")));
            item.setRequired(Boolean.TRUE.equals(itemData.get("required")));
            item.setExplainOn(strings(itemData.get("explain_on")));
            item.setPhotoRequired(Boolean.TRUE.equals(itemData.get("photo_required")));
            item.setHelp((String) itemData.get("help"));
            if (item.getFieldType() == FieldType.CHOICE || item.getFieldType() == FieldType.MULTI) {
                if (item.getOptions().isEmpty()) {
                    throw new IllegalStateException("Catalog: question '" + code + "." + itemCode + "' has no options");
                }
            }
            item.setActive(true);
            if (isNew) {
                created++;
            }
            boolean seedComments = isNew || !item.isCommentsSeeded();
            item.setCommentsSeeded(true);
            items.save(item);
            if (seedComments) {
                List<String> texts = strings(itemData.get("comments"));
                for (int c = 0; c < texts.size(); c++) {
                    comments.save(new CommentTemplate(item, texts.get(c), (c + 1) * 10));
                }
            }
        }
        for (ItemDef item : items.findBySectionOrderByPositionAsc(section)) {
            if (!seenItems.contains(item.getCode()) && item.isActive()) {
                item.setActive(false);
                log.info("Catalog item '{}.{}' deactivated", code, item.getCode());
            }
        }
        return created;
    }

    private void syncLimitations(List<String> labels) {
        List<LimitationDef> existing = limitations.findAll();
        limitations.deleteAll(existing.stream().filter(l -> !labels.contains(l.getLabel())).toList());
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            int position = (i + 1) * 10;
            LimitationDef limitation = existing.stream().filter(l -> l.getLabel().equals(label)).findFirst()
                    .orElseGet(() -> new LimitationDef(label, position));
            limitation.setPosition(position);
            limitations.save(limitation);
        }
    }

    private static String required(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Catalog: missing '" + key + "' in " + data);
        }
        return text.trim();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        return value == null ? List.of() : (List<Map<String, Object>>) value;
    }

    /** List of plain strings; anything else (e.g. "a: b" parsed as a map) is a mistake in the file. */
    private static List<String> strings(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (Object element : (List<?>) value) {
            if (!(element instanceof String text)) {
                throw new IllegalStateException("Catalog: expected text but found " + element
                        + " (quote values that contain ': ')");
            }
            result.add(text.trim());
        }
        return result;
    }
}
