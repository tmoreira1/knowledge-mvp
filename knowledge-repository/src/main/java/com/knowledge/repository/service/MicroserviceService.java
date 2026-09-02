package com.knowledge.repository.service;

import com.knowledge.repository.audit.AuditService;
import com.knowledge.repository.domain.Microservice;
import com.knowledge.repository.dto.MicroserviceDtos.CreateMicroserviceRequest;
import com.knowledge.repository.dto.MicroserviceDtos.UpdateMicroserviceRequest;
import com.knowledge.repository.repo.MicroserviceRepository;
import com.knowledge.repository.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MicroserviceService {

    private final MicroserviceRepository repository;
    private final AuditService audit;

    public List<Microservice> findAll() {
        return repository.findAll();
    }

    public Microservice get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Microservice not found: " + id));
    }

    @Transactional
    public Microservice create(CreateMicroserviceRequest req) {
        Microservice m = new Microservice();
        m.setProjectId(req.projectId());
        m.setProductId(req.productId());
        m.setName(req.name());
        m.setDescription(req.description());
        if (req.status() != null) {
            m.setStatus(req.status());
        }
        m = repository.save(m);
        audit.record("Microservice", m.getId(), "CREATE", Map.of("name", m.getName()));
        return m;
    }

    @Transactional
    public Microservice update(UUID id, UpdateMicroserviceRequest req) {
        Microservice m = get(id);
        m.setName(req.name());
        m.setDescription(req.description());
        if (req.status() != null) {
            m.setStatus(req.status());
        }
        m = repository.save(m);
        audit.record("Microservice", m.getId(), "UPDATE", Map.of("name", m.getName()));
        return m;
    }

    @Transactional
    public void delete(UUID id) {
        Microservice m = get(id);
        repository.delete(m);
        audit.record("Microservice", id, "DELETE", Map.of());
    }
}
