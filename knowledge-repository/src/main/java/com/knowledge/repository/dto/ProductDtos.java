package com.knowledge.repository.dto;

import com.knowledge.repository.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class ProductDtos {

    public record CreateProductRequest(
            @NotNull UUID projectId,
            @NotBlank String name,
            String description,
            String status) {
    }

    public record UpdateProductRequest(
            @NotBlank String name,
            String description,
            String status) {
    }

    public record ProductResponse(
            UUID id,
            UUID projectId,
            String name,
            String description,
            String status) {

        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getProjectId(), p.getName(),
                    p.getDescription(), p.getStatus());
        }
    }
}
