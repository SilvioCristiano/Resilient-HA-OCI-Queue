package com.playbook.ai.queue;

import com.oracle.bmc.queue.model.GetMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Replace this example handler with the transactional business operation for the service. */
@Component
class LoggingMessageHandler implements MessageHandler {
    private static final Logger log = LoggerFactory.getLogger(LoggingMessageHandler.class);

    @Override
    public void handle(GetMessage message) {
        log.info("Processing message id={}, deliveryCount={}, attributes={}", message.getId(),
                message.getDeliveryCount(), message.getMetadata() == null ? null : message.getMetadata().getCustomProperties());
    }
}
