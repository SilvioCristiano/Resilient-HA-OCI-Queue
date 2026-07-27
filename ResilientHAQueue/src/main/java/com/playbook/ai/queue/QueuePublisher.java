package com.playbook.ai.queue;

import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.queue.QueueClient;
import com.oracle.bmc.queue.model.MessageMetadata;
import com.oracle.bmc.queue.model.PutMessagesDetails;
import com.oracle.bmc.queue.model.PutMessagesDetailsEntry;
import com.oracle.bmc.queue.requests.PutMessagesRequest;
import com.playbook.ai.config.OciQueueProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QueuePublisher {
    private static final Logger log = LoggerFactory.getLogger(QueuePublisher.class);
    private final QueueClient primaryClient;
    private final QueueClient secondaryClient;
    private final OciQueueProperties properties;
    private final Counter published;
    private final Counter failed;

    public QueuePublisher(@Qualifier("primaryQueueClient") QueueClient primaryClient,
                          @Qualifier("secondaryQueueClient") QueueClient secondaryClient,
                          OciQueueProperties properties, MeterRegistry meterRegistry) {
        this.primaryClient = primaryClient;
        this.secondaryClient = secondaryClient;
        this.properties = properties;
        this.published = meterRegistry.counter("oci.queue.messages.published");
        this.failed = meterRegistry.counter("oci.queue.messages.publish.failed");
    }

    /** Publishes to the primary queue and uses the pre-provisioned secondary queue only on transient failure. */
    public void publish(QueueEvent event) {
        try {
            putWithRetry(primaryClient, properties.getPrimary(), event, "primary");
            return;
        } catch (RuntimeException primaryFailure) {
            log.warn("Primary queue unavailable after retries; attempting secondary queue", primaryFailure);
        }
        putWithRetry(secondaryClient, properties.getSecondary(), event, "secondary");
    }

    private void putWithRetry(QueueClient client, OciQueueProperties.Endpoint endpoint, QueueEvent event, String target) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.getRetry().getMaxAttempts(); attempt++) {
            try {
                PutMessagesRequest request = PutMessagesRequest.builder()
                        .queueId(endpoint.getQueueId())
                        .opcRequestId(UUID.randomUUID().toString())
                        .putMessagesDetails(PutMessagesDetails.builder().messages(List.of(
                                PutMessagesDetailsEntry.builder()
                                        .content(event.body())
                                        .metadata(MessageMetadata.builder().customProperties(event.attributes()).build())
                                        .build())).build())
                        .build();
                client.putMessages(request);
                published.increment();
                log.info("Published event to {} queue with attributes={}", target, event.attributes());
                return;
            } catch (RuntimeException ex) {
                lastFailure = ex;
                if (!isRetryable(ex) || attempt == properties.getRetry().getMaxAttempts()) {
                    failed.increment();
                    throw ex;
                }
                backoff(attempt, target, ex);
            }
        }
        throw lastFailure == null ? new IllegalStateException("Publish failed without a reported cause") : lastFailure;
    }

    private void backoff(int attempt, String target, RuntimeException error) {
        long exponential = properties.getRetry().getInitialDelayMs() * (1L << (attempt - 1));
        long delay = Math.min(exponential, properties.getRetry().getMaxDelayMs());
        long jitter = (long) (Math.random() * Math.max(1, delay / 4));
        log.warn("Attempt {} to {} queue failed ({}); retrying in {} ms", attempt, target, error.getMessage(), delay + jitter);
        try {
            Thread.sleep(delay + jitter);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying OCI Queue publish", interrupted);
        }
    }

    static boolean isRetryable(RuntimeException error) {
        if (error instanceof BmcException bmc) {
            int status = bmc.getStatusCode();
            return status == 408 || status == 429 || status >= 500;
        }
        return true; // connection/time-out failures do not always have an HTTP response
    }
}
