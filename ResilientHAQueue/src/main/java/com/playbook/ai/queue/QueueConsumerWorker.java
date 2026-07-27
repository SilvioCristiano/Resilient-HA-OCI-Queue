package com.playbook.ai.queue;

import com.oracle.bmc.queue.QueueClient;
import com.oracle.bmc.queue.model.GetMessage;
import com.oracle.bmc.queue.requests.DeleteMessageRequest;
import com.oracle.bmc.queue.requests.GetMessagesRequest;
import com.playbook.ai.config.OciQueueProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** A dedicated long-polling worker; it never runs queue I/O on a web/controller thread. */
@Component
public class QueueConsumerWorker implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(QueueConsumerWorker.class);
    private final QueueClient primaryClient;
    private final QueueClient secondaryClient;
    private final OciQueueProperties properties;
    private final MessageHandler handler;
    private final Counter consumed;
    private final Counter processingFailures;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "oci-queue-consumer"));

    public QueueConsumerWorker(@Qualifier("primaryQueueClient") QueueClient primaryClient,
                               @Qualifier("secondaryQueueClient") QueueClient secondaryClient,
                               OciQueueProperties properties, MessageHandler handler, MeterRegistry meterRegistry) {
        this.primaryClient = primaryClient;
        this.secondaryClient = secondaryClient;
        this.properties = properties;
        this.handler = handler;
        this.consumed = meterRegistry.counter("oci.queue.messages.consumed");
        this.processingFailures = meterRegistry.counter("oci.queue.messages.processing.failed");
    }

    @Override public void start() {
        if (properties.getConsumer().isEnabled() && running.compareAndSet(false, true)) executor.submit(this::runLoop);
    }
    @Override public void stop() { running.set(false); executor.shutdownNow(); }
    @Override public boolean isRunning() { return running.get(); }
    @Override public boolean isAutoStartup() { return true; }
    @Override public int getPhase() { return Integer.MAX_VALUE; }

    private void runLoop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                pollAndProcess(primaryClient, properties.getPrimary(), "primary");
            } catch (RuntimeException primaryFailure) {
                log.warn("Primary queue poll failed; attempting secondary", primaryFailure);
                try {
                    pollAndProcess(secondaryClient, properties.getSecondary(), "secondary");
                } catch (RuntimeException secondaryFailure) {
                    log.error("Secondary queue poll also failed", secondaryFailure);
                    pause(1_000);
                }
            }
        }
    }

    private void pollAndProcess(QueueClient client, OciQueueProperties.Endpoint endpoint, String target) {
        GetMessagesRequest.Builder request = GetMessagesRequest.builder()
                .queueId(endpoint.getQueueId())
                .visibilityInSeconds(properties.getConsumer().getVisibilityTimeoutSeconds())
                .timeoutInSeconds(properties.getConsumer().getPollTimeoutSeconds())
                .limit(properties.getConsumer().getBatchSize());
        if (endpoint.getConsumerGroupId() != null && !endpoint.getConsumerGroupId().isBlank()) {
            request.consumerGroupId(endpoint.getConsumerGroupId());
        }
        var response = client.getMessages(request.build());
        List<GetMessage> messages = response.getGetMessages() == null ? List.of() : response.getGetMessages().getMessages();
        if (messages == null || messages.isEmpty()) return;
        for (GetMessage message : messages) processAndAcknowledge(client, endpoint, message, target);
    }

    private void processAndAcknowledge(QueueClient client, OciQueueProperties.Endpoint endpoint, GetMessage message, String target) {
        try {
            handler.handle(message); // business transaction must commit before this acknowledgement
            DeleteMessageRequest.Builder delete = DeleteMessageRequest.builder()
                    .queueId(endpoint.getQueueId())
                    .messageReceipt(message.getReceipt());
            if (endpoint.getConsumerGroupId() != null && !endpoint.getConsumerGroupId().isBlank()) {
                delete.consumerGroupId(endpoint.getConsumerGroupId());
            }
            client.deleteMessage(delete.build());
            consumed.increment();
            log.debug("Acknowledged message id={} from {} queue", message.getId(), target);
        } catch (Exception processingFailure) {
            processingFailures.increment();
            log.error("Message id={} was not acknowledged; OCI will redeliver it or route it to the configured DLQ", message.getId(), processingFailure);
        }
    }

    private void pause(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
