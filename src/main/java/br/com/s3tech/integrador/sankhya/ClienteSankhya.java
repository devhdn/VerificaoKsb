package br.com.s3tech.integrador.sankhya;

import br.com.s3tech.integrador.email.ServicoEmail;
import java.text.SimpleDateFormat;
import java.util.*;

public class ClienteSankhya {
    private final String baseUrl;
    private final SankhyaHttpService httpService;
    private final SankhyaSessionManager sessionManager;
    private final SankhyaItemRepository repository;

    public ClienteSankhya(Properties config) {
        this.baseUrl = config.getProperty("sankhya.url");
        String usuario = config.getProperty("sankhya.usuario");
        String senha = config.getProperty("sankhya.senha");

        ServicoEmail.configurar(config); // Injeta as configs no serviço de e-mail

        this.httpService = new SankhyaHttpService();
        this.sessionManager = new SankhyaSessionManager(this.baseUrl, usuario, senha);
        this.repository = new SankhyaItemRepository(httpService, baseUrl);
    }

    public boolean verificarEAtualizarDados(List<String[]> dados, String nomeArquivo) {
        List<String[]> listaDivergencias = new ArrayList<>();
        int atualizados = 0;
        int totalUteis = 0;

        try {
            sessionManager.getSessionTokens(); // Garante token inicial válido

            for (String[] linha : dados) {
                if (isLinhaInvalida(linha)) continue;
                totalUteis++;

                String nuNota = linha[5].trim();
                String codProd = linha[9].trim();
                System.out.print("[INFO] Analisando Pedido " + nuNota + " | Mat " + codProd + "... ");

                try {
                    // 1. Busca os dados no ERP (com reconexão automática se cair)
                    SankhyaItemRepository.DadosItemSankhya item = buscarItemComRetry(nuNota, codProd);

                    if (item == null) {
                        System.out.println("NÃO LOCALIZADO NO ERP!");
                        registrarDivergencia(listaDivergencias, linha, "Item não encontrado no pedido Sankhya.");
                        continue;
                    }

                    // 2. Trava Financeira (Qtd e Preço)
                    String erroTrava = verificarTravaFinanceira(item, linha);
                    if (erroTrava != null) {
                        System.out.println("-> BLOQUEADO PELA TRAVA FINANCEIRA!");
                        registrarDivergencia(listaDivergencias, linha, "[ATUALIZAÇÃO BLOQUEADA] " + erroTrava);
                        continue;
                    }

                    // 3. Salva no Sankhya
                    atualizarItemComRetry(item, linha, nomeArquivo);
                    atualizados++;
                    System.out.println("OK!");

                } catch (Exception e) {
                    String erroLimpo = limparMensagemErro(e.getMessage());
                    if (erroLimpo.toLowerCase().contains("faturado")) {
                        System.out.println("IGNORADO (Já Entregue/Faturado)");
                    } else {
                        System.out.println("ERRO: " + erroLimpo);
                        registrarDivergencia(listaDivergencias, linha, erroLimpo);
                    }
                }
            }

            ServicoEmail.enviarRelatorioDashboard(listaDivergencias, nomeArquivo, totalUteis, atualizados);
            return true;

        } catch (Exception e) {
            System.err.println("\n[ERRO CRÍTICO] " + e.getMessage());
            return false;
        }
    }

    // ========================================================================
    // MÉTODOS AUXILIARES (Deixam o método principal menor e mais limpo)
    // ========================================================================

    private SankhyaItemRepository.DadosItemSankhya buscarItemComRetry(String nuNota, String codProd) throws Exception {
        try {
            return repository.buscarDadosItem(sessionManager.getMgeSession(), sessionManager.getJsessionId(), nuNota, codProd);
        } catch (Exception e) {
            if (isErroSessao(e)) {
                System.out.print("[Reconectando...] ");
                sessionManager.refreshSession();
                return repository.buscarDadosItem(sessionManager.getMgeSession(), sessionManager.getJsessionId(), nuNota, codProd);
            }
            throw e;
        }
    }

    private void atualizarItemComRetry(SankhyaItemRepository.DadosItemSankhya item, String[] linha, String nomeArquivo) throws Exception {
        String nuNota = linha[5].trim();
        String linhaKSB = linha[4].trim();
        String dtReprog = formatarData(linha[7].trim());
        String motivoEstatico = "Verificação KSB " + nomeArquivo;

        // AQUI: Capturamos o valor da planilha (Se for o "Documentos de Vendas", mude para linha[3])
        String valorCabecalho = linha[3].trim();

        try {
            // 1. Atualiza os itens da nota (TGFITE) - Já existia
            repository.atualizarCampos(sessionManager.getMgeSession(), sessionManager.getJsessionId(), nuNota, item.sequencia, linhaKSB, dtReprog, motivoEstatico);

            // 2. NOVO: Atualiza o cabeçalho da nota (TGFCAB) com o Pedido do Fornecedor
            repository.atualizarCabecalho(sessionManager.getMgeSession(), sessionManager.getJsessionId(), nuNota, valorCabecalho);

        } catch (Exception e) {
            if (isErroSessao(e)) {
                System.out.print("[Reconectando...] ");
                sessionManager.refreshSession();

                // Tenta novamente após renovar o token
                repository.atualizarCampos(sessionManager.getMgeSession(), sessionManager.getJsessionId(), nuNota, item.sequencia, linhaKSB, dtReprog, motivoEstatico);
                repository.atualizarCabecalho(sessionManager.getMgeSession(), sessionManager.getJsessionId(), nuNota, valorCabecalho);
            } else {
                throw e; // Se não for erro de sessão, repassa o erro para ir para o painel de divergências
            }
        }
    }

