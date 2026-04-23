package br.com.s3tech.integrador.sankhya;

import com.google.gson.*;

public class SankhyaItemRepository {
    private final SankhyaHttpService http;
    private final String baseUrl;

    public SankhyaItemRepository(SankhyaHttpService http, String baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl.replace("/service.sbr", "").replaceAll("/$", "");
    }

    public static class DadosItemSankhya {
        public String sequencia;
        public double valorUnitario;
        public double quantidade;

        public DadosItemSankhya(String seq, double vlr, double qtd) {
            this.sequencia = seq;
            this.valorUnitario = vlr;
            this.quantidade = qtd;
        }
    }

    public DadosItemSankhya buscarDadosItem(String mgeSession, String jsessionId, String nunota, String codBusca) throws Exception {
        String notaLimpa = limparNumeroNota(nunota);
        String codOriginal = limparCodigo(codBusca);
        String codSemZero = codOriginal.replaceFirst("^0+(?!$)", "");

        if (notaLimpa.isEmpty()) {
            throw new IllegalArgumentException("NUNOTA invalido ou vazio.");
        }

        JsonObject criteria = new JsonObject();
        criteria.addProperty("expression", "this.NUNOTA = ?");

        JsonArray parameters = new JsonArray();
        JsonObject param = new JsonObject();
        param.addProperty("type", "N");
        param.addProperty("value", Integer.parseInt(notaLimpa));
        parameters.add(param);
        criteria.add("parameters", parameters);

        JsonArray fields = new JsonArray();
        fields.add("SEQUENCIA");
        fields.add("CODPROD");
        fields.add("AD_CODORIGINAL");
        fields.add("VLRUNIT");
        fields.add("QTDNEG");

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("dataSetID", "00D");
        requestBody.addProperty("entityName", "ItemNota");
        requestBody.addProperty("standAlone", false);
        requestBody.addProperty("orderByExpression", "SEQUENCIA");
        requestBody.add("fields", fields);
        requestBody.addProperty("tryJoinedFields", true);
        requestBody.addProperty("crudListener", "br.com.sankhya.modelcore.comercial.ItemNotaCrudListener");
        requestBody.add("criteria", criteria);

        JsonObject payload = new JsonObject();
        payload.addProperty("serviceName", "DatasetSP.loadRecords");
        payload.add("requestBody", requestBody);

        String url = baseUrl + "/service.sbr?serviceName=DatasetSP.loadRecords&outputType=json&mgeSession=" + mgeSession;
        String resp = http.post(url, jsessionId, payload.toString());

        JsonObject jsonResp = JsonParser.parseString(resp).getAsJsonObject();

        if (jsonResp.has("status") && !"1".equals(jsonResp.get("status").getAsString())) {
            String msgErro = jsonResp.has("statusMessage") ? jsonResp.get("statusMessage").getAsString() : "Erro desconhecido";
            throw new Exception("Falha na busca (DatasetSP): " + msgErro);
        }

        if (jsonResp.has("responseBody")) {
            JsonObject body = jsonResp.getAsJsonObject("responseBody");
            if (body.has("result") && body.getAsJsonArray("result").size() > 0) {
                JsonArray rows = body.getAsJsonArray("result");

                for (JsonElement rowElement : rows) {
                    JsonArray row = rowElement.getAsJsonArray();

                    String rowSeq       = row.get(0).isJsonNull() ? "" : row.get(0).getAsString();
                    String rowCodProd   = row.get(1).isJsonNull() ? "" : row.get(1).getAsString();
                    String rowAdCodOrig = row.get(2).isJsonNull() ? "" : row.get(2).getAsString();

                    if (matchCodigo(rowCodProd, codOriginal, codSemZero) ||
                            matchCodigo(rowAdCodOrig, codOriginal, codSemZero)) {

                        double vlr = row.get(3).isJsonNull() ? 0.0 : row.get(3).getAsDouble();
                        double qtd = row.get(4).isJsonNull() ? 0.0 : row.get(4).getAsDouble();

                        return new DadosItemSankhya(rowSeq, vlr, qtd);
                    }
                }
            }
        }
        return null;
    }

    private boolean matchCodigo(String valorBanco, String codOriginal, String codSemZero) {
        if (valorBanco == null || valorBanco.isEmpty()) return false;
        String val = valorBanco.trim();
        return val.equals(codOriginal) || val.equals(codSemZero);
    }

