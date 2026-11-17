package com.homeinspect.app.report;

import com.homeinspect.app.catalog.FieldType;
import com.homeinspect.app.catalog.ItemDef;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Validation of the answers of form questions: only valid values of the question's type are kept. */
final class FormAnswers {

    private FormAnswers() {
    }

    static List<String> clean(ItemDef question, List<String> answers) {
        List<String> values = answers == null ? List.of() : answers.stream().filter(Objects::nonNull)
                .map(String::strip).filter(StringUtils::hasText).distinct().toList();
        FieldType type = question.getFieldType();
        if (type.hasOptions()) {
            List<String> options = type.options(question.getOptions());
            List<String> valid = values.stream().filter(options::contains).toList();
            return new ArrayList<>(type.multiple() || valid.isEmpty() ? valid : valid.subList(0, 1));
        }
        if (values.isEmpty()) {
            return new ArrayList<>();
        }
        String value = values.getFirst();
        boolean ok = switch (type) {
            case NUMBER -> isNumber(value);
            case YEAR -> value.matches("\\d{4}") && Integer.parseInt(value) >= 1800 && Integer.parseInt(value) <= 2100;
            case DATE -> isDate(value);
            default -> value.length() <= 500;
        };
        return ok ? new ArrayList<>(List.of(value)) : new ArrayList<>();
    }

    private static boolean isNumber(String value) {
        try {
            new BigDecimal(value);
            return value.length() <= 20;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
