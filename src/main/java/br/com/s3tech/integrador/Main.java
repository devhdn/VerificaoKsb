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
        // O Java vai procurar o arquivo na mesma pasta do .jar
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

        // Validação básica para garantir que o .conf não está vazio
        String urlSankhya  = props.getProperty("sankhya.url");
        String userSankhya = props.getProperty("sankhya.usuario");
        String idPendentes = props.getProperty("drive.pendentes");
        String tempoEspera = props.getProperty("tempo.espera", "30");

        if (urlSankhya == null || userSankhya == null || idPendentes == null) {
            System.err.println("[ERRO] Faltam configuracoes obrigatorias no integrador.conf!");
            System.exit(1);
        }

        System.out.println("[INFO] Servidor: " + urlSankhya);
        System.out.println("[INFO] Monitorando a cada " + tempoEspera + " segundos.");

        // INICIA O SERVIÇO PASSANDO O PACOTE "PROPS" INTEIRO
        MonitorPastas monitor = new MonitorPastas(props);
        monitor.iniciarMonitorizacao();
    }
}