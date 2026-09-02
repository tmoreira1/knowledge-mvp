package com.knowledge.repository.repo;

import com.knowledge.repository.domain.Microservice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MicroserviceRepository extends JpaRepository<Microservice, UUID> {
    List<Microservice> findByProjectId(UUID projectId);
}
