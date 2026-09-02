package com.knowledge.repository.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "space")
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String slug;

    @Column(nullable = false)
    private String type = "OTHER";

    private String description;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
