package com.knowledge.repository.service;

import com.knowledge.repository.audit.AuditService;
import com.knowledge.repository.domain.Product;
import com.knowledge.repository.dto.ProductDtos.CreateProductRequest;
import com.knowledge.repository.dto.ProductDtos.UpdateProductRequest;
import com.knowledge.repository.repo.ProductRepository;
import com.knowledge.repository.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;
    private final AuditService audit;

    public List<Product> findAll() {
        return repository.findAll();
    }

    public Product get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }

    @Transactional
    public Product create(CreateProductRequest req) {
        Product p = new Product();
        p.setProjectId(req.projectId());
        p.setName(req.name());
        p.setDescription(req.description());
        if (req.status() != null) {
            p.setStatus(req.status());
        }
        p = repository.save(p);
        audit.record("Product", p.getId(), "CREATE", Map.of("name", p.getName()));
        return p;
    }

    @Transactional
    public Product update(UUID id, UpdateProductRequest req) {
        Product p = get(id);
        p.setName(req.name());
        p.setDescription(req.description());
        if (req.status() != null) {
            p.setStatus(req.status());
        }
        p = repository.save(p);
        audit.record("Product", p.getId(), "UPDATE", Map.of("name", p.getName()));
        return p;
    }

    @Transactional
    public void delete(UUID id) {
        Product p = get(id);
        repository.delete(p);
        audit.record("Product", id, "DELETE", Map.of());
    }
}
