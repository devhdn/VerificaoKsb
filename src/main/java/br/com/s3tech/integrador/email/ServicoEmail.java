package br.com.s3tech.integrador.email;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.mail.*;
import javax.mail.internet.*;

public class ServicoEmail {

    private static Properties configGeral; // Variável que vai guardar o pacote do .conf

    // Método que estava faltando!
    public static void configurar(Properties config) {
        configGeral = config;
    }

    public static void enviarRelatorioDashboard(List<String[]> divergenciasComDados, String nomeArquivo, int totalProcessado, int sucessos) {
        if (divergenciasComDados.isEmpty() || configGeral == null) return;

        String username = configGeral.getProperty("email.user");
        String password = configGeral.getProperty("email.password");
        String destinatarios = configGeral.getProperty("email.destinatario");

        Session session = Session.getInstance(criarPropsSmtp(configGeral), new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            String template = carregarTemplateHtml();
            StringBuilder linhasHtml = new StringBuilder();

            for (String[] d : divergenciasComDados) {
                linhasHtml.append("<tr>")
                        .append("<td>").append(d.length > 0 && d[0] != null ? d[0] : "").append("</td>")
                        .append("<td>").append(d.length > 1 && d[1] != null ? d[1] : "").append("</td>")
                        .append("<td>").append(d.length > 2 && d[2] != null ? d[2] : "").append("</td>")
                        .append("<td>").append(d.length > 3 && d[3] != null ? d[3] : "").append("</td>")
                        .append("<td>").append(d.length > 4 && d[4] != null ? d[4] : "").append("</td>")
                        .append("<td>").append(d.length > 5 && d[5] != null ? d[5] : "").append("</td>")
                        .append("<td style=\"color: #c0392b; font-weight: bold;\">").append(d.length > 6 && d[6] != null ? d[6] : "").append("</td>")
                        .append("</tr>");
            }

            String htmlFinal = template
                    .replace("{{NOME_ARQUIVO}}", nomeArquivo)
                    .replace("{{TOTAL_ITENS}}", String.valueOf(totalProcessado))
                    .replace("{{SUCESSO_COUNT}}", String.valueOf(sucessos))
                    .replace("{{ERRO_COUNT}}", String.valueOf(divergenciasComDados.size()))
                    .replace("{{LINHAS_TABELA}}", linhasHtml.toString())
                    .replace("{{DATA_HORA}}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatarios));
            message.setSubject("Relatório de Integração Sankhya - " + nomeArquivo);
            message.setContent(htmlFinal, "text/html; charset=utf-8");

            Transport.send(message);
            System.out.println("[OK] Dashboard enviado para os destinatarios.");

        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao enviar dashboard por e-mail: " + e.getMessage());
        }
    }

    private static String carregarTemplateHtml() throws IOException {
        InputStream is = ServicoEmail.class.getClassLoader().getResourceAsStream("email_template.html");
        if (is == null) throw new FileNotFoundException("email_template.html nao encontrado na pasta resources.");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    private static Properties criarPropsSmtp(Properties config) {
        Properties p = new Properties();
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.host", config.getProperty("email.host"));
        p.put("mail.smtp.port", config.getProperty("email.port"));
        p.put("mail.smtp.ssl.trust", "*");
        return p;
    }
}