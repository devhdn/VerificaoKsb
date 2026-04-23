package br.com.s3tech.integrador.monitor;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.InputStream;
import java.util.Collections;

public class GoogleDriveService {
    private static final String APPLICATION_NAME = "Integrador HDN KSB";

    /**
     * Método responsável por ler o credentials.json e autenticar o robô no Google Drive.
     */
    public static Drive getService() throws Exception {
        // Busca o arquivo JSON dentro da pasta src/main/resources
        InputStream in = GoogleDriveService.class.getResourceAsStream("/credentials.json");

        if (in == null) {
            throw new RuntimeException("Arquivo credentials.json nao encontrado na pasta resources!");
        }

        // Cria as credenciais com permissão total ao Drive
        GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                .createScoped(Collections.singleton(DriveScopes.DRIVE));

        // Constrói e retorna o serviço do Drive
        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}