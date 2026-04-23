package br.com.s3tech.integrador.sankhya;

import java.time.LocalDateTime;

public class SankhyaSessionManager {
    private final String baseUrl;
    private final String usuario;
    private final String senha;
    private final SankhyaAuthService authService;

    private String mgeSession;
    private String jsessionId;
    private LocalDateTime ultimaConexao;

    public SankhyaSessionManager(String baseUrl, String usuario, String senha) {
        this.baseUrl = baseUrl;
        this.usuario = usuario;
        this.senha = senha;
        this.authService = new SankhyaAuthService();
    }

    /**
     * Retorna a sessão ativa. Se não existir ou for antiga (mais de 20 min), renova.
     */
    public synchronized String[] getSessionTokens() throws Exception {
        if (mgeSession == null || ultimaConexao == null ||
                ultimaConexao.isBefore(LocalDateTime.now().minusMinutes(20))) {
            refreshSession();
        }
        return new String[]{mgeSession, jsessionId};
    }

    public synchronized void refreshSession() throws Exception {
        System.out.println("[SESSION] Solicitando nova sessao Sankhya...");
        String[] tokens = authService.login(baseUrl, usuario, senha);
        this.mgeSession = tokens[0];
        this.jsessionId = tokens[1];
        this.ultimaConexao = LocalDateTime.now();
        System.out.println("[SESSION] Logado com sucesso.");
    }

    public String getMgeSession() { return mgeSession; }
    public String getJsessionId() { return jsessionId; }
}