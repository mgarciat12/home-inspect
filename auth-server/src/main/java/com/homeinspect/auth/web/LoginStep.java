package com.homeinspect.auth.web;

/** Steps that may remain after the password is accepted, in this order. */
public enum LoginStep {
    /** Code of the authenticator app (two-step verification enabled by the user). */
    MFA_VERIFY("/mfa/verify"),
    /** Temporary password (new user or reset by an administrator). */
    PASSWORD_CHANGE("/password/change"),
    /** Two-step verification required for everybody and not set up yet. */
    MFA_SETUP("/mfa/setup");

    private final String path;

    LoginStep(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
