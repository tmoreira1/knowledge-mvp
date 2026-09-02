package com.knowledge.repository.service;

import com.knowledge.repository.audit.AuditService;
import com.knowledge.repository.domain.Space;
import com.knowledge.repository.dto.SpaceDtos.CreateSpaceRequest;
import com.knowledge.repository.dto.SpaceDtos.UpdateSpaceRequest;
import com.knowledge.repository.repo.SpaceRepository;
import com.knowledge.repository.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpaceService {

    /** Vocabulário controlado espelhando o CHECK da migration V2__space.sql. */
    private static final Set<String> VALID_TYPES =
            Set.of("TEAM", "DEPARTMENT", "PROJECT", "PRODUCT", "OTHER");
    private static final String DEFAULT_TYPE = "OTHER";

    private final SpaceRepository repository;
    private final AuditService audit;

    /** Coage tipos desconhecidos para OTHER, respeitando o CHECK do banco. */
    private static String coerceType(String type) {
        if (type == null) {
            return DEFAULT_TYPE;
        }
        String normalized = type.trim().toUpperCase();
        return VALID_TYPES.contains(normalized) ? normalized : DEFAULT_TYPE;
    }

    public List<Space> findAll() {
        return repository.findAll();
    }

    public Space get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Space not found: " + id));
    }

    @Transactional
    public Space create(CreateSpaceRequest req) {
        Space s = new Space();
        s.setName(req.name());
        s.setType(coerceType(req.type()));
        s.setDescription(req.description());
        s.setParentId(req.parentId());
        s.setSlug(req.slug());
        s = repository.save(s);
        audit.record("Space", s.getId(), "CREATE", Map.of("name", s.getName(), "type", s.getType()));
        return s;
    }

    @Transactional
    public Space update(UUID id, UpdateSpaceRequest req) {
        Space s = get(id);
        s.setName(req.name());
        s.setType(coerceType(req.type()));
        s.setDescription(req.description());
        s.setParentId(req.parentId());
        s.setSlug(req.slug());
        if (req.status() != null) {
            s.setStatus(req.status());
        }
        s = repository.save(s);
        audit.record("Space", s.getId(), "UPDATE", Map.of("name", s.getName(), "type", s.getType()));
        return s;
    }

    @Transactional
    public void delete(UUID id) {
        Space s = get(id);
        repository.delete(s);
        audit.record("Space", id, "DELETE", Map.of());
    }
}