    public boolean atualizarCampos(String mgeSession, String jsessionId, String nunota, String sequencia, String linhaKsb, String dtPrev, String motivo) throws Exception {
        String notaLimpa = limparNumeroNota(nunota);

        String motivoSeguro = motivo;
        if (motivoSeguro != null && motivoSeguro.length() > 30) {
            motivoSeguro = motivoSeguro.substring(0, 30);
        }

        String baseComercial = baseUrl;
        if (baseComercial.endsWith("/mge")) {
            baseComercial = baseComercial.substring(0, baseComercial.length() - 4) + "/mgecom";
        }
        String url = baseComercial + "/service.sbr?serviceName=CACSP.incluirAlterarItemNota&outputType=json&mgeSession=" + mgeSession;

        // ==========================================================
        // PASSO 1: ATUALIZAR APENAS O MOTIVO (Para enganar a trigger)
        // ==========================================================
        JsonObject itemPasso1 = new JsonObject();
        itemPasso1.add("NUNOTA", createVal(notaLimpa));
        itemPasso1.add("SEQUENCIA", createVal(sequencia));
        itemPasso1.add("AD_MOTIVOPREVENT", createVal(motivoSeguro));
        itemPasso1.add("MOTIVOPREVENT", createVal(motivoSeguro));

        JsonObject itens1 = new JsonObject();
        itens1.addProperty("ATUALIZACAO_ONLINE", "false");
        itens1.add("item", itemPasso1);

        JsonObject nota1 = new JsonObject();
        nota1.addProperty("NUNOTA", notaLimpa);
        nota1.addProperty("AD_MOTIVOPREVENT", motivoSeguro);
        nota1.add("itens", itens1);

        JsonObject req1 = new JsonObject();
        req1.add("nota", nota1);
        JsonObject payload1 = new JsonObject();
        payload1.addProperty("serviceName", "CACSP.incluirAlterarItemNota");
        payload1.add("requestBody", req1);

        String resp1 = http.post(url, jsessionId, payload1.toString());
        JsonObject jsonResp1 = JsonParser.parseString(resp1).getAsJsonObject();

        if (jsonResp1.has("status") && !"1".equals(jsonResp1.get("status").getAsString())) {
            String msgErro = jsonResp1.has("statusMessage") ? jsonResp1.get("statusMessage").getAsString() : "Erro desconhecido";
            throw new Exception("Erro ao salvar Motivo (Passo 1): " + msgErro);
        }

        // ==========================================================
        // PASSO 2: ATUALIZAR A DATA (DTINICIO) E A LINHA KSB
        // ==========================================================
        JsonObject itemPasso2 = new JsonObject();
        itemPasso2.add("NUNOTA", createVal(notaLimpa));
        itemPasso2.add("SEQUENCIA", createVal(sequencia));
        itemPasso2.add("AD_LINKSB", createVal(linhaKsb));

        // --- CORREÇÃO DA DATA: USANDO O CAMPO DTINICIO ---
        itemPasso2.add("DTINICIO", createVal(dtPrev));

        // Mantemos também o AD_DTPREVFOR por precaução, caso ele grave nos dois
        itemPasso2.add("AD_DTPREVFOR", createVal(dtPrev));

        JsonObject itens2 = new JsonObject();
        itens2.addProperty("ATUALIZACAO_ONLINE", "false");
        itens2.add("item", itemPasso2);

        JsonObject nota2 = new JsonObject();
        nota2.addProperty("NUNOTA", notaLimpa);
        nota2.add("itens", itens2);

        JsonObject req2 = new JsonObject();
        req2.add("nota", nota2);
        JsonObject payload2 = new JsonObject();
        payload2.addProperty("serviceName", "CACSP.incluirAlterarItemNota");
        payload2.add("requestBody", req2);

        String resp2 = http.post(url, jsessionId, payload2.toString());
        JsonObject jsonResp2 = JsonParser.parseString(resp2).getAsJsonObject();

        if (jsonResp2.has("status") && !"1".equals(jsonResp2.get("status").getAsString())) {
            String msgErro = jsonResp2.has("statusMessage") ? jsonResp2.get("statusMessage").getAsString() : "Erro desconhecido";
            throw new Exception("Erro ao salvar Data/Linha (Passo 2): " + msgErro);
        }

        return true;
    }

    private JsonObject createVal(String val) {
        JsonObject obj = new JsonObject();
        obj.addProperty("$", val != null ? val : "");
        return obj;
    }

    private String limparCodigo(String cod) {
        if (cod == null) return "";
        String s = cod.trim();
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s;
    }

    private String limparNumeroNota(String nunota) {
        if (nunota == null) return "";
        String s = nunota.trim();
        if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
        return s.replaceAll("[^0-9]", "");
    }

    public void atualizarCabecalho(String mgeSession, String jsessionId, String nuNota, String valorPedForn) throws Exception {
        String url = baseUrl + "/service.sbr?serviceName=CRUDServiceProvider.saveRecord&outputType=json&mgeSession=" + mgeSession;

        // Usa o método que já existe para garantir que o número da nota está perfeitamente limpo (sem .0 ou letras)
        String notaLimpa = limparNumeroNota(nuNota);

        // Monta o JSON (Payload) na estrutura estrita exigida pelo Sankhya para um UPDATE (Atualização)
        String payload = "{\n" +
                "  \"serviceName\": \"CRUDServiceProvider.saveRecord\",\n" +
                "  \"requestBody\": {\n" +
                "    \"dataSet\": {\n" +
                "      \"rootEntity\": \"CabecalhoNota\",\n" +
                "      \"includePresentationFields\": \"N\",\n" +
                "      \"entity\": {\n" +
                "        \"fieldset\": {\n" +
                "          \"list\": \"AD_NUMPEDFORN\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"dataRow\": {\n" +
                "        \"localFields\": {\n" +
                "          \"AD_NUMPEDFORN\": { \"$\": \"" + valorPedForn + "\" }\n" +
                "        },\n" +
                "        \"key\": {\n" +
                "          \"NUNOTA\": { \"$\": \"" + notaLimpa + "\" }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        String resposta = http.post(url, jsessionId, payload);

        // Verifica se o Sankhya retornou erro (status = 0)
        if (resposta.contains("\"status\":\"0\"") || resposta.contains("status=\"0\"")) {
            throw new Exception(resposta);
        }
    }
}