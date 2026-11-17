package com.homeinspect.app.report;

import com.homeinspect.app.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Access rules of the reports, applied in the service layer:
 * an inspector sees and edits only their own reports; an admin sees all of them.
 * Final reports are read-only; only an admin can reopen them.
 */
@Component
public class ReportAccessGuard {

    public boolean canView(Report report, CurrentUser user) {
        return user.isAdmin() || report.getInspector().getId().equals(user.profileId());
    }

    public boolean canEdit(Report report, CurrentUser user) {
        return canView(report, user) && !report.isFinal();
    }

    public void checkView(Report report, CurrentUser user) {
        if (!canView(report, user)) {
            throw new ReportNotFoundException();
        }
    }

    public void checkEdit(Report report, CurrentUser user) {
        checkView(report, user);
        if (report.isFinal()) {
            throw new ReportLockedException();
        }
    }

    public void checkReopen(Report report, CurrentUser user) {
        checkView(report, user);
        if (!user.isAdmin()) {
            throw new AccessDeniedException("Only an administrator can reopen a report");
        }
    }

    /** Inspectors may delete their own drafts; admins any report. */
    public void checkDelete(Report report, CurrentUser user) {
        checkView(report, user);
        if (!user.isAdmin() && report.isFinal()) {
            throw new ReportLockedException();
        }
    }
}
