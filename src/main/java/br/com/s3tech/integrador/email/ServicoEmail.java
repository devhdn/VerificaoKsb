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

    public static void enviarRelatorioUnificado(List<String[]> divergencias, List<String[]> avisos, String nomeArquivo, int totalProcessado, int sucessos) {
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

            // 1. Monta as linhas da tabela de BLOQUEIOS (11 colunas)
            StringBuilder htmlErros = new StringBuilder();
            if (divergencias.isEmpty()) {
                htmlErros.append("<tr><td colspan='11' style='text-align:center;'>Nenhum bloqueio encontrado.</td></tr>");
            } else {
                for (String[] d : divergencias) {
                    htmlErros.append("<tr>")
                            .append("<td class='nowrap'>").append(d[0]).append("</td>") // Ped HDN
                            .append("<td class='nowrap'>").append(d[1]).append("</td>") // Ped KSB
                            .append("<td class='nowrap'>").append(d[2]).append("</td>") // Linha
                            .append("<td class='nowrap'>").append(d[3]).append("</td>") // Referência
                            .append("<td class='nowrap'><strong>").append(d[4]).append("</strong></td>") // Cód. Sankhya (Interno)
                            .append("<td>").append(d[5]).append("</td>")               // Material
                            .append("<td class='nowrap'>").append(d[6]).append("</td>") // Qtd
                            .append("<td class='nowrap'>").append(d[7]).append("</td>") // Vlr Unit
                            .append("<td class='nowrap'>").append(d[8]).append("</td>") // Emissão
                            .append("<td class='nowrap'>").append(d[9]).append("</td>") // Reprogramada
                            .append("<td><span class='badge badge-error'>").append(d[10]).append("</span></td>") // Motivo
                            .append("</tr>");
                }
            }

            // 2. Monta as linhas da tabela de AVISOS DATABOOK (11 colunas)
            StringBuilder htmlAvisos = new StringBuilder();
            if (avisos.isEmpty()) {
                htmlAvisos.append("<tr><td colspan='11' style='text-align:center;'>Nenhum alerta de databook.</td></tr>");
            } else {
                for (String[] a : avisos) {
                    htmlAvisos.append("<tr>")
                            .append("<td class='nowrap'>").append(a[0]).append("</td>")
                            .append("<td class='nowrap'>").append(a[1]).append("</td>")
                            .append("<td class='nowrap'>").append(a[2]).append("</td>")
                            .append("<td class='nowrap'>").append(a[3]).append("</td>")
                            .append("<td class='nowrap'><strong>").append(a[4]).append("</strong></td>") // Cód. Sankhya (Interno)
                            .append("<td>").append(a[5]).append("</td>")
                            .append("<td class='nowrap'>").append(a[6]).append("</td>")
                            .append("<td class='nowrap'>").append(a[7]).append("</td>")
                            .append("<td class='nowrap'>").append(a[8]).append("</td>")
                            .append("<td class='nowrap'>").append(a[9]).append("</td>")
                            .append("<td><span class='badge badge-warning'>").append(a[10]).append("</span></td>")
                            .append("</tr>");
                }
            }

            String htmlFinal = template
                    .replace("{{NOME_ARQUIVO}}", nomeArquivo)
                    .replace("{{TOTAL_ITENS}}", String.valueOf(totalProcessado))
                    .replace("{{SUCESSO_COUNT}}", String.valueOf(sucessos))
                    .replace("{{ERRO_COUNT}}", String.valueOf(divergencias.size()))
                    .replace("{{AVISO_COUNT}}", String.valueOf(avisos.size()))
                    .replace("{{LINHAS_TABELA_ERROS}}", htmlErros.toString())
                    .replace("{{LINHAS_TABELA_AVISOS}}", htmlAvisos.toString())
                    .replace("{{DATA_HORA}}", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatarios));
            message.setSubject("Relatório de Integração Sankhya - " + nomeArquivo);
            message.setContent(htmlFinal, "text/html; charset=utf-8");

            Transport.send(message);
            System.out.println("[OK] Dashboard unificado enviado com todas as colunas.");

        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao enviar dashboard: " + e.getMessage());
        }
    }

    private static String carregarTemplateHtml() throws IOException {
        InputStream is = ServicoEmail.class.getClassLoader().getResourceAsStream("email_template.html");
        if (is == null) throw new FileNotFoundException("email_template.html não encontrado.");
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