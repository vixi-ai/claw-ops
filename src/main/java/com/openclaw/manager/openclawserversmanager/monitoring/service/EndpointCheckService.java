package com.openclaw.manager.openclawserversmanager.monitoring.service;

import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheck;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheckResult;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.EndpointCheckRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.EndpointCheckResultRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EndpointCheckService {

    private final EndpointCheckRepository checkRepository;
    private final EndpointCheckResultRepository resultRepository;

    public EndpointCheckService(EndpointCheckRepository checkRepository,
                                 EndpointCheckResultRepository resultRepository) {
        this.checkRepository = checkRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(readOnly = true)
    public List<EndpointCheck> listChecks(UUID serverId, Boolean enabled) {
        if (serverId != null) return checkRepository.findByServerId(serverId);
        if (enabled != null) return checkRepository.findByEnabled(enabled);
        return checkRepository.findAll();
    }

    @Transactional(readOnly = true)
    public EndpointCheck getCheck(UUID id) {
        return checkRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Endpoint check not found: " + id));
    }

    @Transactional
    public EndpointCheck createCheck(String name, String url, String checkType,
                                      UUID serverId, Integer expectedStatusCode,
                                      int intervalSeconds) {
        EndpointCheck check = new EndpointCheck();
        check.setName(name);
        check.setUrl(url);
        check.setCheckType(com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheckType.valueOf(checkType));
        check.setServerId(serverId);
        check.setExpectedStatusCode(expectedStatusCode != null ? expectedStatusCode : 200);
        check.setIntervalSeconds(intervalSeconds > 0 ? intervalSeconds : 300);
        check.setEnabled(true);
        return checkRepository.save(check);
    }

    @Transactional
    public EndpointCheck updateCheck(UUID id, String name, String url,
                                      Boolean enabled, Integer expectedStatusCode,
                                      Integer intervalSeconds) {
        EndpointCheck check = getCheck(id);
        if (name != null) check.setName(name);
        if (url != null) check.setUrl(url);
        if (enabled != null) check.setEnabled(enabled);
        if (expectedStatusCode != null) check.setExpectedStatusCode(expectedStatusCode);
        if (intervalSeconds != null) check.setIntervalSeconds(intervalSeconds);
        return checkRepository.save(check);
    }

    @Transactional
    public void deleteCheck(UUID id) {
        if (!checkRepository.existsById(id)) {
            throw new ResourceNotFoundException("Endpoint check not found: " + id);
        }
        checkRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public EndpointCheckResult getLatestResult(UUID checkId) {
        return resultRepository.findFirstByEndpointCheckIdOrderByCheckedAtDesc(checkId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Page<EndpointCheckResult> getResults(UUID checkId, Pageable pageable) {
        return resultRepository.findByEndpointCheckIdOrderByCheckedAtDesc(checkId, pageable);
    }

    @Transactional
    public EndpointCheckResult saveResult(EndpointCheckResult result) {
        return resultRepository.save(result);
    }
}
