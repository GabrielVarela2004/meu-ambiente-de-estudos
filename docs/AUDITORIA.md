# Auditoria Técnica — Estudos IA PRO PLATFORM

**Data:** 29/10/2026
**Escopo:** leitura completa do repositório (backend, frontend, migrações, testes), verificação das rotas
contra o backend em execução, execução de testes, build e lint, e verificação da interface em navegador headless.
**Nenhuma funcionalidade foi implementada durante a auditoria.** A única correção de código feita na mesma
sessão está registrada na seção 7 (Feed de Vagas).

Legenda: ✅ funciona de ponta a ponta · 🟡 parcial · 🔴 quebrado na interface (backend existe) · ⚪ não existe · 🔍 precisa de investigação

---

## 1. Resumo do produto

Plataforma pessoal de estudos com IA, no estilo NotebookLM. O usuário organiza os estudos em **blocos**, importa
**fontes** (texto, PDF, DOCX, TXT, MD e YouTube), conversa com um **tutor de IA** fundamentado nessas fontes, gera
**questões e exercícios**, responde e acompanha **desempenho**, **revisão espaçada** e **mapa de tópicos**. Há
ainda um **módulo de carreira** que busca vagas reais em APIs públicas e calcula a compatibilidade com o que o
usuário estuda.

## 2. Arquitetura atual

```
frontend/ (React 19 + TS + Vite, porta 5173)
  App.tsx ─ estado global (página atual, bloco selecionado, blocos, contadores)
  ├─ components/Sidebar.tsx ─ navegação por PageId (SEM biblioteca de rotas)
  ├─ pages/ e features/ ─ telas
  ├─ api/http.ts ─ cliente fetch único (proxy do Vite: /api → :8080)
  └─ styles/ ─ tokens e design system (Etapa 1 visual)
        │  HTTP/JSON
        ▼
backend/ (Java 21 + Spring Boot 4.1, porta 8080) — pacotes por domínio
  studyblock · material · chat · question · exercise · knowledge · learning
  goal · studysession · security · career (portas e adaptadores)
        │  JPA + Flyway (V1 a V21)
        ▼
PostgreSQL 17 (Docker) — bancos `estudos` (app) e `estudos_test` (testes)
        │
APIs externas: OpenAI (Chat Completions) · YouTube (transcrição) · Remotive · Arbeitnow
```

- **Identificação do usuário:** `CurrentUserResolver` lê o cabeçalho `X-Study-User-Id`; sem cabeçalho usa
  `default-user`. **O frontend nunca envia esse cabeçalho**, e não há autenticação.
- **Camada de IA:** interface `AiTutorProvider`; existe apenas uma implementação (`OpenAiTutorProvider`).

## 3. Tecnologias

| Camada | Tecnologias |
|---|---|
| Backend | Java 21, Spring Boot 4.1.2 (WebMVC, Data JPA, Validation, Actuator), Flyway, PDFBox 3.0.3, `HttpClient` nativo |
| Frontend | React 19, TypeScript 7, Vite 8, oxlint, CSS próprio com tokens (sem Tailwind e sem React Router) |
| Banco | PostgreSQL 17 via Docker Compose |
| Testes | JUnit 5, Mockito, MockMvc — **108 testes, 0 falhas** (executados em 29/10/2026) |

## 4. Estado das funcionalidades

### 4.1 Funcionando de ponta a ponta ✅
- Criar, editar, listar e arquivar blocos de estudo.
- Adicionar fonte por **texto** e por **YouTube** (página "Adicionar Conteúdo").
- Chat do bloco e chat universal multi-bloco, com histórico persistido, citações de fontes e estado offline sem chave.
- Gerar exercícios práticos.
- Questões teóricas: listar e **responder** (múltipla escolha, V/F e discursiva).
- Painel de Desempenho Global com dados reais (`/api/performance/global`).
- Listagem da revisão espaçada (`/api/reviews/summary` e `/battery`).
- Mapeamento de tópicos: extrair, criar, validar, arquivar e definir pré-requisito.
- Carreira: preferências (GET/PUT), status e execução manual do agente pela página "Agendamento", fontes (listar e testar), competências (GET).

### 4.2 Quebrados na interface — o backend existe, mas o frontend chama rota ou formato errado 🔴
Verificado lendo os controllers e testando contra o backend em execução.

