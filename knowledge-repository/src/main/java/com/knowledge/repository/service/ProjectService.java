package com.knowledge.repository.service;

import com.knowledge.repository.audit.AuditService;
import com.knowledge.repository.domain.Project;
import com.knowledge.repository.dto.ProjectDtos.CreateProjectRequest;
import com.knowledge.repository.dto.ProjectDtos.UpdateProjectRequest;
import com.knowledge.repository.repo.ProjectRepository;
import com.knowledge.repository.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository repository;
    private final AuditService audit;

    public List<Project> findAll() {
        return repository.findAll();
    }

    public Project get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    @Transactional
    public Project create(CreateProjectRequest req) {
        Project p = new Project();
        p.setName(req.name());
        p.setDescription(req.description());
        if (req.status() != null) {
            p.setStatus(req.status());
        }
        p = repository.save(p);
        audit.record("Project", p.getId(), "CREATE", Map.of("name", p.getName()));
        return p;
    }

    @Transactional
    public Project update(UUID id, UpdateProjectRequest req) {
        Project p = get(id);
        p.setName(req.name());
        p.setDescription(req.description());
        if (req.status() != null) {
            p.setStatus(req.status());
        }
        p = repository.save(p);
        audit.record("Project", p.getId(), "UPDATE", Map.of("name", p.getName()));
        return p;
    }

    @Transactional
    public void delete(UUID id) {
        Project p = get(id);
        repository.delete(p);
        audit.record("Project", id, "DELETE", Map.of());
    }
}
