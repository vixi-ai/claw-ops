package com.openclaw.manager.openclawserversmanager.templates.mapper;

import com.openclaw.manager.openclawserversmanager.templates.dto.CreateTemplateRequest;
import com.openclaw.manager.openclawserversmanager.templates.dto.TemplateResponse;
import com.openclaw.manager.openclawserversmanager.templates.entity.AgentTemplate;

public final class AgentTemplateMapper {

    private AgentTemplateMapper() {}

    public static TemplateResponse toResponse(AgentTemplate template) {
        return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getAgentType(),
                template.getInstallScript(),
                template.getCreatedAt(),
                template.getUpdatedAt()
        );
    }

    public static AgentTemplate toEntity(CreateTemplateRequest request) {
        AgentTemplate template = new AgentTemplate();
        template.setName(request.name());
        template.setDescription(request.description());
        template.setAgentType(request.agentType());
        template.setInstallScript(request.installScript());
        return template;
    }
}
