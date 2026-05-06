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

    private static Properties configGeral;

    public static void configurar(Properties config) {
        configGeral = config;
    }

    // NOVO MÉTODO UNIFICADO: Recebe erros e avisos e monta o e-mail único
    public static void enviarRelatorioUnificado(List<String[]> divergencias, List<String[]> avisos, String nomeArquivo, int totalProcessado, int sucessos) {
        // Só cancela o envio se não houver NENHUM erro E NENHUM aviso
        if ((divergencias.isEmpty() && avisos.isEmpty()) || configGeral == null) return;

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

            // 1. Monta as linhas da tabela de BLOQUEIOS (Erros)
            StringBuilder htmlErros = new StringBuilder();
            if (divergencias.isEmpty()) {
                htmlErros.append("<tr><td colspan='7' style='text-align:center;'>Nenhum bloqueio encontrado.</td></tr>");
            } else {
                for (String[] d : divergencias) {
                    htmlErros.append("<tr>")
                            .append("<td>").append(d.length > 0 && d[0] != null ? d[0] : "").append("</td>")
                            .append("<td>").append(d.length > 1 && d[1] != null ? d[1] : "").append("</td>")
                            .append("<td>").append(d.length > 2 && d[2] != null ? d[2] : "").append("</td>")
                            .append("<td>").append(d.length > 3 && d[3] != null ? d[3] : "").append("</td>")
                            .append("<td>").append(d.length > 4 && d[4] != null ? d[4] : "").append("</td>")
                            .append("<td>").append(d.length > 5 && d[5] != null ? d[5] : "").append("</td>")
                            .append("<td><span class='badge badge-error'>").append(d.length > 6 && d[6] != null ? d[6] : "").append("</span></td>")
                            .append("</tr>");
                }
            }

            // 2. Monta as linhas da tabela de AVISOS DATABOOK
            StringBuilder htmlAvisos = new StringBuilder();
            if (avisos.isEmpty()) {
                htmlAvisos.append("<tr><td colspan='7' style='text-align:center;'>Nenhum prazo excedido.</td></tr>");
            } else {
                for (String[] a : avisos) {
                    htmlAvisos.append("<tr>")
                            .append("<td>").append(a.length > 0 && a[0] != null ? a[0] : "").append("</td>")
                            .append("<td>").append(a.length > 1 && a[1] != null ? a[1] : "").append("</td>")
                            .append("<td>").append(a.length > 2 && a[2] != null ? a[2] : "").append("</td>")
                            .append("<td>").append(a.length > 3 && a[3] != null ? a[3] : "").append("</td>")
                            .append("<td>").append(a.length > 4 && a[4] != null ? a[4] : "").append("</td>")
                            .append("<td>").append(a.length > 5 && a[5] != null ? a[5] : "").append("</td>")
                            .append("<td><span class='badge badge-warning'>").append(a.length > 6 && a[6] != null ? a[6] : "").append("</span></td>")
                            .append("</tr>");
                }
            }

            // 3. Substitui as variáveis no seu HTML
            String htmlFinal = template
                    .replace("{{NOME_ARQUIVO}}", nomeArquivo)
                    .replace("{{TOTAL_ITENS}}", String.valueOf(totalProcessado))
                    .replace("{{SUCESSO_COUNT}}", String.valueOf(sucessos))
                    .replace("{{ERRO_COUNT}}", String.valueOf(divergencias.size()))
                    .replace("{{AVISO_COUNT}}", String.valueOf(avisos.size()))
                    .replace("{{LINHAS_TABELA_ERROS}}", htmlErros.toString())
                    .replace("{{LINHAS_TABELA_AVISOS}}", htmlAvisos.toString())
                    .replace("{{DATA_HORA}}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

            //Saulo criou

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatarios));
            message.setSubject("Relatório de Integração Sankhya - " + nomeArquivo);
            message.setContent(htmlFinal, "text/html; charset=utf-8");

            Transport.send(message);
            System.out.println("[OK] Dashboard unificado enviado para os destinatários.");

        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao enviar dashboard por e-mail: " + e.getMessage());
        }
    }

    private static String carregarTemplateHtml() throws IOException {
        InputStream is = ServicoEmail.class.getClassLoader().getResourceAsStream("email_template.html");
        if (is == null) throw new FileNotFoundException("email_template.html não encontrado na pasta resources.");
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