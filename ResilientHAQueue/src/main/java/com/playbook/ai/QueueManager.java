package com.playbook.ai;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.queue.QueueClient;
import com.oracle.bmc.queue.model.CreateQueueDetails;
import com.oracle.bmc.queue.model.WorkRequest;
import com.oracle.bmc.queue.model.WorkRequestResource;
import com.oracle.bmc.queue.model.PutMessagesDetails;
import com.oracle.bmc.queue.model.PutMessagesDetailsEntry;
import com.oracle.bmc.queue.requests.PutMessagesRequest;
import com.oracle.bmc.queue.responses.PutMessagesResponse;
/*
import com.oracle.bmc.queueadmin.QueueAdminClient;
import com.oracle.bmc.queueadmin.model.CreateQueueDetails;
import com.oracle.bmc.queueadmin.requests.CreateQueueRequest;
import com.oracle.bmc.queueadmin.responses.CreateQueueResponse;*/



import com.oracle.bmc.queue.QueueAdminClient;
import com.oracle.bmc.queue.requests.CreateQueueRequest;
import com.oracle.bmc.queue.requests.GetWorkRequestRequest;
import com.oracle.bmc.queue.responses.CreateQueueResponse;
import com.oracle.bmc.queue.responses.GetWorkRequestResponse;

import java.util.List;
import java.io.IOException;

public class QueueManager {

    private QueueClient primaryClient;
    private QueueClient secondaryClient;
    private String primaryQueueId;
    private String secondaryQueueId;
    private String primaryEndpoint;
    private String secondaryEndpoint;

    private static final int MAX_RETRIES = 3;
    private static final int BASE_DELAY_MS = 500;

    // Compartment fixo para criação da fila secundária
    private static final String COMPARTMENT_ID = "ocid1.compartment.oc1.....d6td4ywm3l5xwkuckkh6...tyazfyq";

    public QueueManager(ConfigFileAuthenticationDetailsProvider provider,
                        String primaryQueueId,
                        String primaryEndpoint,
                        String secondaryQueueId,
                        String secondaryEndpoint) {

        this.primaryQueueId = primaryQueueId;
        this.secondaryQueueId = secondaryQueueId;
        this.primaryEndpoint = primaryEndpoint;
        this.secondaryEndpoint = secondaryEndpoint;

        primaryClient = QueueClient.builder().build(provider);
        primaryClient.setEndpoint(primaryEndpoint);

        secondaryClient = QueueClient.builder().build(provider);
        secondaryClient.setEndpoint(secondaryEndpoint);
    }

