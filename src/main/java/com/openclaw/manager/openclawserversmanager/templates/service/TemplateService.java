package com.openclaw.manager.openclawserversmanager.templates.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.DuplicateResourceException;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.deployment.dto.DeploymentJobResponse;
import com.openclaw.manager.openclawserversmanager.deployment.service.DeploymentJobService;
import com.openclaw.manager.openclawserversmanager.templates.dto.CreateTemplateRequest;
import com.openclaw.manager.openclawserversmanager.templates.dto.DeployTemplateResponse;
import com.openclaw.manager.openclawserversmanager.templates.dto.TemplateResponse;
import com.openclaw.manager.openclawserversmanager.templates.dto.UpdateTemplateRequest;
import com.openclaw.manager.openclawserversmanager.templates.entity.AgentTemplate;
import com.openclaw.manager.openclawserversmanager.templates.mapper.AgentTemplateMapper;
import com.openclaw.manager.openclawserversmanager.templates.repository.AgentTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class TemplateService {

    private final AgentTemplateRepository templateRepository;
    private final DeploymentJobService deploymentJobService;
    private final AuditService auditService;

    public TemplateService(AgentTemplateRepository templateRepository,
                           DeploymentJobService deploymentJobService,
                           AuditService auditService) {
        this.templateRepository = templateRepository;
        this.deploymentJobService = deploymentJobService;
        this.auditService = auditService;
    }

    @Transactional
    public TemplateResponse createTemplate(CreateTemplateRequest request, UUID userId) {
        if (templateRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Template with name '" + request.name() + "' already exists");
        }

        AgentTemplate template = AgentTemplateMapper.toEntity(request);
        template.setCreatedBy(userId);
        AgentTemplate saved = templateRepository.save(template);

        try {
            auditService.log(AuditAction.TEMPLATE_CREATED, "AGENT_TEMPLATE", saved.getId(), userId,
                    "Template '%s' created (agent type: %s)".formatted(saved.getName(), saved.getAgentType()));
        } catch (Exception ignored) {}

        return AgentTemplateMapper.toResponse(saved);
    }

    public Page<TemplateResponse> getAllTemplates(Pageable pageable) {
        return templateRepository.findAll(pageable).map(AgentTemplateMapper::toResponse);
    }

    public TemplateResponse getTemplate(UUID id) {
        return AgentTemplateMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public TemplateResponse updateTemplate(UUID id, UpdateTemplateRequest request, UUID userId) {
        AgentTemplate template = findOrThrow(id);

        if (request.name() != null && !request.name().isBlank()) {
            if (!template.getName().equals(request.name()) && templateRepository.existsByName(request.name())) {
                throw new DuplicateResourceException("Template with name '" + request.name() + "' already exists");
            }
            template.setName(request.name());
        }
        if (request.description() != null) template.setDescription(request.description());
        if (request.agentType() != null && !request.agentType().isBlank()) template.setAgentType(request.agentType());
        if (request.installScript() != null && !request.installScript().isBlank()) template.setInstallScript(request.installScript());

        AgentTemplate saved = templateRepository.save(template);

        try {
            auditService.log(AuditAction.TEMPLATE_UPDATED, "AGENT_TEMPLATE", saved.getId(), userId,
                    "Template '%s' updated".formatted(saved.getName()));
        } catch (Exception ignored) {}

        return AgentTemplateMapper.toResponse(saved);
    }

    @Transactional
    public void deleteTemplate(UUID id, UUID userId) {
        AgentTemplate template = findOrThrow(id);
        templateRepository.delete(template);

        try {
            auditService.log(AuditAction.TEMPLATE_DELETED, "AGENT_TEMPLATE", id, userId,
                    "Template '%s' deleted".formatted(template.getName()));
        } catch (Exception ignored) {}
    }

    @Transactional
    public DeployTemplateResponse deployTemplate(UUID templateId, UUID serverId, UUID userId) {
        AgentTemplate template = findOrThrow(templateId);

        DeploymentJobResponse job = deploymentJobService.triggerTemplateJob(
                serverId, template.getName(), template.getInstallScript(), userId);

        try {
            auditService.log(AuditAction.TEMPLATE_DEPLOYED, "AGENT_TEMPLATE", templateId, userId,
                    "Template '%s' deployed to server %s (job: %s)".formatted(template.getName(), serverId, job.id()));
        } catch (Exception ignored) {}

        return new DeployTemplateResponse(job.id());
    }

    private AgentTemplate findOrThrow(UUID id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent template with id " + id + " not found"));
    }
}
