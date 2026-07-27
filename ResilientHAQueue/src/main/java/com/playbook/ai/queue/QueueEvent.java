package com.playbook.ai.queue;

import java.util.Map;

/**
 * The payload and the attributes evaluated by OCI Queue consumer-group filters.
 * Keep attribute names stable: filters are evaluated only when the message is published.
 */
public record QueueEvent(String body, Map<String, String> attributes) {
    public QueueEvent {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Event body must not be blank");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