    // ===== Envio de mensagem com failover =====
    public void sendMessage(String message) throws InterruptedException {
        boolean sent = false;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                sendToPrimary(message);
                sent = true;
                break;
            } catch (Exception ex) {
                if (isRetryableError(ex)) {
                    int delay = (int) (BASE_DELAY_MS * Math.pow(2, attempt));
                    System.err.printf("Tentativa %d falhou (%s). Retentando em %d ms...%n",
                            attempt, ex.getMessage(), delay);
                    sleep(delay);
                } else {
                    System.err.println("Erro não recuperável na fila primária: " + ex.getMessage());
                    break;
                }
            }
        }

        if (!sent) {
            System.err.println("Fila primária indisponível. Tentando usar fila secundária...");
            if (secondaryQueueId.isEmpty()) {
                System.out.println("Criando nova fila secundária...");
                secondaryQueueId = createSecondaryQueue();
                
            }
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
	            try {
	            sendToSecondary(message);
	            sent = false;
	            break;
	            } catch (Exception ex) {
	                if (isRetryableError(ex)) {
	                    int delay = (int) (BASE_DELAY_MS * Math.pow(2, attempt));
	                    System.err.printf("Tentativa %d falhou (%s). Retentando em %d ms...%n",
	                            attempt, ex.getMessage(), delay);
	                    sleep(delay);
	                } else {
	                    System.err.println("Erro não recuperável na fila secundária: " + ex.getMessage());
	                    break;
	                }
	            }  
            }
        }
    }

    // ===== Métodos de envio =====
    protected void sendToPrimary(String message) {
        sendMessageToQueue(primaryClient, primaryQueueId, message);
        System.out.println("Mensagem enviada para fila primária: " + message);
    }

    protected void sendToSecondary(String message) {
        sendMessageToQueue(secondaryClient, secondaryQueueId, message);
        System.out.println("Mensagem enviada para fila secundária: " + message);
    }

    private void sendMessageToQueue(QueueClient client, String queueId, String message) {
        PutMessagesRequest request = PutMessagesRequest.builder()
                .queueId(queueId)
                .putMessagesDetails(
                        PutMessagesDetails.builder()
                                .messages(List.of(
                                        PutMessagesDetailsEntry.builder()
                                                .content(message)
                                                .build()))
                                .build())
                .build();

        PutMessagesResponse response = client.putMessages(request);
    }

    // ===== Criação automática da fila secundária =====
    protected String createSecondaryQueue() {
        String newQueueOcid = null;
        try {
            ConfigFileAuthenticationDetailsProvider provider =
                    new ConfigFileAuthenticationDetailsProvider("C:\\Users\\Silvio\\.oci\\config", "DEFAULT");
            
            QueueAdminClient client = QueueAdminClient.builder().build(provider);
            String displayName = "OCI-SECONDARY-QUEUE";
         // Define os detalhes da fila
            CreateQueueDetails createQueueDetails = CreateQueueDetails.builder()
                    .compartmentId(COMPARTMENT_ID)
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
            
           
            
         
              

                // 3. Criar a Requisição
                GetWorkRequestRequest getWorkRequestRequest = GetWorkRequestRequest.builder()
                        .workRequestId(queueOcid)
                        .build();

                System.out.println("Buscando detalhes da Work Request: " + queueOcid);

                // 4. Executar a Chamada da API
                GetWorkRequestResponse responseworkrequest = client.getWorkRequest(getWorkRequestRequest);

                // 5. Processar e Imprimir o Identifier
                if (responseworkrequest.getWorkRequest() != null && responseworkrequest.getWorkRequest().getResources() != null) {
                    List<WorkRequestResource> resources = responseworkrequest.getWorkRequest().getResources();

                    if (!resources.isEmpty()) {
                        // O identifier do recurso criado (a Queue) é o primeiro item na lista de recursos
                        newQueueOcid = resources.get(0).getIdentifier();
                        
                        System.out.println("---");
                        System.out.println("Status da Work Request: " + responseworkrequest.getWorkRequest().getStatus());
                        System.out.println("Operation Type: " + responseworkrequest.getWorkRequest().getOperationType());
                        System.out.println("---");
                        System.out.println("Identifier do Recurso Retornado (Queue OCID):");
                        System.out.println(newQueueOcid); 
                        System.out.println("---");
                    } else {
                        System.out.println("A Work Request não retornou detalhes de recursos (Resources).");
                    }
                } else {
                    System.out.println("Não foi possível obter os detalhes da Work Request ou o campo 'data' está vazio.");
                }
         

            System.out.println("Fila secundária criada com sucesso: " + newQueueOcid);

            // 🧠 Salva o OCID para reutilização
            QueueUtils.saveSecondaryQueueId(newQueueOcid);

            // 🌱 Exporta como variável de ambiente (para persistir fora da JVM)
            setEnvironmentVariable("SECONDARY_QUEUE_OCID", newQueueOcid);

            System.out.println("Variável de ambiente 'SECONDARY_QUEUE_OCID' configurada.");

            client.close();

        } catch (Exception ex) {
            System.err.println("Erro ao criar fila secundária: " + ex.getMessage());
        }

        return newQueueOcid;
    }

    // ===== Define variável de ambiente no SO =====
    private void setEnvironmentVariable(String key, String value) {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "setx", key, value).inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("bash", "-c", "export " + key + "=" + value).inheritIO().start().waitFor();
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("Não foi possível definir variável de ambiente: " + e.getMessage());
        }
    }

    // ===== Utilidades =====
    private boolean isRetryableError(Exception ex) {
        String msg = ex.getMessage();
        return msg != null && (msg.contains("500") || msg.contains("429"));
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    public String getSecondaryQueueId() { return secondaryQueueId; }
    public QueueClient getSecondaryClient() { return secondaryClient; }
}
