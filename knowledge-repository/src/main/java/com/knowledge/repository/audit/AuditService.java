package com.knowledge.repository.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.repository.domain.AuditLog;
import com.knowledge.repository.repo.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void record(String entityType, UUID entityId, String action, Map<String, Object> detail) {
        AuditLog row = new AuditLog();
        row.setEntityType(entityType);
        row.setEntityId(entityId);
        row.setAction(action);
        row.setActor(ActorContext.get());
        row.setDetail(toJson(detail));
        auditLogRepository.save(row);
    }

    private String toJson(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit detail, storing empty object", e);
            return "{}";
        }
    }
}
