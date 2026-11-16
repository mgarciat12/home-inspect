package com.homeinspect.app.admin;

import org.springframework.http.HttpStatusCode;

/** Error returned by the user administration API; {@link #code()} is a message key. */
public class AdminApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final String code;

    public AdminApiException(HttpStatusCode status, String code, Throwable cause) {
        super(code, cause);
        this.status = status;
        this.code = code;
    }

    static AdminApiException from(HttpStatusCode status, String code) {
        String resolved = code != null && code.startsWith("user.error.") ? code
                : status.value() == 404 ? "user.error.notFound"
                : status.value() == 401 || status.value() == 403 ? "admin.error.unauthorized"
                : status.is4xxClientError() ? "admin.error.invalid"
                : "admin.error.unavailable";
        return new AdminApiException(status, resolved, null);
    }

    public HttpStatusCode status() {
        return status;
    }

    public String code() {
        return code;
    }
}
