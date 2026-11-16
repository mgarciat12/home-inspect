package com.homeinspect.app.photo;

/** The uploaded file is not a supported image; {@link #code()} is a message key. */
public class ImageRejectedException extends RuntimeException {

    private final String code;

    public ImageRejectedException(String code) {
        super(code);
        this.code = code;
    }

    public ImageRejectedException(String code, Throwable cause) {
        super(code, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
