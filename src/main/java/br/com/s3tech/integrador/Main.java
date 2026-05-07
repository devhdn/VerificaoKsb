package br.com.s3tech.integrador;

import br.com.s3tech.integrador.monitor.MonitorPastas;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("  INTEGRADOR SANKHYA - MODO VPS (.CONF)  ");
        System.out.println("=========================================");

        Properties props = new Properties();
        File arquivoConf = new File("integrador.conf");

        if (arquivoConf.exists()) {
            try (FileInputStream fis = new FileInputStream(arquivoConf)) {
                props.load(fis);
                System.out.println("[OK] Arquivo de configuracao carregado com sucesso.");
            } catch (Exception e) {
                System.err.println("[ERRO] Falha ao ler integrador.conf: " + e.getMessage());
                System.exit(1);
            }
        } else {
            System.err.println("[ERRO CRITICO] Arquivo 'integrador.conf' nao encontrado na pasta!");
            System.err.println("Certifique-se de que o arquivo .conf esta ao lado do .jar");
            System.exit(1);
        }

        // --- VALIDAÇÃO DAS CONFIGURAÇÕES ---
        String urlSankhya  = props.getProperty("sankhya.url");
        String userSankhya = props.getProperty("sankhya.usuario");
        String idPendentes = props.getProperty("drive.pendentes");
        String tempoEspera = props.getProperty("tempo.espera", "30");

        // NOVO: Captura o caminho do JSON definido no .conf
        String caminhoCredentials = props.getProperty("drive.credentials.path");

        // Atualizamos a validação para incluir o caminho das credenciais
        if (urlSankhya == null || userSankhya == null || idPendentes == null || caminhoCredentials == null) {
            System.err.println("[ERRO] Faltam configuracoes obrigatorias no integrador.conf!");
            System.err.println("Verifique: sankhya.url, sankhya.usuario, drive.pendentes e drive.credentials.path");
            System.exit(1);
        }

        System.out.println("[INFO] Servidor: " + urlSankhya);
        System.out.println("[INFO] Monitorando a cada " + tempoEspera + " segundos.");
        System.out.println("[INFO] Credenciais Google: " + caminhoCredentials);

        // INICIA O SERVIÇO PASSANDO O PROPS E O CAMINHO EXTERNO DO JSON
        // Certifique-se que o construtor do MonitorPastas aceita esses dois argumentos
        MonitorPastas monitor = new MonitorPastas(props, caminhoCredentials);
        monitor.iniciarMonitorizacao();
    }
}