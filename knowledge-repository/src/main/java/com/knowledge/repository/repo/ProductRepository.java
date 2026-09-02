package com.knowledge.repository.repo;

import com.knowledge.repository.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByProjectId(UUID projectId);
}
