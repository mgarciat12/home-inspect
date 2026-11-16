package com.homeinspect.app.report;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** The report is final: it cannot be changed until an administrator reopens it. */
@ResponseStatus(HttpStatus.CONFLICT)
public class ReportLockedException extends RuntimeException {

    public ReportLockedException() {
        super("Report is final");
    }
}