| # | Tela / ação | Frontend chamava | Backend expõe | Situação |
|---|---|---|---|---|
| 1 | Blocos › **Restaurar** bloco arquivado | `POST /study-blocks/{id}/unarchive` | não existia → criado `POST /study-blocks/{id}/unarchive` | ✅ Corrigido (2026-10-29) |
| 2 | Exercícios Teóricos › **Gerar simulado** | `POST …/questions/generate-simulado` | só `POST …/questions/generate` (1 questão); `generateSimulado()` agora exposto em `POST …/questions/generate-simulado` (1–10 questões) | ✅ Corrigido (2026-10-29) |
| 3 | Área do bloco › **Ativar/desativar fonte no chat** | `PATCH …/materials/{id}/chat-toggle` | `PATCH …/materials/{id}/toggle-chat` | ✅ Corrigido (2026-10-29) |
| 4 | Adicionar Conteúdo › **Upload de arquivo** | `POST …/materials/upload` | `POST …/materials/import` (multipart) | ✅ Corrigido (2026-10-29) |
| 5 | Revisar Dificuldades › **Responder** questão | `POST …/questions/{id}/answer`, corpo `textAnswer` | `POST …/questions/{id}/answers`, corpo `discursiveAnswer` | ✅ Corrigido (2026-10-29) |
| 6 | Revisar Dificuldades › **Adiar** revisão | `PATCH /study-blocks/{b}/questions/{q}/reschedule` | `POST /api/reviews/questions/{q}/reschedule?days=N` | ✅ Corrigido (2026-10-29) |
| 7 | Exercícios Práticos › **Enviar resposta** | corpo `{ codeAnswer, selfRating }` | exige `{ answer, selfRating }` (`@NotBlank answer` → 400) | ✅ Corrigido (2026-10-29) |
| 8 | Feed de Vagas › **Buscar agora** | `POST /career/agent/search-now` | `POST /career/agent/run` | ✅ Corrigido (2026-10-29) |
| 9 | Feed de Vagas › **Mudar status** da vaga | `PATCH /career/jobs/{id}/status` | criado `PATCH /career/jobs/{id}/status` + coluna `application_status` (V22) | ✅ Corrigido (2026-10-29) |
| 10 | Competências › **Avaliar** | `POST /career/skills/assess` | criado: gera exercício prático para o tópico da competência | ✅ Corrigido (2026-10-29) |
| 11 | Fontes de Vagas › **Ativar/desativar** | `POST /career/sources/{id}/toggle` | `PATCH /career/sources/{id}/toggle` | ✅ Corrigido (2026-10-29) |
| 12 | Adicionar Conteúdo › **YouTube** | corpo `{ youtubeUrl, … }` | exige `{ url }` (`@NotBlank` → 400) | ✅ Corrigido (2026-10-29) |

**Atualização 2026-10-29 (Etapa A):** os itens 3–8, 11 e 12 foram corrigidos só no frontend e validados contra o backend em execução. Título personalizado e "incluir no chat" agora são aplicados após a criação (PUT e toggle-chat), pois as rotas de criação não recebem esses campos. O seletor de idioma do YouTube (sem efeito) foi trocado por uma explicação: o backend escolhe a legenda (pt → en → es). Itens 1, 2, 9 e 10 ganharam rotas novas no backend, cobertas por `ContractEndpointsIntegrationTest`. Também foram alinhados `matchScore`/`postedAt` → `compatibilityPercent`/`publishedAt` e `?limit` → `?size` no Feed de Vagas.

Consequência (antes da correção): a **revisão espaçada não registra respostas pela tela de revisão**, e **exercícios práticos não
registram tentativas**. Por isso o histórico e o desempenho só são alimentados pela tela de Questões Teóricas.

### 4.3 Parcialmente implementados 🟡
- **Materiais:** suporta TXT, MD, PDF, DOCX e YouTube. Não suporta PPT/PPTX, links de páginas web nem código-fonte.
  Não há resumo automático de material. O `AddSourceModal.tsx` usa a rota correta, mas **não é usado** em nenhuma tela (código morto).
- **Mapa de conhecimento:** tópicos, subtópicos, pré-requisitos e estado de aprendizagem calculado (`TopicLearningStateEvaluator`).
  Não há visualização em grafo. Na página "Mapeamento de Fontes" o `App.tsx` passa `questions={[]}`, então **não é possível vincular questões a tópicos por essa tela**.
- **Camada de IA multi-provedor:** a interface existe, mas há apenas a OpenAI. Não há seleção de provedor ou modelo por operação,
  controle de consumo (só limite de requisições por minuto) nem configuração pela interface.
- **Geração de questões:** formatos múltipla escolha, V/F e discursiva, com dificuldade. Sem chave de IA, gera questões a partir
  de trechos reais das fontes. Não há formatos "exercício de programação" ou "situação real" para questões teóricas
  (os exercícios práticos cobrem parte disso).
