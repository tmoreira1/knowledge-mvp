package com.knowledge.repository.dto;

import com.knowledge.repository.domain.Space;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class SpaceDtos {

    public record CreateSpaceRequest(
            @NotBlank String name,
            String type,
            String description,
            UUID parentId,
            String slug) {
    }

    public record UpdateSpaceRequest(
            @NotBlank String name,
            String type,
            String description,
            UUID parentId,
            String slug,
            String status) {
    }

    public record SpaceResponse(
            UUID id,
            String name,
            String slug,
            String type,
            String description,
            UUID parentId,
            String status) {

        public static SpaceResponse from(Space s) {
            return new SpaceResponse(
                    s.getId(),
                    s.getName(),
                    s.getSlug(),
                    s.getType(),
                    s.getDescription(),
                    s.getParentId(),
                    s.getStatus());
        }
    }
}
