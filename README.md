# Plataforma de Estudos com Inteligência Artificial

[![CI](https://github.com/GabrielVarela2004/meu-ambiente-de-estudos/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/GabrielVarela2004/meu-ambiente-de-estudos/actions/workflows/ci.yml)

Plataforma pessoal de estudos fundamentada nos seus próprios materiais (estilo *NotebookLM*): blocos de estudo,
fontes (arquivos, páginas web e vídeos do YouTube), chat com IA que só usa as fontes ativas, questões teóricas
abertas corrigidas com rubrica, exercícios práticos com correção automática, repetição espaçada, metas e sessões,
desempenho por bloco e um radar de vagas.

Backend em **Java 21 / Spring Boot 4** com **PostgreSQL 17** e **Flyway** (migrações `V1` a `V32`); frontend em
**React 19 / TypeScript / Vite**. Arquitetura em [`ARCHITECTURE.md`](ARCHITECTURE.md); estado atual em
[`PROJECT_STATUS.md`](PROJECT_STATUS.md); problemas conhecidos em [`docs/AUDITORIA.md`](docs/AUDITORIA.md).

---

## 1. Funcionalidades

- **Blocos de estudo** com fontes, chat, questões, exercícios, metas e sessões.
- **Fontes:** PDF, DOCX, PPTX, Markdown, texto e código-fonte (Java, Python, TS, SQL e outros); páginas web;
  vídeos do YouTube. Só as fontes **ativas no chat** entram no contexto da IA.
- **Chat** do bloco e universal (vários blocos), com histórico e citações das fontes.
- **Questões teóricas abertas** com resposta-modelo e rubrica, corrigidas com rigor pela IA (seção 6).
- **Exercícios práticos** com tutoria por chat e correção automática de código Java em contêiner isolado.
- **Repetição espaçada** e tela de revisão.
- **Desempenho Global:** KPIs, linha do tempo, detalhamento **por bloco** (tentativas, acertos, parciais, nota média,
  precisão), análises semanais/mensais e recomendações. Todos os números são calculados no backend.
- **Metas e Sessões** por bloco (progresso, cronômetro da sessão ativa).
- **Carreira:** vagas reais de Remotive e Arbeitnow, compatibilidade calculada contra os seus blocos (cada bloco é
  uma competência), análise da vaga por IA, perfil profissional e agente agendado.
- **IA com vários provedores:** OpenAI, Anthropic (Claude) e Google Gemini, escolhidos por operação.

Uso individual, sem login: todo dado pertence ao usuário `default-user`.

### Estrutura do repositório

```
backend/    Spring Boot — pacotes por domínio: studyblock, material, chat, question, exercise, learning,
            goal, studysession, career (portas e adaptadores), ai, security, config, common
frontend/   React + Vite — App.tsx (troca de páginas, sem React Router), components/, pages/, features/,
            api/ (funções tipadas + testes de contrato), styles/ (tokens.css, components.css, pages/)
runner/     imagem Docker que corrige os exercícios práticos em Java
docs/       visão do produto, plano de execução, decisões pendentes e auditoria
```

---

## 2. Pré-requisitos

1. **JDK 21** ou superior.
2. **Node.js LTS** (20 ou 22) e `npm`.
3. **Docker Desktop** (PostgreSQL e, opcionalmente, o avaliador de exercícios).
4. Chave de pelo menos um provedor de IA (opcional: sem chave, os recursos de IA respondem 503 e o resto funciona).

---

## 3. Como rodar

### 3.1. Tudo de uma vez (Windows)

```powershell
.\start-dev.ps1      # ou start-dev.bat
```

Abre o Docker Desktop se preciso, sobe o PostgreSQL, inicia backend e frontend em janelas próprias com reinício
automático (`dev-restart-loop.ps1`; o backend roda com `-Xmx768m`) e espera o `/actuator/health` antes de abrir o
navegador em `http://localhost:5173`.

### 3.2. Passo a passo

```powershell
docker compose up -d postgres          # porta 5432, usuário estudos
copy .env.example .env                 # depois preencha as chaves (seção 4)

cd backend
.\mvnw.cmd spring-boot:run             # porta 8080; aplica as migrações Flyway

cd ..\frontend
npm install
npm run dev                            # porta 5173; proxy /api → 8080
```

### 3.3. Avaliador de exercícios práticos (opcional)

```powershell
docker build -t estudos-java-runner:1 runner/java
```

O código roda num contêiner descartável, sem rede e com limites de memória, CPU e tempo, contra os testes JUnit
gerados com o exercício (a solução de referência é executada antes de liberar a correção). Nota: corretude 50%,
conceito 25% e boas práticas 25%, multiplicada pela autonomia (cai com as ajudas pedidas à tutora). Sem a imagem,
com o Docker parado ou com `RUNNER_ENABLED=false`, o exercício usa autoavaliação.

---

## 4. Configuração da IA (`.env` na raiz)

As chaves ficam só no `.env` (fora do Git) e nunca vão ao navegador. Em **Configurações › Provedores de IA** você
escolhe o provedor e o modelo de cada operação, liga o fallback, define um limite diário e vê o consumo dos últimos
7 dias. `AI_PROVIDER` define o provedor padrão das operações sem escolha na tela.

| Provedor | Variáveis principais |
|---|---|
| OpenAI | `OPENAI_ENABLED`, `OPENAI_API_KEY`, `OPENAI_MODEL`, `OPENAI_MAX_OUTPUT_TOKENS`, `OPENAI_MAX_REQUESTS_PER_MINUTE` |
| Anthropic | `ANTHROPIC_ENABLED`, `ANTHROPIC_API_KEY`, `ANTHROPIC_MODEL`, `ANTHROPIC_EFFORT` |
| Gemini | `GEMINI_ENABLED`, `GEMINI_API_KEY`, `GEMINI_MODEL`, `GEMINI_REQUEST_TIMEOUT_SECONDS`, `GEMINI_MAX_REQUESTS_PER_MINUTE` |

### Google Gemini (chave gratuita)

1. Gere a chave em [Google AI Studio › API keys](https://aistudio.google.com/apikey).
2. No `.env`:
   ```env
   GEMINI_API_KEY=cole-a-chave-aqui
   AI_PROVIDER=gemini
   ```
3. Reinicie o backend; o Gemini aparece como disponível em Configurações › Provedores de IA.

Modelo padrão `gemini-3.8-flash`. O backend limita o Gemini a 10 chamadas por minuto; a tela diz se o erro foi o
limite por minuto ou a cota diária (renovada à meia-noite do Pacífico). A chave vai no cabeçalho
`x-goog-api-key`, nunca na URL.

Respostas repetíveis (resumos, correções, análises) vêm de um cache; geração de questões e chat não usam cache.
Chamadas de IA ficam fora de transações e o conteúdo das fontes vai delimitado como dado não confiável.

---

## 5. Vídeos do YouTube

Em **Adicionar Conteúdo › YouTube**, cole um ou vários links (há prévia com miniatura, título e canal). O vídeo é
processado em segundo plano:

1. **Análise por IA (Gemini):** o vídeo é analisado **uma única vez** (resumo, assuntos principais e anotações com
   o minuto de cada trecho, inclusive código mostrado na tela).
2. **Plano B, transcrição:** sem Gemini configurado, sem cota ou com o vídeo indisponível para análise, usa as
   legendas do vídeo.
3. Sem análise nem legendas, a fonte fica com erro e uma mensagem explicando o motivo (é possível reprocessar).

Limites: só vídeos **públicos** podem ser analisados pelo Gemini; o plano gratuito permite cerca de 8 horas de vídeo
por dia. Playlists inteiras não são importadas (decisão D2 em `docs/DECISOES-PENDENTES.md`).

---

## 6. Correção das questões teóricas

As questões teóricas são sempre **abertas**. Na geração, a IA grava, antes de a questão ser exibida, a
resposta-modelo e a **rubrica** (conceitos essenciais e complementares, termos do material e erros comuns), e cita
um trecho que precisa existir no material; questão sem fundamento no material é descartada.

Na resposta, a IA compara o texto com a rubrica e o backend classifica:

| Rigor (Configurações) | CORRETA quando |
|---|---|
| **Rigoroso** (padrão) | nota ≥ 85, todos os conceitos essenciais e a terminologia do material |
| **Moderado** | nota ≥ 75 e todos os conceitos essenciais |

O resultado pode ser CORRETA, PARCIAL ou INCORRETA, com o que faltou e os equívocos. Sem IA disponível, a tentativa
fica **pendente** e pode ser corrigida depois (nunca é corrigida por palavras-chave). Notas, percentuais e médias são
sempre calculados no backend.

---

## 7. Testes

> Os testes do backend usam o banco `estudos_test` e **apagam as tabelas** dele. Crie-o uma vez:
> `docker exec programa-estudos-ia-postgres psql -U estudos -d postgres -c "CREATE DATABASE estudos_test OWNER estudos;"`

```powershell
cd backend;  .\mvnw.cmd test        # 237 testes (1 ignorado), contra o PostgreSQL real
cd frontend; npm run lint; npm run build; npm run test:run   # 46 testes (Vitest + Testing Library)
```

Nenhum teste chama APIs externas reais: provedores de IA, YouTube, Remotive e Arbeitnow são simulados. No frontend,
`api/contracts.test.ts` confere a rota, o método e o corpo de cada função tipada contra os controllers.

**CI:** a cada push e pull request na `master`, o GitHub Actions (`.github/workflows/ci.yml`) roda os testes do
backend contra um PostgreSQL 17 e, no frontend, `npm ci`, lint, build e `npm run test:run`. Nenhuma chave de IA é
usada no CI.

Com o Cloudflare WARP ativo, o Maven falha em TLS ao baixar dependências: use
`$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'` uma vez e depois `.\mvnw.cmd -o test`.

---

## 8. Verificar a API

```bash
curl http://localhost:8080/actuator/health     # banco e provedores de IA (sem chaves)
curl http://localhost:8080/api/chat/status     # {"providerName": "...", "configured": true|false, "model": "..."}
```

---

## 9. Erros comuns

| Código | Causa provável | Solução |
| :--- | :--- | :--- |
| **503** | Nenhum provedor de IA configurado para a operação. | Preencha a chave no `.env` e reinicie o backend. |
| **429** | Limite por minuto, limite diário definido na tela ou cota do provedor. | Aguarde; a mensagem indica qual limite foi atingido. |
| **502** | Resposta do provedor malformada ou instável. | Tente de novo; respostas inválidas não são gravadas. |
| **504** | O provedor passou do tempo limite. | Tente de novo ou use fontes menores. |
| **413** | Arquivo acima do limite (10 MB por padrão; 2 MB para texto e código). | Divida o arquivo ou ajuste `UPLOAD_MAX_FILE_SIZE` e `UPLOAD_MAX_REQUEST_SIZE`. |
| **415** | Extensão não suportada. | Use PDF, DOCX, PPTX, Markdown, texto ou código-fonte. |
| **422** | Bloco sem fonte concluída e ativa no chat, ou vídeo sem análise nem legendas. | Adicione ou ative uma fonte no bloco. |

---

## 10. Limitações conhecidas

- Sem autenticação: o cabeçalho `X-Study-User-Id` existe no backend, mas o frontend não o envia (decisão pendente).
- O Fórum é só visual e está fora do menu (decisão pendente).
- O agendamento de vagas fica ligado por padrão (`SCHEDULING_ENABLED`).
- As tabelas do antigo Mapeamento de Fontes continuam no banco sem uso (decisão D4).
