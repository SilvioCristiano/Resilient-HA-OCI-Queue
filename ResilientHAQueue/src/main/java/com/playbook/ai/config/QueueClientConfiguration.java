package com.playbook.ai.config;

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.queue.QueueClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
class QueueClientConfiguration {
    @Bean(destroyMethod = "close")
    QueueClient primaryQueueClient(OciQueueProperties properties) throws Exception {
        return newClient(properties, properties.getPrimary());
    }

    @Bean(destroyMethod = "close")
    QueueClient secondaryQueueClient(OciQueueProperties properties) throws Exception {
        return newClient(properties, properties.getSecondary());
    }

    private QueueClient newClient(OciQueueProperties properties, OciQueueProperties.Endpoint endpoint) throws Exception {
        var provider = new ConfigFileAuthenticationDetailsProvider(
                Path.of(properties.getConfigFile().replace("${user.home}", System.getProperty("user.home"))).toString(),
                properties.getProfile());
        QueueClient client = QueueClient.builder().build(provider);
        client.setEndpoint(endpoint.getEndpoint());
        return client;
    }
}
