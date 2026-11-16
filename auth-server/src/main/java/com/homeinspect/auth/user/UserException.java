package com.homeinspect.auth.user;

/**
 * Business error of the user service. {@link #code()} is a message key (messages.properties),
 * so web pages and the REST API can show it translated.
 */
public class UserException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT, INVALID }

    private final Kind kind;
    private final String code;

    public UserException(Kind kind, String code) {
        super(code);
        this.kind = kind;
        this.code = code;
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    static UserException notFound() {
        return new UserException(Kind.NOT_FOUND, "user.error.notFound");
    }

    static UserException conflict(String code) {
        return new UserException(Kind.CONFLICT, code);
    }

    static UserException invalid(String code) {
        return new UserException(Kind.INVALID, code);
    }
}
