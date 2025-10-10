package com.playbook.ai;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.queue.QueueAdminClient;
import com.oracle.bmc.queue.requests.GetWorkRequestRequest;
import com.oracle.bmc.queue.responses.GetWorkRequestResponse;
import com.oracle.bmc.queue.model.WorkRequestResource;

import java.io.IOException;
import java.util.List;

public class GetWorkRequestDetails {

    // O Work Request ID que você forneceu
    private static final String WORK_REQUEST_ID = "ocid1.queueworkrequest.oc1.sa-saopaulo-1.aaaaaaaabtvijixccnyu2o.......o25em7vq";

    public static void main(String[] args) throws IOException {
        // 1. Configurar o Provedor de Autenticação
        // Este código assume que você está usando o arquivo de configuração padrão do OCI (normalmente em ~/.oci/config)
        final String configurationFilePath = "C:\\Users\\Silvio\\.oci\\config";
        final String profile = "DEFAULT"; // Use o perfil configurado no seu arquivo config

        final AuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(configurationFilePath, profile);

        // 2. Criar o Cliente OCI Queue Admin
        // Defina a região correta (sa-saopaulo-1 para o seu OCID)
        try (QueueAdminClient queueAdminClient = QueueAdminClient.builder().build(provider)) {
            queueAdminClient.setRegion(Region.SA_SAOPAULO_1);

            // 3. Criar a Requisição
            GetWorkRequestRequest getWorkRequestRequest = GetWorkRequestRequest.builder()
                    .workRequestId(WORK_REQUEST_ID)
                    .build();

            System.out.println("Buscando detalhes da Work Request: " + WORK_REQUEST_ID);

            // 4. Executar a Chamada da API
            GetWorkRequestResponse response = queueAdminClient.getWorkRequest(getWorkRequestRequest);

            // 5. Processar e Imprimir o Identifier
            if (response.getWorkRequest() != null && response.getWorkRequest().getResources() != null) {
                List<WorkRequestResource> resources = response.getWorkRequest().getResources();

                if (!resources.isEmpty()) {
                    // O identifier do recurso criado (a Queue) é o primeiro item na lista de recursos
                    String identifier = resources.get(0).getIdentifier();
                    
                    System.out.println("---");
                    System.out.println("Status da Work Request: " + response.getWorkRequest().getStatus());
                    System.out.println("Operation Type: " + response.getWorkRequest().getOperationType());
                    System.out.println("---");
                    System.out.println("Identifier do Recurso Retornado (Queue OCID):");
                    System.out.println(identifier); 
                    System.out.println("---");
                } else {
                    System.out.println("A Work Request não retornou detalhes de recursos (Resources).");
                }
            } else {
                System.out.println("Não foi possível obter os detalhes da Work Request ou o campo 'data' está vazio.");
            }

        } catch (Exception e) {
            System.err.println("Erro ao obter os detalhes da Work Request: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

