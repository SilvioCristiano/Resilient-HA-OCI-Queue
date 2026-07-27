package com.playbook.ai.queue;

import com.oracle.bmc.queue.model.GetMessage;

/** Implement business processing here. Throwing leaves the message for OCI redelivery/DLQ. */
@FunctionalInterface
public interface MessageHandler {
    void handle(GetMessage message) throws Exception;
}
