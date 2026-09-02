package com.knowledge.repository.repo;

import com.knowledge.repository.domain.Space;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpaceRepository extends JpaRepository<Space, UUID> {
}