- **Desempenho:** KPIs, linha do tempo e quebra por bloco e tópico. Não há agregação semanal ou mensal explícita, desempenho
  por dificuldade no painel, tempo de realização nem análise de desempenho por IA.
- **Carreira — compatibilidade:** pontuação **determinística por palavras-chave** (`CareerJobCompatibilityService`) contra um
  perfil derivado dos blocos e tópicos. Não há perfil profissional (formação, experiências, projetos) nem análise por IA.
- **Carreira — agendamento automático:** ✅ ativado em 2026-10-29 (`config/SchedulingConfiguration` com `@EnableScheduling`, desligável por `app.scheduling.enabled`; testes `SchedulingConfigurationTest` e `SchedulingConfigurationConditionTest`). Antes: `CareerAgentSchedulerService` usava `@Scheduled`, mas **não existia `@EnableScheduling`**
  no projeto, então a execução agendada nunca acontece. Só a execução manual funciona.
- **Configurações:** o "ID de usuário" é salvo no `localStorage`, mas **nunca é enviado** ao backend (o cabeçalho
  `X-Study-User-Id` não é usado pelo `http.ts`). A "Meta diária" não é persistida.

### 4.4 Somente visual ⚪
- **Fórum:** tópicos fixos no código (`initialTopics`); criar e votar só alteram o estado em memória.
- **Sugestões do chat universal:** textos fixos no frontend (as do chat por bloco vêm do backend).

### 4.5 Ausentes ⚪
- ~~**Metas** e **sessões de estudo** sem tela~~ ✅ tela "Metas e Sessões" criada em 2026-10-29 (`pages/GoalsSessionsPage.tsx`).
- Perfil profissional; estados de candidatura de vagas; recomendações profissionais por IA; ligação "vagas → criar blocos de estudo".
- Autenticação e controle de acesso reais.
- Outros provedores de IA (Anthropic, Gemini).
- Resumo de materiais; consulta individual de material pelo chat (o filtro de fontes existe e é parcial).

## 5. Relação frontend ↔ backend ↔ banco
- O frontend só se comunica via `/api` (proxy do Vite). Todos os cálculos de desempenho e revisão ficam no backend. ✅
- O banco é versionado por Flyway V1 a V21. O banco de testes é separado. ✅
- **Os contratos não têm tipagem compartilhada:** os tipos do frontend são redeclarados manualmente em cada tela, o que explica os 11 desencontros.
  Os testes do backend passam porque não cobrem o que o frontend chama.
- Dados atuais no banco `estudos`: 1 bloco, 0 materiais, 0 questões, 0 tentativas, 42 vagas.

## 6. Situação dos módulos-chave
| Módulo | Situação |
|---|---|
| IA | OpenAI funcional, com timeouts, rate limit, proteção da chave e delimitação contra prompt injection. Sem chave, responde 503 de forma controlada. Multi-provedor ainda não existe. |
| Desempenho e histórico | Backend sólido e com snapshots imutáveis das tentativas. Alimentado na prática só pelas Questões Teóricas (veja 4.2, itens 5 e 7). |
| Revisão inteligente | Algoritmo por dificuldade e sequência de acertos (EASY 4/9/20 dias; INTERMEDIATE 3/6/12; HARD 1/2/4; erro reinicia) testado no backend. **A tela de revisão não consegue responder nem adiar.** |
| Vagas | Busca real (Remotive e Arbeitnow) e compatibilidade determinística. Feed corrigido nesta sessão. Status, avaliação, "buscar agora" e alternância de fontes estão quebrados. Agendamento inativo. |
| Padronização visual | Etapas 1 e 2 concluídas: tokens, design system e layout interno de todas as páginas (`styles/pages/*.css`), verificados no navegador em desktop e mobile. |

