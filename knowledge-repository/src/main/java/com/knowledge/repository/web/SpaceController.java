package com.knowledge.repository.web;

import com.knowledge.repository.dto.SpaceDtos.CreateSpaceRequest;
import com.knowledge.repository.dto.SpaceDtos.SpaceResponse;
import com.knowledge.repository.dto.SpaceDtos.UpdateSpaceRequest;
import com.knowledge.repository.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService service;

    @GetMapping
    public List<SpaceResponse> list() {
        return service.findAll().stream().map(SpaceResponse::from).toList();
    }

    @GetMapping("/{id}")
    public SpaceResponse get(@PathVariable UUID id) {
        return SpaceResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceResponse create(@Valid @RequestBody CreateSpaceRequest req) {
        return SpaceResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    public SpaceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSpaceRequest req) {
        return SpaceResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
