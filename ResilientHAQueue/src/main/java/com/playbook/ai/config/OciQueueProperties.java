package com.playbook.ai.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("oci.queue")
public class OciQueueProperties {
    @NotBlank private String configFile = "${user.home}/.oci/config";
    @NotBlank private String profile = "DEFAULT";
    private Endpoint primary = new Endpoint();
    private Endpoint secondary = new Endpoint();
    private Consumer consumer = new Consumer();
    private Retry retry = new Retry();

    public static class Endpoint {
        @NotBlank private String queueId;
        @NotBlank private String endpoint;
        /** Empty means the Primary Consumer Group (backwards-compatible behaviour). */
        private String consumerGroupId;
        public String getQueueId() { return queueId; }
        public void setQueueId(String queueId) { this.queueId = queueId; }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getConsumerGroupId() { return consumerGroupId; }
        public void setConsumerGroupId(String consumerGroupId) { this.consumerGroupId = consumerGroupId; }
    }

    public static class Consumer {
        private boolean enabled = true;
        @Min(1) @Max(100) private int batchSize = 10;
        @Min(0) @Max(30) private int pollTimeoutSeconds = 30;
        @Min(1) private int visibilityTimeoutSeconds = 60;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public int getPollTimeoutSeconds() { return pollTimeoutSeconds; }
        public void setPollTimeoutSeconds(int pollTimeoutSeconds) { this.pollTimeoutSeconds = pollTimeoutSeconds; }
        public int getVisibilityTimeoutSeconds() { return visibilityTimeoutSeconds; }
        public void setVisibilityTimeoutSeconds(int visibilityTimeoutSeconds) { this.visibilityTimeoutSeconds = visibilityTimeoutSeconds; }
    }

    public static class Retry {
        @Min(1) @Max(10) private int maxAttempts = 3;
        @Min(50) private long initialDelayMs = 250;
        @Min(1_000) private long maxDelayMs = 5_000;
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public long getInitialDelayMs() { return initialDelayMs; }
        public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }
        public long getMaxDelayMs() { return maxDelayMs; }
        public void setMaxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; }
    }

    public String getConfigFile() { return configFile; }
    public void setConfigFile(String configFile) { this.configFile = configFile; }
    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }
    public Endpoint getPrimary() { return primary; }
    public void setPrimary(Endpoint primary) { this.primary = primary; }
    public Endpoint getSecondary() { return secondary; }
    public void setSecondary(Endpoint secondary) { this.secondary = secondary; }
    public Consumer getConsumer() { return consumer; }
    public void setConsumer(Consumer consumer) { this.consumer = consumer; }
    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }
}
