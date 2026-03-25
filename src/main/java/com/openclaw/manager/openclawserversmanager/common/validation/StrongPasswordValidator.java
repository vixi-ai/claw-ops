package com.openclaw.manager.openclawserversmanager.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return true; // @NotBlank handles null/blank
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;

        if (password.length() < 8) {
            addViolation(context, "must be at least 8 characters");
            valid = false;
        }
        if (password.length() > 128) {
            addViolation(context, "must be at most 128 characters");
            valid = false;
        }
        if (!password.matches(".*[A-Z].*")) {
            addViolation(context, "must contain at least one uppercase letter");
            valid = false;
        }
        if (!password.matches(".*[a-z].*")) {
            addViolation(context, "must contain at least one lowercase letter");
            valid = false;
        }
        if (!password.matches(".*[0-9].*")) {
            addViolation(context, "must contain at least one digit");
            valid = false;
        }
        if (!password.matches(".*[^a-zA-Z0-9].*")) {
            addViolation(context, "must contain at least one special character");
            valid = false;
        }

        return valid;
    }

    private void addViolation(ConstraintValidatorContext context, String message) {
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
    }
}
