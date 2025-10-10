package com.playbook.ai;

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;

public class ResilientHAQueueProducer {

    public static void main(String[] args) throws Exception {
        var provider = new ConfigFileAuthenticationDetailsProvider(
                "C:\\Users\\Silvio\\.oci\\config", "DEFAULT");

        String primaryQueueId = "ocid1.queue.oc1.sa-vinhedo-1.amaaaaaa7acctnqandvexgkl7nq....wa6ditcqz6vdyftuxcvsq";
        String primaryEndpoint = "https://cell-1.queue.messaging.sa-vinhedo-1.oci.oraclecloud.com";

        String secondaryQueueId = QueueUtils.loadSecondaryQueueId(); // inicia null se não existir
        String secondaryEndpoint = "https://cell-1.queue.messaging.sa-saopaulo-1.oci.oraclecloud.com";

        QueueManager queueManager = new QueueManager(provider,
                primaryQueueId, primaryEndpoint,
                secondaryQueueId, secondaryEndpoint);

        for (int i = 1; i <= 6000; i++) {
            queueManager.sendMessage("Mensagem " + i);
        }
    }
}
