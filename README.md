# Plataforma de Estudos com Inteligência Artificial

[![CI](https://github.com/GabrielVarela2004/meu-ambiente-de-estudos/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/GabrielVarela2004/meu-ambiente-de-estudos/actions/workflows/ci.yml)

Plataforma pessoal para organização e aprofundamento de estudos baseada em materiais próprios (estilo *NotebookLM*). O sistema organiza os estudos em blocos independentes, importa documentos em múltiplos formatos e transcrições de vídeos do YouTube, fundamenta o chat com IA e a geração de questões exclusivamente no conteúdo ativo do estudante, rastreia retenção por repetição espaçada e mapa de tópicos.

O projeto foi construído sobre uma **Arquitetura Modular Inspirada em Hexagonal/Ports & Adapters**, com persistência em **PostgreSQL 17**, versionamento de schema via **Flyway** (migrações V1 a V21), backend em **Java 21 / Spring Boot 4** e frontend reativo em **React 19 / TypeScript**.

---

## 0. Estado Atual do Projeto (auditoria de 29/10/2026)

> O relatório completo está em [`docs/AUDITORIA.md`](docs/AUDITORIA.md). Leia-o antes de evoluir o projeto.

**Funciona de ponta a ponta:** blocos de estudo; fontes por texto e YouTube; chat do bloco e universal com
histórico e citações; geração de exercícios; responder questões teóricas; painel de Desempenho Global; mapeamento
de tópicos; preferências e busca manual de vagas.

**Problemas conhecidos (principais):**
- O frontend chama **rotas ou formatos diferentes** dos expostos pelo backend em 11 ações: restaurar bloco, gerar
  simulado, ativar fonte no chat, upload de arquivo, responder e adiar na tela de revisão, enviar resposta de
  exercício prático, "buscar agora", status de vaga, avaliar competências e ativar fonte de vagas. Detalhes na tabela 4.2 da auditoria.
- O agendamento automático de vagas está ativo (`app.scheduling.enabled`, padrão `true`).
- Não há autenticação; o frontend não envia `X-Study-User-Id` (todo uso cai em `default-user`).
- Upload limitado a **10 MB** para PDF, DOCX e PPTX e **2 MB** para texto e código-fonte (`UPLOAD_MAX_FILE_SIZE` / `UPLOAD_MAX_REQUEST_SIZE`).
- **Fórum** está oculto do menu (era apenas visual). **Metas e Sessões** têm tela própria na barra lateral.
- **IA com vários provedores:** OpenAI, Anthropic (Claude) e Google Gemini. Em Configurações › Provedores de IA escolhe-se o provedor de cada operação (chat, questões, exercícios, tópicos), o fallback, o modelo e o limite diário; o consumo dos últimos 7 dias aparece na mesma tela. As chaves ficam só no `.env` (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`).
- Plataforma de **uso individual** (sem login).
- Padronização visual concluída: tokens em `styles/tokens.css`, componentes em `styles/components.css` e estilos por página em `styles/pages/`.

### Estrutura do repositório

```
backend/    Spring Boot (pacotes por domínio: studyblock, material, chat, question, exercise,
            knowledge, learning, goal, studysession, security, career)
frontend/   React + Vite
  src/App.tsx              estado global e troca de páginas (sem React Router)
  src/components/Sidebar   navegação
  src/components/ui/       Button, IconButton, FormField, PageHeader, Spinner
  src/styles/tokens.css    cores, espaçamentos, tipografia e camadas (fonte única)
  src/styles/components.css design system: botões, campos, cards, abas, badges, alertas, modais, tabelas
  src/App.css              layout (shell, sidebar, drawer do Tutor IA)
docs/       auditoria e documentação técnica
```

### Design system (frontend)
- Todas as cores ficam em `src/styles/tokens.css`. **Não use cores hexadecimais diretamente nas telas.**
- Botões: `btn btn-primary | btn-secondary | btn-ghost | btn-danger | btn-ghost-danger`, tamanhos `btn-sm` e `btn-xs`.
  Não existe estilo global para `<button>`: todo botão precisa de uma classe.
- Campos: `form-input`, `form-select`, `form-textarea`, agrupados em `form-field-group`.
- Tutor IA: botão flutuante abre um drawer lateral (440px; tela cheia no celular), que fecha com Esc ou clique fora.
- Em telas de até 768px a Sidebar vira menu deslizante, aberto pelo botão da barra superior.

---

## 1. Arquitetura da Integração com IA (OpenAI)

A integração com a OpenAI segue rigorosamente os princípios de baixo acoplamento e segurança:

```
[ Frontend (React/TypeScript) ]
          │  (Apenas endpoints da aplicação - nenhuma chave secreta no cliente)
          ▼
[ REST Controllers (/api/chat, /api/questions, etc.) ]
          │
[ Application Services (OpenAiChatService, AiQuestionGeneratorService) ]
          │
          ▼
[ Porta do Domínio: AiTutorProvider ]  ◄── Abstração agnóstica de provedor
          │
          ▼
[ Adaptador: OpenAiTutorProvider ]
    ├── HttpClient nativo Java (timeouts configuráveis)
    ├── Sliding Window Rate Limiter local (thread-safe, anti-estouro de cota)
    ├── Sanitização e delimitação de dados (anti-Prompt Injection)
    ├── JSON Object Mode estruturado
    └── Mapeamento resiliente de códigos de erro HTTP
```

### Características de Segurança e Resiliência
- **Chave Oculta**: `OPENAI_API_KEY` reside exclusivamente no backend; nunca é enviada ao navegador, nunca é impressa em logs e nunca é exposta em payloads de erro.
- **Desacoplamento Transacional**: as chamadas de rede à OpenAI são executadas **fora** de transações `@Transactional`, impedindo o bloqueio de conexões do pool JDBC do PostgreSQL durante a latência da API.
- **Isolamento de Contexto**: o tutor e os geradores utilizam exclusivamente as fontes ativas pertencentes ao bloco de estudo autorizado.
- **Proteção contra Injeção de Instruções**: delimitadores `[INÍCIO DOS MATERIAIS — DADOS NÃO CONFIÁVEIS]` e sanitização impedem que comandos embutidos em materiais manipulem o comportamento do sistema.
- **Substituição Fácil de Provedores**: para adicionar novos provedores (Anthropic Claude, Google Gemini ou modelos locais como Ollama), basta criar um componente implementando a interface [`AiTutorProvider`](file:///backend/src/main/java/br/com/estudos/estudos_backend/chat/AiTutorProvider.java).

---

## 2. Tecnologias Utilizadas

- **Backend**: Java 21 LTS, Spring Boot 4, Spring Web, Spring Data JPA, Bean Validation, Apache PDFBox 3.0.3, Flyway Migration.
- **Frontend**: React 19, TypeScript, Vite, oxlint, CSS com tokens de design (sem React Router e sem Tailwind).
- **Banco de Dados**: PostgreSQL 17 (via Docker Compose, bancos `estudos` e `estudos_test`).
- **Testes Automatizados**: JUnit 5, Mockito, Spring MockMvc (108 testes automatizados, 100% offline, sem custos de API). `ContractEndpointsIntegrationTest` cobre as rotas usadas pelo frontend que foram criadas na Etapa A.

---

## 3. Pré-requisitos

1. **Java Development Kit (JDK) 21** ou superior instalado (`java -version`).
2. **Node.js LTS** (versão 20 ou 22) e `npm` (`node -v`).
3. **Docker Desktop** ativo para execução do container PostgreSQL.
4. **Chave de API da OpenAI** (opcional para testes e desenvolvimento; necessária apenas para respostas ao vivo).

---

## 4. Configuração Passo a Passo

### 4.1. Subir o Banco de Dados (PostgreSQL)

Na raiz do projeto, inicie o container PostgreSQL:

```powershell
docker compose up -d postgres
```

O container inicializará na porta `5432` com usuário `estudos` e senha `estudos-dev-only`.

### 4.1.1. Construir o avaliador de exercícios práticos (Docker)

Os exercícios em Java são corrigidos automaticamente: o código roda num contêiner descartável, sem rede e com limites de memória, CPU e tempo, contra os testes JUnit gerados com o exercício. O avaliador também verifica a aplicação do conceito (ex.: `@Override`) e regras de boas práticas. Construa a imagem uma vez:

```powershell
docker build -t estudos-java-runner:1 runner/java
```

Sem a imagem (ou com o Docker parado), os exercícios continuam funcionando com autoavaliação. Para desativar a correção automática, use `RUNNER_ENABLED=false`.

A IA gera os testes e a solução de referência junto com o exercício, e a solução é executada antes de liberar a correção automática. O Gemini (gratuito) já tem limite de resposta suficiente; com a OpenAI, aumente `OPENAI_MAX_OUTPUT_TOKENS` para ~4000.

Nota de cada tentativa prática: corretude pelos testes (50%), conceito (25%) e boas práticas (25%), multiplicada pela autonomia, que cai conforme as ajudas pedidas à tutora no chat do exercício. O desempenho de um tópico é 70% prática + 30% teoria.

### 4.2. Configurar Variáveis de Ambiente

Copie o arquivo de exemplo na raiz do projeto:

```powershell
copy .env.example .env
```

Abra o arquivo `.env` e configure suas preferências:

```env
# Ativação e Chave da API
OPENAI_ENABLED=true
OPENAI_API_KEY=sk-proj-sua-chave-aqui

# Modelo padrão recomendado (baixo custo, alta velocidade e JSON mode)
OPENAI_MODEL=gpt-4o-mini

# Limites e Timeouts
OPENAI_CONNECT_TIMEOUT_SECONDS=10
OPENAI_REQUEST_TIMEOUT_SECONDS=45
OPENAI_MAX_OUTPUT_TOKENS=1200
OPENAI_MAX_INPUT_CHARS=40000
OPENAI_MAX_REQUESTS_PER_MINUTE=20
```

> **Nota de Degradação Segura:** Se a `OPENAI_API_KEY` permanecer em branco, a plataforma iniciará normalmente em modo offline. As funcionalidades de IA retornarão HTTP 503 com mensagem informativa amigável na interface, e a geração de simulados utilizará a extração heurística baseada nos materiais locais.

### 4.3. Usar o Google Gemini (chave gratuita)

O Gemini tem plano gratuito, suficiente para estudar com o chat, gerar questões e extrair tópicos.

1. Acesse [Google AI Studio › API keys](https://aistudio.google.com/apikey) com sua conta Google.
2. Clique em **Create API key** (crie ou escolha um projeto do Google Cloud quando pedir) e copie a chave.
3. No arquivo `.env` **da raiz do projeto** (o mesmo copiado do `.env.example`), preencha:
   ```env
   GEMINI_API_KEY=cole-a-chave-aqui
   # Para usar o Gemini em todas as operações que não têm provedor escolhido na tela de Configurações:
   AI_PROVIDER=gemini
   ```
4. Reinicie o backend. Em **Configurações › Provedores de IA** o Gemini aparece como disponível; ali dá para
   escolher o provedor de cada operação, ligar o fallback e definir um limite diário de chamadas.

Observações:
- A chave fica só no `.env` (que não vai para o Git) e é enviada ao Google no cabeçalho `x-goog-api-key`,
  nunca na URL nem ao navegador.
- Modelo padrão: `gemini-3.8-flash` (`GEMINI_MODEL`); o `gemini-3.5-flash-lite` é mais leve. Os limites do plano
  gratuito (requisições por minuto e por dia) variam por modelo e aparecem em
  [AI Studio › Rate limits](https://aistudio.google.com/rate-limit). O backend limita a 10 chamadas por minuto
  (`GEMINI_MAX_REQUESTS_PER_MINUTE`).
- Quando a cota acaba, a tela mostra se foi o limite por minuto (aguarde um minuto) ou a cota diária, que é
  renovada à meia-noite do horário do Pacífico (por volta das 4h ou 5h em Brasília).
- A IA só usa as fontes do bloco que estão **ativas no chat**, e o conteúdo delas vai marcado como dado, nunca
  como instrução.

---

## 5. Execução do Projeto

### 5.1. Iniciar o Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

O backend executará as migrações Flyway automaticamente e subirá na porta `http://localhost:8080`.

### 5.2. Iniciar o Frontend

Em outro terminal:

```powershell
cd frontend
npm install
npm run dev
```

A interface abrirá em `http://localhost:5173`, com proxy reverso configurado para repassar chamadas `/api` ao backend.

---

## 6. Testes Automatizados

A suíte de testes foi projetada para ser **100% offline e segura**, simulando o comportamento da OpenAI por meio de mocks sem realizar chamadas pagas à internet.

**Integração contínua:** a cada push e pull request na `master`, o GitHub Actions (`.github/workflows/ci.yml`) roda os
testes do backend contra um PostgreSQL 17 (banco `estudos_test`) e, no frontend, `npm ci`, lint, build e
`npm run test:run`. Nenhuma chave de IA é usada no CI.

### Executar todos os testes do backend (108 testes):

> Os testes usam o banco `estudos_test` e **apagam as tabelas** dele. Crie-o uma única vez com:
> `docker exec programa-estudos-ia-postgres psql -U estudos -d postgres -c "CREATE DATABASE estudos_test OWNER estudos;"`

```powershell
cd backend
.\mvnw.cmd test
```

### Principais suítes de teste:
- `Etapa7CompletePlatformE2EIntegrationTest`: Teste E2E cobrindo ciclo de vida completo (Bloco -> Material -> Tópicos -> Questão HARD e Discursiva -> Snapshots Imutáveis -> Desempenho Global -> Repetição Espaçada -> Isolamento Multi-Inquilino).
- `Etapa6SpacedRepetitionIntegrationTest`: Validação do algoritmo adaptativo baseado em dificuldade e priorização na bateria de revisão.
- `Etapa5GlobalPerformanceIntegrationTest`: Cálculo de KPIs agregados, métricas por bloco/tópico e histórico temporal.
- `UniversalMultiBlockChatIntegrationTest`: Síntese cruzada entre múltiplos blocos autorizados.
- `OpenAiSecurityAndErrorsTest`: 10 testes cobrindo rate limit, mapeamento de códigos HTTP (200, 401, 429, 500, 502, 504), sanitização e proteção de credenciais.
- `MaterialFileStorageTest`: 10 testes cobrindo extração de PDF (PDFBox), DOCX (XML anti-XXE e zip-bomb), Markdown, TXT e higienização de tokens.

### Validar build e lint do frontend:

```powershell
cd frontend
cmd /c "npm run lint && npm run build"
```

---

## 7. Como Testar a Conexão e a API

### 7.1. Verificar o Status do Provedor de IA

```bash
curl http://localhost:8080/api/chat/status
```

Resposta quando não configurado:
```json
{
  "providerName": "OpenAI",
  "configured": false,
  "model": "gpt-4o-mini"
}
```

Resposta quando configurado:
```json
{
  "providerName": "OpenAI",
  "configured": true,
  "model": "gpt-4o-mini"
}
```

### 7.2. Fazer uma Pergunta ao Tutor via API

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Explique o conceito de injeção de dependências.",
    "studyBlockId": "ID-DO-BLOCO-AQUI"
  }'
```

---

## 8. Tratamento de Erros Comuns e Soluções

| Código HTTP | Causa Provável | Solução |
| :--- | :--- | :--- |
| **503 Service Unavailable** | `OPENAI_API_KEY` ausente, em branco ou chave revogada pela OpenAI. | Verifique o `.env`, certifique-se de que a variável `OPENAI_API_KEY` contém uma chave válida e reinicie o backend. |
| **429 Too Many Requests** | Limite de requisições por minuto atingido (local: 20 req/min na OpenAI, 10 no Gemini) ou cota do provedor. | Aguarde alguns segundos antes de enviar nova mensagem. Na OpenAI, verifique os créditos em platform.openai.com. No Gemini gratuito, a mensagem indica se é o limite por minuto ou a cota diária (renovada à meia-noite do Pacífico). |
| **502 Bad Gateway** | Resposta da OpenAI malformada ou instabilidade no serviço da OpenAI. | Tente novamente em instantes. O backend descarta respostas corrompidas para não persistir dados inválidos. |
| **504 Gateway Timeout** | A OpenAI demorou mais do que o tempo limite configurado (padrão 45s). | Reduza o tamanho do texto enviado ou divida o material em fontes menores. |
| **400 Bad Request** | Arquivo corrompido, encriptado ou mensagem excedeu o limite máximo (40.000 caracteres). | Envie um arquivo legível e não protegido por senha. |
| **413 Content Too Large** | Arquivo maior que o limite de upload (**10 MB** por padrão; 2 MB para texto e código-fonte). A resposta traz a mensagem em português. | Divida o arquivo ou ajuste `UPLOAD_MAX_FILE_SIZE` e `UPLOAD_MAX_REQUEST_SIZE` no `.env`. |
| **415 Unsupported Media Type** | Extensão não suportada. | Utilize apenas `.txt`, `.md`, `.pdf` ou `.docx`. |
| **422 Unprocessable Entity** | Bloco sem materiais cadastrados para gerar questões com IA ou vídeo YouTube sem legendas. | Adicione materiais com conteúdo de texto ao bloco antes de solicitar a geração. |

---

## 9. Extensão para Novos Provedores de IA

Para adicionar outro provedor no futuro sem alterar o domínio:

1. Implemente a interface [`AiTutorProvider`](file:///backend/src/main/java/br/com/estudos/estudos_backend/chat/AiTutorProvider.java):
   ```java
   public class ClaudeTutorProvider implements AiTutorProvider {
       @Override public String providerName() { return "Claude"; }
       @Override public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
       @Override public AiTutorReply generateReply(AiTutorRequest request) { ... }
   }
   ```
2. Registre o novo adaptador no Spring (`@Component` ou via configuração condicional).
3. Toda a aplicação continuará funcionando sem qualquer alteração em entidades, migrações ou controllers.
