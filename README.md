# 🚀 Integrador Sankhya - Monitoramento KSB (Google Drive)

Este projeto é uma aplicação Java *Standalone* (desenhada para rodar em uma VPS) que atua como um robô de integração. Ele monitora planilhas recebidas via Google Drive, processa os dados, aplica regras de negócio financeiras e de prazos, e atualiza pedidos de compra/venda diretamente no ERP Sankhya através de integrações REST (JSON).

---

## 🎯 Arquitetura e Fluxo de Trabalho

O robô opera em um ciclo contínuo (loop infinito) baseado no tempo de espera configurado:

1. **Varredura (Google Drive):** Verifica a pasta "Pendentes" em busca de novos arquivos (Excel/CSV).
2. **Download Temporário:** Baixa o arquivo para a memória da VPS.
3. **Leitura Inteligente:** Utiliza o *Apache POI* para ler os dados, formatando datas nativas do Excel de forma segura.
4. **Validação Sankhya (Item a Item):**
    * Busca o pedido/item no ERP (`NUNOTA` e `CODPROD`).
    * **Trava Financeira:** Valida se a quantidade ou valor divergem em mais de 5%. Se sim, **bloqueia** a atualização e gera um erro.
    * **Databook:** Verifica a regra de prazos (`AD_DTBKKS`). Se a diferença de datas exceder o limite, gera um **aviso**, mas *não bloqueia* a atualização.
5. **Atualização ERP:** Se passar nas travas, atualiza `AD_MOTIVOPREVENT`, `AD_LINKSB`, `AD_DTPREVFOR` no Item e `AD_NUMPEDFORN` no Cabeçalho da Nota.
6. **Notificação:** Envia um dashboard unificado por e-mail, separando Erros (Bloqueios) de Avisos.
7. **Organização:** Renomeia a planilha com um prefixo (`[OK]_` ou `[ERRO]_`) + *Timestamp* e a move para a respectiva pasta no Google Drive.

---

## 🛠️ Tecnologias Utilizadas

* **Java 8+** (Lógica principal)
* **Google API Client for Java** (Comunicação com o Google Drive)
* **Apache POI** (Leitura de arquivos Excel `.xlsx` / `.xls`)
* **Sankhya API / JAPE** (Consumo de serviços `DatasetSP`, `MobileLoginSP` e `CACSP`)
* **JavaMail / Jakarta Mail** (Envio de relatórios HTML autenticados)
* **Gson** (Manipulação e construção rápida de payloads JSON)

---

## ⚙️ Configuração do Ambiente (VPS)

Para que o integrador funcione corretamente no servidor, ele depende de arquivos de configuração externos, garantindo que credenciais não fiquem hardcoded no código fonte.

### 1. `integrador.conf`
Crie um arquivo chamado `integrador.conf` **na mesma pasta** onde o seu arquivo `.jar` será executado. Ele deve conter a estrutura abaixo:

```ini
# Configurações do Sankhya
sankhya.url=http://seuservidor:8280
sankhya.usuario=SEU_USUARIO_SANKHYA
sankhya.senha=SUA_SENHA_SANKHYA

# Configurações do Google Drive (IDs das pastas na URL)
drive.pendentes=ID_DA_PASTA_PENDENTES_AQUI
drive.feitas=ID_DA_PASTA_FEITAS_AQUI
drive.erros=ID_DA_PASTA_ERROS_AQUI
tempo.espera=30

# Configurações de E-mail (SMTP)
email.host=smtp.seudominio.com.br
email.port=587
email.user=seu_email@dominio.com.br
email.password=SUA_SENHA_DE_APP
email.destinatario=destinatario1@dominio.com,destinatario2@dominio.com