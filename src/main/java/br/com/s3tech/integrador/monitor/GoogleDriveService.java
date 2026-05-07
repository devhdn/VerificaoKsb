package br.com.s3tech.integrador.monitor;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;

public class GoogleDriveService {
    private static final String APPLICATION_NAME = "Integrador HDN KSB";

    /**
     * Agora o método recebe o caminho externo do JSON
     */
    public static Drive getService(String caminhoJson) throws Exception {
        java.io.File file = new java.io.File(caminhoJson);

        if (!file.exists()) {
            throw new RuntimeException("Arquivo credentials.json não encontrado em: " + caminhoJson);
        }

        // Usa FileInputStream para ler o arquivo fora do JAR
        try (java.io.InputStream in = new java.io.FileInputStream(file)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(in)
                    .createScoped(Collections.singleton(DriveScopes.DRIVE));

            return new Drive.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
    }
}