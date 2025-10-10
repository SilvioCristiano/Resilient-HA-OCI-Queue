package com.playbook.ai;

import java.io.*;
import java.util.Properties;

public class QueueUtils {

    private static final String FILE_PATH = "queue.properties";
    
    // Caminho absoluto do arquivo conforme solicitado.
    private static final String ABSOLUTE_PROPERTIES_PATH = "C:\\Users\\Silvio\\genai\\ResilientHAQueue\\queue.properties";
    private static final String SECONDARY_KEY = "SECONDARY_QUEUE_ID";

    public static String loadSecondaryQueueId() {
        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream(FILE_PATH)) {
            prop.load(fis);
            return prop.getProperty("SECONDARY_QUEUE_ID");
        } catch (IOException e) {
            return null;
        }
    }

    public static void saveSecondaryQueueId(String queueId) {
        Properties prop = new Properties();
        prop.setProperty("SECONDARY_QUEUE_ID", queueId);
        try (FileOutputStream fos = new FileOutputStream(FILE_PATH)) {
            prop.store(fos, "OCID da fila secundária");
            System.out.println("SECONDARY_QUEUE_ID salvo em " + FILE_PATH);
        } catch (IOException e) {
            System.err.println("Erro ao salvar SECONDARY_QUEUE_ID: " + e.getMessage());
        }
    }

    public static String loadSecondaryQueue() {
        Properties prop = new Properties();
        // Tenta carregar o arquivo a partir do caminho absoluto
        try (InputStream input = new FileInputStream(ABSOLUTE_PROPERTIES_PATH)) {

            prop.load(input);
            String ocid = prop.getProperty(SECONDARY_KEY);

            if (ocid == null || ocid.trim().isEmpty()) {
                throw new IllegalStateException("A propriedade '" + SECONDARY_KEY + "' não foi encontrada ou está vazia no arquivo " + ABSOLUTE_PROPERTIES_PATH);
            }
            System.out.println("Configuração carregada: " + SECONDARY_KEY + "=" + ocid.trim());
            return ocid.trim();

        } catch (IOException ex) {
            System.err.println("Erro fatal ao carregar o arquivo de propriedades no caminho absoluto: " + ABSOLUTE_PROPERTIES_PATH + ". Detalhe: " + ex.getMessage());
            // Lança uma RuntimeException para interromper o carregamento da aplicação
            throw new RuntimeException("Falha ao carregar a configuração da fila. Verifique se o arquivo existe no caminho especificado: " + ABSOLUTE_PROPERTIES_PATH, ex);
        } catch (IllegalStateException ex) {
            System.err.println("Erro na configuração: " + ex.getMessage());
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }
}
