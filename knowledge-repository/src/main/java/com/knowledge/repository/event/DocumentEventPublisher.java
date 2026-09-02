package com.knowledge.repository.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes document lifecycle events to the {@code knowledge.events} topic
 * exchange. Best-effort: if the broker is unavailable the failure is logged
 * and swallowed so the originating request still succeeds.
 *
 * Contract (consumed by knowledge-search):
 *   body JSON: { "event": "DocumentCreated", "documentId": "<uuid>" }
 *   routing key: document.created | document.updated | document.deleted
 */
@Slf4j
@Component
public class DocumentEventPublisher {

    public static final String CREATED = "DocumentCreated";
    public static final String UPDATED = "DocumentUpdated";
    public static final String DELETED = "DocumentDeleted";

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;

    public DocumentEventPublisher(RabbitTemplate rabbitTemplate,
                                  @Value("${knowledge.events.exchange}") String exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
    }

    public void publishCreated(UUID documentId) {
        publish(CREATED, "document.created", documentId);
    }

    public void publishUpdated(UUID documentId) {
        publish(UPDATED, "document.updated", documentId);
    }

    public void publishDeleted(UUID documentId) {
        publish(DELETED, "document.deleted", documentId);
    }

    private void publish(String event, String routingKey, UUID documentId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event);
        body.put("documentId", documentId.toString());
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, body);
            log.info("Published {} (rk={}) for document {}", event, routingKey, documentId);
        } catch (Exception e) {
            log.warn("Best-effort event {} for document {} not published: {}",
                    event, documentId, e.getMessage());
        }
    }
}