## 7. Problemas encontrados (prioridade)
1. 🔴 **Críticos — contratos quebrados** (tabela 4.2): afetam revisão, exercícios, upload de arquivos, simulado e carreira.
2. 🔴 **Corrigido nesta sessão:** o Feed de Vagas derrubava a aplicação inteira (o backend retorna `Page` `{content: []}` e o frontend esperava uma lista).
3. ✅ ~~**Agendamento de vagas inativo**~~ — corrigido em 2026-10-29.
4. 🟠 **Isolamento por usuário só no papel:** sem autenticação e sem envio do cabeçalho; qualquer cliente pode se passar por outro usuário.
5. 🟡 **Documentação divergente:** o `PROJECT_STATUS.md` afirmava "100% concluído", e o README citava React Router, V1–V20 e 85 testes.
6. 🟡 **Código morto:** `AddSourceModal` (componente) — tipos movidos para `types/material.ts` em 2026-10-30; apagar ou integrar é a decisão D3. O `career.css` antigo foi removido em 2026-10-29.
7. ✅ ~~**Avisos de lint:** avisos `set-state-in-effect`~~ — corrigidos em 2026-10-30 (`hooks/useDeferredEffect`); lint sem avisos.
8. ✅ **Limite de upload** agora configurado em 10 MB (`UPLOAD_MAX_FILE_SIZE`; ajustado de 20 para 10 MB em 2026-10-30, Etapa 4 do plano novo), igual ao limite do `MaterialFileStorage`, com 413 e mensagem em português (`common/exception/GlobalExceptionHandler`). Situação anterior: não há `spring.servlet.multipart.max-file-size` configurado, então vale o
   padrão do Spring (**1 MB por arquivo**). O `AddSourceModal` diz "15 MB" e o README antigo dizia "5 MB para PDF/DOCX";
   nenhum dos dois valores existe no código. PDFs comuns acima de 1 MB serão recusados.

## 8. Plano recomendado
Cada etapa deve ser pequena e validada (build, lint, testes do backend e verificação no navegador).

1. ✅ **Etapa A — Corrigir contratos quebrados** (concluída em 2026-10-29) (sem mudar regras de negócio): ajustar o frontend às rotas existentes
   (itens 3, 4, 5, 6, 7, 8 e 11) e decidir com o usuário os que exigem backend novo (1 desarquivar, 2 expor o simulado,
   9 status de vaga, 10 avaliação de competências). Adicionar testes de contrato.
2. ✅ **Etapa B — Ativar o agendamento** (concluída) (`@EnableScheduling`) com teste.
3. ✅ **Etapa C — Padronização visual por página** (concluída) (Etapas 2+ do plano visual), usando `styles/components.css`.
4. ✅ **Etapa D — Telas de Metas e Sessões** (concluída) sobre o backend existente.
5. ✅ **Etapa E — decidido em 2026-10-29: manter uso individual, sem login.** A tela de Configurações deixou de oferecer a troca de usuário (que nunca era enviada ao backend). Texto original — **Identidade do usuário:** decidir entre autenticação real ou, no mínimo, enviar o cabeçalho de forma consistente. **Decisão arquitetural: requer autorização.**
6. ✅ **Etapa F — concluída em 2026-10-29:** OpenAI, Anthropic (SDK oficial `anthropic-java`) e Google Gemini atrás de `AiProviderRouter`; provedor por operação com fallback, modelo e limite diário por provedor, registro de consumo (`ai_usage_records`, V23) e tela em Configurações › Provedores de IA. Texto original — **IA multi-provedor:** registro de provedores, seleção por operação e controle de consumo. **Requer autorização.**
7. ✅ **Etapa G — concluída em 2026-10-30:** perfil profissional (`/api/career/profile`, tela Carreira › Perfil Profissional; V25), estados de candidatura com anotações (`OFFER`, `REJECTED`, `applicationNotes`, `statusUpdatedAt`), análise da vaga por IA com a descrição delimitada como dado e fallback por regras (`POST /api/career/jobs/{id}/analysis`) e vaga → bloco de estudo com os requisitos como tópicos, sem duplicar (`POST /api/career/jobs/{id}/study-block`). Texto original — **Carreira:** perfil profissional, estados de candidatura, análise por IA e ligação vagas → blocos.
8. ✅ **Etapa H — concluída em 2026-10-30:** PPTX (slides + notas, com limites contra zip-bomb), arquivos de código-fonte, páginas web (`POST /materials/web`, extração HTTP com `WebPageExtractor`) e resumo por material (`POST /materials/{id}/summary`, IA com fallback para resumo extrativo; V24). Texto original — **Materiais:** PPTX, páginas web e resumos.
9. ✅ **Etapa I — concluída em 2026-10-30:** `GET /api/performance/insights?period=WEEK|MONTH` (agregação semanal/mensal, por dificuldade, tópicos fracos/fortes, consistência, tendência e recomendações, tudo calculado no backend) e `POST /api/performance/insights/analysis` (a IA só interpreta os números; sem IA, análise por regras). Painel "Análises de Desempenho" no topo de Desempenho Global. Texto original — **Análises de desempenho por IA** (semanal/mensal, por dificuldade, recomendações sobre dados reais).
10. ✅ **Etapa J — decidido em 2026-10-29: Fórum oculto do menu** até existir persistência real (`ForumPage.tsx` mantido no código). Texto original — **Fórum:** decidir entre implementar persistência ou remover/ocultar a tela. **Requer autorização.**
