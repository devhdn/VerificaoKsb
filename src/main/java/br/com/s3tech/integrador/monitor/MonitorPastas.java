package br.com.s3tech.integrador.monitor;

import br.com.s3tech.integrador.processador.LeitorExcelCsv;
import br.com.s3tech.integrador.sankhya.ClienteSankhya;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

public class MonitorPastas {
    private final String idPendentes, idFeitas, idErros;
    private final int tempoEsperaSegundos;
    private final LeitorExcelCsv leitor = new LeitorExcelCsv();
    private final ClienteSankhya clienteSankhya;
    private Drive drive;

    // Construtor recebe o pacote completo de configurações lido do .conf
    public MonitorPastas(Properties config) {
        this.idPendentes = config.getProperty("drive.pendentes");
        this.idFeitas = config.getProperty("drive.feitas");
        this.idErros = config.getProperty("drive.erros");
        this.tempoEsperaSegundos = Integer.parseInt(config.getProperty("tempo.espera", "30"));

        // Repassa o pacote de propriedades para o Sankhya/Email se virar com o resto
        this.clienteSankhya = new ClienteSankhya(config);
    }

    public void iniciarMonitorizacao() {
        System.out.println("[INFO] Monitoramento Google Drive iniciado. Aguardando planilhas...");

        while (true) {
            try {
                if (drive == null) drive = GoogleDriveService.getService();

                // Busca arquivos na pasta "pendentes"
                FileList result = drive.files().list()
                        .setQ("'" + idPendentes + "' in parents and trashed = false")
                        .setFields("files(id, name)")
                        .execute();

                List<File> files = result.getFiles();

                for (File googleFile : files) {
                    String nomeOriginal = googleFile.getName();
                    System.out.println("\n[DETETADO NA NUVEM] Arquivo: " + nomeOriginal);

                    // 1. Download Temporário para a VPS
                    Path tempFile = Files.createTempFile("sankhya_", nomeOriginal);
                    try (FileOutputStream outputStream = new FileOutputStream(tempFile.toFile())) {
                        drive.files().get(googleFile.getId()).executeMediaAndDownloadTo(outputStream);
                    }

                    // 2. Processamento (Sankhya + Email)
                    List<String[]> dados = leitor.processarFicheiro(tempFile);
                    boolean sucesso = (dados != null && !dados.isEmpty()) &&
                            clienteSankhya.verificarEAtualizarDados(dados, nomeOriginal);

                    // 3. Monta o novo nome com Carimbo de Tempo e Status
                    String carimboTempo = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String prefixo = sucesso ? "[OK]_" : "[ERRO]_";
                    String novoNomeArquivo = prefixo + carimboTempo + "_" + nomeOriginal;

                    // 4. Mover e Renomear no Google Drive
                    String novaPastaDestino = sucesso ? idFeitas : idErros;
                    moverERenomearArquivoNoDrive(googleFile.getId(), idPendentes, novaPastaDestino, novoNomeArquivo);

                    // Limpa o arquivo da memória da VPS
                    Files.deleteIfExists(tempFile);
                    System.out.println("[INFO] Arquivo movido e renomeado para: " + novoNomeArquivo);
                }

                // Pausa antes da próxima varredura
                Thread.sleep(tempoEsperaSegundos * 1000L);

            } catch (Exception e) {
                System.err.println("[ERRO DRIVE] " + e.getMessage());
                try { Thread.sleep(10000); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Move o arquivo de pasta e atualiza o nome dele simultaneamente na API do Google Drive
     */
    private void moverERenomearArquivoNoDrive(String fileId, String oldParent, String newParent, String novoNome) throws Exception {
        File metadadosAtualizacao = new File();
        metadadosAtualizacao.setName(novoNome);

        drive.files().update(fileId, metadadosAtualizacao)
                .setRemoveParents(oldParent)
                .setAddParents(newParent)
                .execute();
    }
}