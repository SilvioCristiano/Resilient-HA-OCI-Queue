package com.playbook.ai;

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.queue.QueueClient;
import com.oracle.bmc.queue.requests.GetMessagesRequest;
import com.oracle.bmc.queue.responses.GetMessagesResponse;
import com.oracle.bmc.model.BmcException;


public class ResilientQueueConsumer {

    // Clientes do OCI Queue Messaging
    private static QueueClient primaryClient;
    private static QueueClient secondaryClient;

    // OCID da fila primária (fixo para este exemplo)
    private static final String PRIMARY_QUEUE_ID = "ocid1.queue.oc1.sa-vinhedo-1.amaaaaaa7acctnqand....c25k2r4wa6ditcqz6vdyftuxcvsq";
    
  

    // Endpoints (fixos para este exemplo, devem corresponder aos OCIDs/Regiões)
    private static final String PRIMARY_ENDPOINT = "https://cell-1.queue.messaging.sa-vinhedo-1.oci.oraclecloud.com";
    private static final String SECONDARY_ENDPOINT = "https://cell-1.queue.messaging.sa-saopaulo-1.oci.oraclecloud.com";

    private static final int MAX_RETRIES = 3;
    private static final int BASE_DELAY_MS = 500; // 0.5s

    public static void main(String[] args) throws Exception {
        System.out.println("\n--- Resilient Queue Consumer Iniciado ---");
        //System.out.println("Fila Secundária OCID (variável estática): " + SECONDARY_QUEUE_ID);

        // 1. Configuração do OCI SDK e Clientes
        // IMPORTANTE: Ajuste o caminho do arquivo de configuração OCI conforme necessário.
        var provider = new ConfigFileAuthenticationDetailsProvider("C:\\Users\\Silvio\\.oci\\config", "DEFAULT");

        primaryClient = QueueClient.builder().build(provider);
        primaryClient.setEndpoint(PRIMARY_ENDPOINT);

        secondaryClient = QueueClient.builder().build(provider);
        secondaryClient.setEndpoint(SECONDARY_ENDPOINT);

        // 2. Loop principal de consumo
        while (true) {
            consumeWithRetryAndFailover();
            Thread.sleep(2000); // Pausa
        }
    }

    /**
     * Tenta consumir da fila primária com retry exponencial.
     * Se falhar após todas as tentativas, tenta consumir da fila secundária (failover).
     */
    private static void consumeWithRetryAndFailover() {
        boolean consumed = false;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                consumeFromPrimary();
                consumed = true;
                break;
            } catch (Exception ex) {
                if (isRetryableError(ex)) {
                    int delay = (int) (BASE_DELAY_MS * Math.pow(2, attempt));
                    System.err.printf("Tentativa %d de %d na Fila Primária falhou (%s). Retentando em %d ms...%n",
                            attempt, MAX_RETRIES, ex.getMessage(), delay);
                    sleep(delay);
                } else {
                    System.err.println("Erro não recuperável na fila primária. Quebrando o loop de retentativas: " + ex.getMessage());
                    break;
                }
            }
        }

        if (!consumed) {
            System.err.println("\n--- FALHA GERAL NA FILA PRIMÁRIA. TENTANDO FAILOVER PARA FILA SECUNDÁRIA... ---");
            try {
                consumeFromSecondary(); 
            } catch (Exception ex) {
                System.err.println("Falha também na fila secundária: " + ex.getMessage());
            }
        }
    }

    /**
     * Tenta buscar e imprimir mensagens da fila primária.
     */
    private static void consumeFromPrimary() {
        GetMessagesRequest request = GetMessagesRequest.builder()
                .queueId(PRIMARY_QUEUE_ID)
                .visibilityInSeconds(30)    
                .timeoutInSeconds(10)      
                .limit(10)
                .build();

        GetMessagesResponse response = primaryClient.getMessages(request);

        if (response.getGetMessages() != null && response.getGetMessages().getMessages() != null) {
             response.getGetMessages().getMessages().forEach(msg ->
                System.out.println("Consumido da primária: " + new String(msg.getContent()))
             );
             if (response.getGetMessages().getMessages().isEmpty()) {
                 System.out.println("Fila Primária: Nenhuma mensagem disponível.");
             }
        } else {
             System.out.println("Fila Primária: Nenhuma mensagem disponível.");
        }
    }

    /**
     * Tenta buscar e imprimir mensagens da fila secundária.
     * Utiliza o SECONDARY_QUEUE_ID carregado do arquivo properties.
     */
    private static void consumeFromSecondary() {
    	  // OCID da fila secundária (carregado do arquivo queue.properties via QueueUtils)
        String SECONDARY_QUEUE_ID = QueueUtils.loadSecondaryQueue();
        // A variável SECONDARY_QUEUE_ID já contém o valor lido do arquivo properties.
        System.out.println("DEBUG: Iniciando consumo da Fila Secundária (OCID: " + SECONDARY_QUEUE_ID + ")");
        
        GetMessagesRequest request = GetMessagesRequest.builder()
                .queueId(SECONDARY_QUEUE_ID) // <--- Usa o OCID carregado
                .visibilityInSeconds(30)    
                .timeoutInSeconds(10)      
                .limit(10)
                .build();

        GetMessagesResponse response = secondaryClient.getMessages(request);

        if (response.getGetMessages() != null && response.getGetMessages().getMessages() != null) {
            response.getGetMessages().getMessages().forEach(msg ->
                System.out.println("Consumido da secundária: " + new String(msg.getContent()))
            );
            if (response.getGetMessages().getMessages().isEmpty()) {
                 System.out.println("Fila Secundária: Nenhuma mensagem disponível.");
             }
        } else {
             System.out.println("Fila Secundária: Nenhuma mensagem disponível.");
        }
    }

    /**
     * Verifica se a exceção é um erro recuperável (retryable) do OCI SDK.
     */
    private static boolean isRetryableError(Exception ex) {
        if (ex instanceof BmcException) {
            int status = ((BmcException) ex).getStatusCode();
            // 5xx (Server Error) ou 429 (Too Many Requests) são retryable
            return status >= 500 || status == 429;
        }
        return false;
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
