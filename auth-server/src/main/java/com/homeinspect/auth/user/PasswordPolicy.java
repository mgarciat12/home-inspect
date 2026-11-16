package com.homeinspect.auth.user;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Rules for passwords chosen by users and generator of temporary passwords. */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;

    // No ambiguous characters (0/O, 1/l/I) so it can be dictated or typed on a phone
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LETTERS = 48;
    private static final int TEMPORARY_LENGTH = 14;

    private final SecureRandom random = new SecureRandom();

    public void check(String username, String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw UserException.invalid("password.error.tooShort");
        }
        if (password.length() > 128) {
            throw UserException.invalid("password.error.tooLong");
        }
        boolean letter = password.chars().anyMatch(Character::isLetter);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw UserException.invalid("password.error.weak");
        }
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            throw UserException.invalid("password.error.containsUsername");
        }
    }

    public String temporaryPassword() {
        StringBuilder builder = new StringBuilder(TEMPORARY_LENGTH);
        // Guarantee one letter and one digit so the temporary password also satisfies the policy
        builder.append(ALPHABET.charAt(random.nextInt(LETTERS)));
        builder.append(ALPHABET.charAt(LETTERS + random.nextInt(ALPHABET.length() - LETTERS)));
        for (int i = 2; i < TEMPORARY_LENGTH; i++) {
            builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
