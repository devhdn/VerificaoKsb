package br.com.s3tech.integrador.sankhya;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SankhyaAuthService {

    public String[] login(String baseUrl, String user, String pass) throws Exception {
        // Correção Crítica: Adicionado o /service.sbr na URL de login
        String base = baseUrl.replace("/service.sbr", "").replaceAll("/$", "");
        String urlStr = base + "/service.sbr?serviceName=MobileLoginSP.login&outputType=json";

        JsonObject nomusu = new JsonObject();
        nomusu.addProperty("$", user);

        JsonObject interno = new JsonObject();
        interno.addProperty("$", pass);

        JsonObject requestBody = new JsonObject();
        requestBody.add("NOMUSU", nomusu);
        requestBody.add("INTERNO", interno);

        JsonObject payload = new JsonObject();
        payload.addProperty("serviceName", "MobileLoginSP.login");
        payload.add("requestBody", requestBody);

        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
        }

        String cookie = conn.getHeaderField("Set-Cookie");
        String jsession = (cookie != null) ? cookie.split(";")[0] : null;

        InputStream is = (conn.getResponseCode() < 300) ? conn.getInputStream() : conn.getErrorStream();
        JsonObject jsonResp = JsonParser.parseReader(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonObject();

        if (jsonResp.has("status") && !"1".equals(jsonResp.get("status").getAsString())) {
            String msgErro = jsonResp.has("statusMessage") ? jsonResp.get("statusMessage").getAsString() : "Erro desconhecido";
            throw new Exception("Credenciais recusadas pelo Sankhya: " + msgErro);
        }

        String token = null;
        if (jsonResp.has("responseBody")) {
            JsonObject body = jsonResp.getAsJsonObject("responseBody");
            if (body.has("jsessionid")) {
                token = body.getAsJsonObject("jsessionid").get("$").getAsString();
            }
        }

        if (token == null || token.isEmpty()) {
            throw new Exception("Login aceito, mas Sankhya nao retornou token.");
        }

        return new String[]{token, jsession};
    }
}