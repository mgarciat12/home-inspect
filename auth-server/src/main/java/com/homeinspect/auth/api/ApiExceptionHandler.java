package com.homeinspect.auth.api;

import com.homeinspect.auth.user.UserException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/** Errors of the user API as RFC 9457 problem details; {@code code} is a stable message key. */
@RestControllerAdvice(assignableTypes = UserAdminController.class)
@RequiredArgsConstructor
class ApiExceptionHandler {

    private final MessageSource messages;

    @ExceptionHandler(UserException.class)
    ProblemDetail userError(UserException e) {
        HttpStatus status = switch (e.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case INVALID -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status,
                messages.getMessage(e.code(), null, e.code(), LocaleContextHolder.getLocale()));
        problem.setProperty("code", e.code());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        Map<String, String> fields = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(error -> error.getField(),
                        error -> String.valueOf(error.getDefaultMessage()), (a, b) -> a));
        problem.setProperty("code", "validation");
        problem.setProperty("fields", fields);
        return problem;
    }
}
