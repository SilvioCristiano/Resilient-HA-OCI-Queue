package com.playbook.ai;

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.queue.QueueAdminClient;
import com.oracle.bmc.queue.requests.CreateQueueRequest;
import com.oracle.bmc.queue.responses.CreateQueueResponse;
import com.oracle.bmc.queue.model.CreateQueueDetails;

public class CreateQueue {
    public static void main(String[] args) throws Exception {

        // Caminho para o ficheiro ~/.oci/config e o perfil (ex: DEFAULT)
        final String configurationFilePath = "C:\\Users\\Silvio\\.oci\\config";
        final String profile = "DEFAULT";

        // Cria o provider de autenticação
        ConfigFileAuthenticationDetailsProvider provider =
                new ConfigFileAuthenticationDetailsProvider(configurationFilePath, profile);

        // Inicializa o cliente do serviço Queue Admin
        try (QueueAdminClient client = QueueAdminClient.builder().build(provider)) {

            // Substitui com o OCID real do compartimento
            String compartmentId = "ocid1.compartment.oc1..aaaaaaaayyfw5s76wsgg2d6td4ywm........jq7mvnltyazfyq";
            String displayName = "FilaExemploJava2";

            // Define os detalhes da fila
            CreateQueueDetails createQueueDetails = CreateQueueDetails.builder()
                    .compartmentId(compartmentId)
                    .displayName(displayName)
                    .build();

            // Monta o request
            CreateQueueRequest request = CreateQueueRequest.builder()
                    .createQueueDetails(createQueueDetails)
                    .build();

            // Faz a chamada à API OCI Queue
            CreateQueueResponse response = client.createQueue(request);

            // Obtém o OCID da fila criada
            String queueOcid = response.getOpcWorkRequestId();

            // Exibe o OCID no console
            System.out.println("Fila criada com sucesso!");
            System.out.println("OCID da fila: " + queueOcid);

            // Define variável de ambiente (temporária)
            setEnv("OCI_QUEUE_OCID", queueOcid);
            System.out.println("Variável de ambiente OCI_QUEUE_OCID definida: " + System.getenv("OCI_QUEUE_OCID"));
        }
    }

    // Função utilitária para definir variável de ambiente
    private static void setEnv(String key, String value) throws Exception {
        try {
            java.util.Map<String, String> env = System.getenv();
            java.lang.reflect.Field field = env.getClass().getDeclaredField("m");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> writableEnv = (java.util.Map<String, String>) field.get(env);
            writableEnv.put(key, value);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao definir variável de ambiente: " + e.getMessage(), e);
        }
    }
}
