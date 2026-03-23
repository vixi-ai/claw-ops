package com.openclaw.manager.openclawserversmanager.deployment.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.DuplicateResourceException;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.deployment.dto.CreateScriptRequest;
import com.openclaw.manager.openclawserversmanager.deployment.dto.ScriptResponse;
import com.openclaw.manager.openclawserversmanager.deployment.dto.UpdateScriptRequest;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentScript;
import com.openclaw.manager.openclawserversmanager.deployment.mapper.DeploymentMapper;
import com.openclaw.manager.openclawserversmanager.deployment.repository.DeploymentScriptRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeploymentScriptService {

    private final DeploymentScriptRepository scriptRepository;
    private final AuditService auditService;

    public DeploymentScriptService(DeploymentScriptRepository scriptRepository,
                                   AuditService auditService) {
        this.scriptRepository = scriptRepository;
        this.auditService = auditService;
    }

    @Transactional
    public ScriptResponse createScript(CreateScriptRequest request, UUID userId) {
        if (scriptRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Script with name '" + request.name() + "' already exists");
        }

        DeploymentScript script = DeploymentMapper.toEntity(request);
        script.setCreatedBy(userId);
        DeploymentScript saved = scriptRepository.save(script);

        try {
            auditService.log(AuditAction.SCRIPT_CREATED, "DEPLOYMENT_SCRIPT", saved.getId(), userId,
                    "Script '%s' created (type: %s)".formatted(saved.getName(), saved.getScriptType()));
        } catch (Exception ignored) {}

        return DeploymentMapper.toScriptResponse(saved);
    }

    public Page<ScriptResponse> getAllScripts(Pageable pageable) {
        return scriptRepository.findAll(pageable).map(DeploymentMapper::toScriptResponse);
    }

    public ScriptResponse getScript(UUID id) {
        return DeploymentMapper.toScriptResponse(findOrThrow(id));
    }

    public DeploymentScript getScriptEntity(UUID id) {
        return findOrThrow(id);
    }

    @Transactional
    public ScriptResponse updateScript(UUID id, UpdateScriptRequest request, UUID userId) {
        DeploymentScript script = findOrThrow(id);

        if (request.name() != null && !request.name().isBlank()) {
            if (!script.getName().equals(request.name()) && scriptRepository.existsByName(request.name())) {
                throw new DuplicateResourceException("Script with name '" + request.name() + "' already exists");
            }
            script.setName(request.name());
        }
        if (request.description() != null) script.setDescription(request.description());
        if (request.scriptContent() != null && !request.scriptContent().isBlank()) script.setScriptContent(request.scriptContent());
        if (request.scriptType() != null) script.setScriptType(request.scriptType());

        DeploymentScript saved = scriptRepository.save(script);

        try {
            auditService.log(AuditAction.SCRIPT_UPDATED, "DEPLOYMENT_SCRIPT", saved.getId(), userId,
                    "Script '%s' updated".formatted(saved.getName()));
        } catch (Exception ignored) {}

        return DeploymentMapper.toScriptResponse(saved);
    }

    @Transactional
    public void deleteScript(UUID id, UUID userId) {
        DeploymentScript script = findOrThrow(id);
        scriptRepository.delete(script);

        try {
            auditService.log(AuditAction.SCRIPT_DELETED, "DEPLOYMENT_SCRIPT", id, userId,
                    "Script '%s' deleted".formatted(script.getName()));
        } catch (Exception ignored) {}
    }

    private DeploymentScript findOrThrow(UUID id) {
        return scriptRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment script with id " + id + " not found"));
    }
}