    private String verificarTravaFinanceira(SankhyaItemRepository.DadosItemSankhya item, String[] linha) {
        double qtdExcel = converterDoubleBR(linha[8].trim());
        double vlrExcel = converterDoubleBR(linha[14].trim());
        StringBuilder motivo = new StringBuilder();

        if (Math.abs(item.quantidade - qtdExcel) > 0.01) {
            motivo.append(String.format("Qtd divergente (ERP: %.2f | Plan: %.2f). ", item.quantidade, qtdExcel));
        }

        double limiteTolerancia = item.valorUnitario * 0.05;
        if (Math.abs(item.valorUnitario - vlrExcel) > limiteTolerancia) {
            motivo.append(String.format("Preço divergente > 5%% (ERP: R$ %.2f | Plan: R$ %.2f). ", item.valorUnitario, vlrExcel));
        }

        return motivo.length() > 0 ? motivo.toString().trim() : null;
    }

    private void registrarDivergencia(List<String[]> lista, String[] linha, String mensagem) {
        lista.add(new String[]{
                linha[5].trim(), // nuNota
                linha[3].trim(), // pedidoKSB
                linha[4].trim(), // linhaKSB
                linha[10].trim(), // descricaoMat
                formatarData(linha[6].trim()), // emissaoOV
                formatarData(linha[7].trim()), // dtReprog
                mensagem // motivo
        });
    }

    private boolean isLinhaInvalida(String[] linha) {
        return linha.length < 15 || linha[5] == null || linha[5].trim().isEmpty() || linha[5].trim().equalsIgnoreCase("Nº. Pedido do Cliente");
    }

    private boolean isErroSessao(Exception e) {
        String msg = e.getMessage().toLowerCase();
        return msg.contains("sessao") || msg.contains("autorizado") || msg.contains("expired");
    }

    private String limparMensagemErro(String msg) {
        return msg == null ? "" : msg.replace("Erro ao salvar Motivo (Passo 1): ", "").replace("Erro ao salvar Data/Linha (Passo 2): ", "").replace("Erro ao atualizar item (CACSP): ", "");
    }

    private String formatarData(String dataStr) {
        try {
            if (dataStr == null || dataStr.trim().isEmpty()) return "";
            dataStr = dataStr.trim();

            // 1. NOVO: Identifica formato Internacional de Base de Dados (ex: 2026-05-15)
            if (dataStr.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
                String[] partes = dataStr.split("-");
                // Inverte de YYYY-MM-DD para DD/MM/YYYY
                return partes[2] + "/" + partes[1] + "/" + partes[0];
            }

            // 2. Identifica se é um número serial do Excel (ex: 46128, 46128.0)
            if (dataStr.matches("^\\d+([.,]\\d+)?$")) {
                double dias = Double.parseDouble(dataStr.replace(",", "."));
                Calendar cal = Calendar.getInstance();
                cal.set(1899, Calendar.DECEMBER, 30); // Base do Excel
                cal.add(Calendar.DATE, (int) dias);
                return new SimpleDateFormat("dd/MM/yyyy").format(cal.getTime());
            }

            // 3. Trata datas normais (DD/MM/YY ou DD-MM-YY)
            dataStr = dataStr.replace("-", "/");
            if (dataStr.contains("/")) {
                String[] partes = dataStr.split("/");
                if (partes.length == 3) {
                    // Se o ano tiver apenas 2 dígitos (ex: 26)
                    if (partes[2].length() == 2) {
                        int ano = Integer.parseInt(partes[2]);
                        String anoCorrigido = (ano > 50 ? "19" : "20") + String.format("%02d", ano);
                        return String.format("%02d/%02d/%s",
                                Integer.parseInt(partes[0]),
                                Integer.parseInt(partes[1]),
                                anoCorrigido);
                    }
                }
            }

            // 4. Se já for uma data normal (DD/MM/YYYY), só retorna
            return dataStr;

        } catch (Exception e) {
            // Em caso de falha, devolve como veio para não travar o robô
            return dataStr;
        }
    }

    private double converterDoubleBR(String valorStr) {
        try {
            if (valorStr == null || valorStr.trim().isEmpty()) return 0.0;
            String limpo = valorStr.replaceAll("[^0-9.,]", "").trim();
            if (limpo.contains(",") && limpo.contains(".")) {
                limpo = limpo.lastIndexOf(",") > limpo.lastIndexOf(".") ? limpo.replace(".", "").replace(",", ".") : limpo.replace(",", "");
            } else if (limpo.contains(",")) {
                limpo = limpo.replace(",", ".");
            }
            return Double.parseDouble(limpo);
        } catch (Exception e) { return 0.0; }
    }
}