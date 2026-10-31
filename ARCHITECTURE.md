# Arquitetura

Estado em 2026-10-31. Descreve o que está implementado; ideias não implementadas ficam em
`docs/DECISOES-PENDENTES.md`.

## Visão geral

```
[ Frontend React 19 + TS (Vite, :5173) ]  ── /api (proxy) ──►  [ Backend Spring Boot 4 (:8080) ]
                                                                   │            │
                                                     PostgreSQL 17 (Flyway V1–V32)   Provedores externos
                                                                                  (IA, YouTube, Remotive, Arbeitnow,
                                                                                   Docker do avaliador)
```

- **Backend:** Java 21, Spring Web, Spring Data JPA, Bean Validation, Flyway, Actuator (health com banco e
  provedores de IA), PDFBox/POI para extração de arquivos, `HttpClient` nativo para as APIs externas.
- **Frontend:** React 19 + TypeScript, sem React Router (`App.tsx` guarda página e bloco selecionado;
  `components/Sidebar.tsx` define o menu). HTTP via `api/http.ts` (`request<T>()`, `ApiError` com a mensagem do
  backend). Estilo por tokens (`styles/tokens.css`) e componentes (`styles/components.css`).
- **Usuário:** sem login. `security/CurrentUserResolver` lê `X-Study-User-Id` (o frontend não envia; cai em
  `default-user`) e todos os services filtram os dados por esse usuário.

## Módulos do backend (`br.com.estudos.estudos_backend`)

| Pacote | Responsabilidade |
|---|---|
| `studyblock` | Blocos de estudo (criar, editar, arquivar, restaurar). |
| `material` | Fontes: upload (`MaterialFileStorage`: PDF, DOCX, PPTX, Markdown, texto, código), página web (`WebPageExtractor`), YouTube (`YoutubeImportService`), resumo por IA, ativação no chat. |
| `chat` | Chat do bloco e universal; porta `AiTutorProvider`; `SourceContextSelector` escolhe trechos das fontes ativas. |
| `question` | Questões abertas com rubrica, correção (`DiscursiveAnswerService`), rigor (`EvaluationPreference`), repetição espaçada e simulados. |
| `exercise` | Exercícios práticos, tutoria por chat, autonomia e correção automática (`CodeRunner` + Docker). |
| `learning` | Histórico central de tentativas (`LearningHistoryService`), Desempenho Global por bloco e análises (`LearningInsightsService`). |
| `goal`, `studysession` | Metas com progresso; sessões planejadas, iniciadas, concluídas ou canceladas. |
| `career` | Vagas, perfil, compatibilidade e agente agendado — portas e adaptadores (`adapter/in/web`, `adapter/out/persistence`, `adapter/out/jobprovider`, `domain`, `service`). |
| `ai` | `AiProviderRouter` (roteamento por operação, fallback, limite diário, consumo, cache), provedores e health. |
| `security`, `config`, `common` | Usuário atual, agendamento/executores, tratamento de erros padronizado. |

Os demais pacotes seguem o formato plano (entidade + repository + service + controller).

## IA: provedores e roteamento

```
Services (chat, questões, exercícios, resumo, correção, análises, carreira)
        │  AiOperationContext.call(AiOperation.X, …)
        ▼
  AiTutorProvider  ◄── bean principal: AiProviderRouter
        │  rota da operação (tela Configurações › Provedores de IA, gravada em banco)
        ├── OpenAI    (OpenAiTutorProvider)
        ├── Anthropic (AnthropicModelProvider)
        └── Gemini    (GeminiModelProvider; GeminiYoutubeVideoAnalyzer para vídeos)
```

- Cada `AiOperation` (chat, geração de questões e exercícios, tutoria, resumo, correção de respostas, análise de
  desempenho, análise de vagas) tem provedor, modelo e fallback próprios; há limite diário e registro de consumo
  (`ai_usage_records`).
- Operações determinísticas usam cache de respostas (`ai_response_cache`); geração e chat não.
- Chaves só em variáveis de ambiente; sem chave, os endpoints de IA respondem 503.
- Chamadas de IA ficam fora de `@Transactional`; fontes, vagas e respostas do aluno vão delimitadas como dado não
  confiável (proteção contra prompt injection).
- A IA só gera conteúdo, avalia e interpreta: notas, percentuais e médias são calculados no backend. Vários
  recursos têm fallback sem IA (questões extraídas das fontes, análise de desempenho e de vagas por regras).

## Fluxos principais

### Vídeo do YouTube
1. `POST …/materials/youtube` grava a fonte como "em processamento" (prévia por oEmbed).
2. Em segundo plano (`youtubeProcessingExecutor`): **análise por IA** do Gemini, uma única vez, com resumo,
   assuntos e anotações com minuto; se indisponível, **transcrição** das legendas (`YoutubeTranscriptExtractor`).
3. Sem nenhum dos dois, a fonte fica com erro e mensagem; pode ser reprocessada.

### Questão teórica aberta
1. Geração: a IA recebe as fontes ativas do bloco (delimitadas) e os enunciados já existentes; devolve enunciado,
   resposta-modelo, rubrica e trecho do material. Questão cujo trecho não está no material é descartada.
2. Resposta: `DiscursiveAnswerService` pede à IA a avaliação contra a rubrica; `DiscursiveEvaluationRules` aplica o
   rigor (Rigoroso: nota ≥ 85, essenciais e terminologia; Moderado: nota ≥ 75 e essenciais) e classifica em
   CORRETA, PARCIAL ou INCORRETA. Sem IA, a tentativa fica pendente.
3. A tentativa entra no histórico com snapshots (enunciado, resposta, gabarito, fonte) e alimenta a repetição
   espaçada e o desempenho por bloco.

### Vagas (Remotive e Arbeitnow)
1. `CareerAgentSchedulerService` (`@Scheduled`, liga com `app.scheduling.enabled`) busca vagas pelos adaptadores
   `RemotiveJobProviderAdapter` e `ArbeitnowJobProviderAdapter`, conforme as preferências e fontes ativas.
2. `CareerJobCompatibilityService` calcula a compatibilidade de forma determinística: termos técnicos da vaga contra
   o perfil de habilidades (`CareerSkillsProfileService`), em que cada bloco de estudo é uma competência
   (demonstrada, em desenvolvimento ou estudada, pelas tentativas no bloco).
3. Análise da vaga por IA (descrição delimitada como dado; fallback por regras) e "vaga → bloco de estudo".

## Persistência

- PostgreSQL 17; schema só por migrações Flyway novas (`V1`…`V32`).
- Tentativas guardam snapshots imutáveis para o histórico sobreviver à edição ou arquivamento de questões e fontes.
- As tabelas `knowledge_*`, `topic_review_decisions` e as colunas `topic_id` são do antigo Mapeamento de Fontes,
  removido em 2026-10-31; ficam sem uso até a decisão D4.

## Testes

- Backend: `@SpringBootTest` + MockMvc + perfil `test` contra o PostgreSQL real (`estudos_test`); serviços de IA
  com `AiTutorProvider` simulado; nenhuma chamada a APIs externas.
- Frontend: Vitest + Testing Library (jsdom); `api/contracts.test.ts` confere rotas, métodos e corpos.
- CI no GitHub Actions (`.github/workflows/ci.yml`).
