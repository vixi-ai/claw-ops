package com.openclaw.manager.openclawserversmanager.users.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.DuplicateResourceException;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.users.dto.ChangePasswordRequest;
import com.openclaw.manager.openclawserversmanager.users.dto.CreateUserRequest;
import com.openclaw.manager.openclawserversmanager.users.dto.UpdateUserRequest;
import com.openclaw.manager.openclawserversmanager.users.dto.UserResponse;
import com.openclaw.manager.openclawserversmanager.users.entity.User;
import com.openclaw.manager.openclawserversmanager.users.mapper.UserMapper;
import com.openclaw.manager.openclawserversmanager.users.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User with email " + request.email() + " already exists");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("User with username " + request.username() + " already exists");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());

        User saved = userRepository.save(user);

        try { auditService.log(AuditAction.USER_CREATED, "USER", saved.getId(), getCurrentUserId(),
                "User '%s' created with role %s".formatted(saved.getUsername(), saved.getRole())); } catch (Exception ignored) {}

        return UserMapper.toResponse(saved);
    }

    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserMapper::toResponse);
    }

    public UserResponse getUserById(UUID id) {
        User user = findUserOrThrow(id);
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = findUserOrThrow(id);
        boolean wasEnabled = user.isEnabled();

        if (request.email() != null && !request.email().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new DuplicateResourceException("User with email " + request.email() + " already exists");
            }
            user.setEmail(request.email());
        }

        if (request.username() != null && !request.username().equals(user.getUsername())) {
            if (userRepository.existsByUsername(request.username())) {
                throw new DuplicateResourceException("User with username " + request.username() + " already exists");
            }
            user.setUsername(request.username());
        }

        if (request.role() != null) {
            user.setRole(request.role());
        }

        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }

        User saved = userRepository.save(user);

        // Audit: disable vs general update
        try {
            if (request.enabled() != null && wasEnabled && !request.enabled()) {
                auditService.log(AuditAction.USER_DISABLED, "USER", saved.getId(), getCurrentUserId(),
                        "User '%s' disabled".formatted(saved.getUsername()));
            } else {
                auditService.log(AuditAction.USER_UPDATED, "USER", saved.getId(), getCurrentUserId(),
                        "User '%s' updated".formatted(saved.getUsername()));
            }
        } catch (Exception ignored) {}

        return UserMapper.toResponse(saved);
    }

    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest request) {
        User user = findUserOrThrow(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        try { auditService.log(AuditAction.USER_PASSWORD_CHANGED, "USER", user.getId(), getCurrentUserId(),
                "Password changed for '%s'".formatted(user.getUsername())); } catch (Exception ignored) {}
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = findUserOrThrow(id);
        String username = user.getUsername();
        userRepository.delete(user);

        try { auditService.log(AuditAction.USER_DELETED, "USER", id, getCurrentUserId(),
                "User '%s' deleted".formatted(username)); } catch (Exception ignored) {}
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User with email " + email + " not found"));
    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + id + " not found"));
    }

    private UUID getCurrentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UUID uuid) {
                return uuid;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
