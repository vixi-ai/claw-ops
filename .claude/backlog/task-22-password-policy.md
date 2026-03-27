# Task 22: Password Policy Enforcement

**Status:** DONE
**Module(s):** users, common
**Priority:** HIGH
**Created:** 2026-03-24
**Completed:** 2026-03-24

## Description

Password validation currently only enforces length (8-128 chars) via `@Size`. There are no complexity requirements. This task adds a custom `@StrongPassword` Jakarta Validation annotation that enforces configurable password rules.

## Acceptance Criteria

### Custom Validator
- [ ] New `@StrongPassword` annotation in `common/validation/`
- [ ] Corresponding `StrongPasswordValidator` implementing `ConstraintValidator<StrongPassword, String>`
- [ ] Rules enforced:
  - Minimum 8 characters (already exists, kept for consistency)
  - Maximum 128 characters
  - At least 1 uppercase letter (`[A-Z]`)
  - At least 1 lowercase letter (`[a-z]`)
  - At least 1 digit (`[0-9]`)
  - At least 1 special character (`[^a-zA-Z0-9]`)
- [ ] Each failed rule produces a specific error message (not a generic "password too weak")

### Applied to DTOs
- [ ] `CreateUserRequest.password` — replace `@Size(min=8, max=128)` with `@StrongPassword`
- [ ] `ChangePasswordRequest.newPassword` — replace `@Size(min=8, max=128)` with `@StrongPassword`

### Documentation
- [ ] Architecture log entry in `.claude/architecture/common.md`

## Implementation Notes

### New Files
- `src/main/java/com/openclaw/manager/openclawserversmanager/common/validation/StrongPassword.java`
- `src/main/java/com/openclaw/manager/openclawserversmanager/common/validation/StrongPasswordValidator.java`

### Files to Modify
- `src/main/java/com/openclaw/manager/openclawserversmanager/users/dto/CreateUserRequest.java`
- `src/main/java/com/openclaw/manager/openclawserversmanager/users/dto/ChangePasswordRequest.java`
- `.claude/architecture/common.md`

### StrongPasswordValidator Structure
```java
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) return true; // @NotBlank handles null

        context.disableDefaultConstraintViolation();
        boolean valid = true;

        if (password.length() < 8) { addViolation(context, "must be at least 8 characters"); valid = false; }
        if (password.length() > 128) { addViolation(context, "must be at most 128 characters"); valid = false; }
        if (!password.matches(".*[A-Z].*")) { addViolation(context, "must contain at least one uppercase letter"); valid = false; }
        if (!password.matches(".*[a-z].*")) { addViolation(context, "must contain at least one lowercase letter"); valid = false; }
        if (!password.matches(".*[0-9].*")) { addViolation(context, "must contain at least one digit"); valid = false; }
        if (!password.matches(".*[^a-zA-Z0-9].*")) { addViolation(context, "must contain at least one special character"); valid = false; }

        return valid;
    }
}
```

## Files Modified
- `src/main/java/com/openclaw/manager/openclawserversmanager/common/validation/StrongPassword.java` — **created** — custom Jakarta Validation annotation
- `src/main/java/com/openclaw/manager/openclawserversmanager/common/validation/StrongPasswordValidator.java` — **created** — validates password complexity with per-rule error messages
- `src/main/java/com/openclaw/manager/openclawserversmanager/users/dto/CreateUserRequest.java` — **modified** — replaced `@Size(min=8, max=128)` with `@StrongPassword`
- `src/main/java/com/openclaw/manager/openclawserversmanager/users/dto/ChangePasswordRequest.java` — **modified** — replaced `@Size(min=8, max=128)` with `@StrongPassword`
- `.claude/architecture/common.md` — **modified** — appended StrongPassword Validator entry
