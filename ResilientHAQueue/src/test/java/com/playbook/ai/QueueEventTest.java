package com.playbook.ai;

import com.playbook.ai.queue.QueueEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueueEventTest {
    @Test
    void eventCopiesAttributesToPreventMutationAfterPublishing() {
        QueueEvent event = new QueueEvent("{\"id\":\"order-1\"}", Map.of("eventType", "order.created"));
        assertEquals("order.created", event.attributes().get("eventType"));
        assertThrows(UnsupportedOperationException.class, () -> event.attributes().put("region", "sa-saopaulo-1"));
    }

    @Test
    void rejectsBlankPayload() {
        assertThrows(IllegalArgumentException.class, () -> new QueueEvent(" ", Map.of()));
    }
}
