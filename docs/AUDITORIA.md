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
| Testes | JUnit 5, Mockito, MockMvc — **90 testes, 0 falhas** (executados em 29/10/2026) |

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

| # | Tela / ação | Frontend chama | Backend expõe |
|---|---|---|---|
| 1 | Blocos › **Restaurar** bloco arquivado | `POST /study-blocks/{id}/unarchive` | não existe (não há desarquivamento) |
| 2 | Exercícios Teóricos › **Gerar simulado** | `POST …/questions/generate-simulado` | só `POST …/questions/generate` (1 questão); `generateSimulado()` existe no serviço, mas não está exposto |
| 3 | Área do bloco › **Ativar/desativar fonte no chat** | `PATCH …/materials/{id}/chat-toggle` | `PATCH …/materials/{id}/toggle-chat` |
| 4 | Adicionar Conteúdo › **Upload de arquivo** | `POST …/materials/upload` | `POST …/materials/import` (multipart) |
| 5 | Revisar Dificuldades › **Responder** questão | `POST …/questions/{id}/answer`, corpo `textAnswer` | `POST …/questions/{id}/answers`, corpo `discursiveAnswer` |
| 6 | Revisar Dificuldades › **Adiar** revisão | `PATCH /study-blocks/{b}/questions/{q}/reschedule` | `POST /api/reviews/questions/{q}/reschedule?days=N` |
| 7 | Exercícios Práticos › **Enviar resposta** | corpo `{ codeAnswer, selfRating }` | exige `{ answer, selfRating }` (`@NotBlank answer` → 400) |
| 8 | Feed de Vagas › **Buscar agora** | `POST /career/agent/search-now` | `POST /career/agent/run` |
| 9 | Feed de Vagas › **Mudar status** da vaga | `PATCH /career/jobs/{id}/status` | não existe; a entidade `CareerJob` não tem campo de status |
| 10 | Competências › **Avaliar** | `POST /career/skills/assess` | não existe |
| 11 | Fontes de Vagas › **Ativar/desativar** | `POST /career/sources/{id}/toggle` | `PATCH /career/sources/{id}/toggle` |

Consequência: a **revisão espaçada não registra respostas pela tela de revisão**, e **exercícios práticos não
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
- **Carreira — agendamento automático:** `CareerAgentSchedulerService` usa `@Scheduled`, mas **não existe `@EnableScheduling`**
  no projeto, então a execução agendada nunca acontece. Só a execução manual funciona.
- **Configurações:** o "ID de usuário" é salvo no `localStorage`, mas **nunca é enviado** ao backend (o cabeçalho
  `X-Study-User-Id` não é usado pelo `http.ts`). A "Meta diária" não é persistida.

### 4.4 Somente visual ⚪
- **Fórum:** tópicos fixos no código (`initialTopics`); criar e votar só alteram o estado em memória.
- **Sugestões do chat universal:** textos fixos no frontend (as do chat por bloco vêm do backend).

### 4.5 Ausentes ⚪
- **Metas** e **sessões de estudo**: o backend existe (`/goals`, `/sessions`), mas não há nenhuma tela.
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
| Padronização visual | Etapa 1 concluída: tokens, design system, Sidebar, modais, drawer do Tutor e mobile. **O layout interno das páginas ainda não foi padronizado.** |

## 7. Problemas encontrados (prioridade)
1. 🔴 **Críticos — contratos quebrados** (tabela 4.2): afetam revisão, exercícios, upload de arquivos, simulado e carreira.
2. 🔴 **Corrigido nesta sessão:** o Feed de Vagas derrubava a aplicação inteira (o backend retorna `Page` `{content: []}` e o frontend esperava uma lista).
3. 🟠 **Agendamento de vagas inativo** (falta `@EnableScheduling`).
4. 🟠 **Isolamento por usuário só no papel:** sem autenticação e sem envio do cabeçalho; qualquer cliente pode se passar por outro usuário.
5. 🟡 **Documentação divergente:** o `PROJECT_STATUS.md` afirmava "100% concluído", e o README citava React Router, V1–V20 e 85 testes.
6. 🟡 **Código morto:** `AddSourceModal` (componente), `career.css` (nunca importado).
7. 🟡 **Avisos de lint:** 14 avisos `set-state-in-effect` (sem erros).
8. 🟠 **Limite de upload inconsistente:** não há `spring.servlet.multipart.max-file-size` configurado, então vale o
   padrão do Spring (**1 MB por arquivo**). O `AddSourceModal` diz "15 MB" e o README antigo dizia "5 MB para PDF/DOCX";
   nenhum dos dois valores existe no código. PDFs comuns acima de 1 MB serão recusados.

## 8. Plano recomendado
Cada etapa deve ser pequena e validada (build, lint, testes do backend e verificação no navegador).

1. **Etapa A — Corrigir contratos quebrados** (sem mudar regras de negócio): ajustar o frontend às rotas existentes
   (itens 3, 4, 5, 6, 7, 8 e 11) e decidir com o usuário os que exigem backend novo (1 desarquivar, 2 expor o simulado,
   9 status de vaga, 10 avaliação de competências). Adicionar testes de contrato.
2. **Etapa B — Ativar o agendamento** (`@EnableScheduling`) com teste.
3. **Etapa C — Padronização visual por página** (Etapas 2+ do plano visual), usando `styles/components.css`.
4. **Etapa D — Telas de Metas e Sessões** sobre o backend existente.
5. **Etapa E — Identidade do usuário:** decidir entre autenticação real ou, no mínimo, enviar o cabeçalho de forma consistente. **Decisão arquitetural: requer autorização.**
6. **Etapa F — IA multi-provedor:** registro de provedores, seleção por operação e controle de consumo. **Requer autorização.**
7. **Etapa G — Carreira:** perfil profissional, estados de candidatura, análise por IA e ligação vagas → blocos.
8. **Etapa H — Materiais:** PPTX, páginas web e resumos.
9. **Etapa I — Análises de desempenho por IA** (semanal/mensal, por dificuldade, recomendações sobre dados reais).
10. **Etapa J — Fórum:** decidir entre implementar persistência ou remover/ocultar a tela. **Requer autorização.**
