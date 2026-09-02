package com.knowledge.repository.web;

import com.knowledge.repository.dto.MicroserviceDtos.CreateMicroserviceRequest;
import com.knowledge.repository.dto.MicroserviceDtos.MicroserviceResponse;
import com.knowledge.repository.dto.MicroserviceDtos.UpdateMicroserviceRequest;
import com.knowledge.repository.service.MicroserviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/microservices")
@RequiredArgsConstructor
public class MicroserviceController {

    private final MicroserviceService service;

    @GetMapping
    public List<MicroserviceResponse> list() {
        return service.findAll().stream().map(MicroserviceResponse::from).toList();
    }

    @GetMapping("/{id}")
    public MicroserviceResponse get(@PathVariable UUID id) {
        return MicroserviceResponse.from(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MicroserviceResponse create(@Valid @RequestBody CreateMicroserviceRequest req) {
        return MicroserviceResponse.from(service.create(req));
    }

    @PutMapping("/{id}")
    public MicroserviceResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateMicroserviceRequest req) {
        return MicroserviceResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
