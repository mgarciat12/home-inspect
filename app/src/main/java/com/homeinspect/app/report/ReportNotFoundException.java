package com.homeinspect.app.report;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Unknown report, or one the user may not see (same answer, so ids of others are not revealed). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ReportNotFoundException extends RuntimeException {

    public ReportNotFoundException() {
        super("Report not found");
    }
}
