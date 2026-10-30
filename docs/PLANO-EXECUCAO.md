# Plano de execução — docs/VISAO-PRODUTO.md

Legenda: `[ ]` pendente · `[x]` concluída · `[!]` aguardando decisão (ver `docs/DECISOES-PENDENTES.md`).

Linha de base (2026-10-30, antes da Etapa 0): backend com 131 testes passando; frontend sem testes automatizados.

Várias etapas já tinham sido cobertas pelo plano do `docs/AUDITORIA.md` (Etapas A–J, concluídas em 2026-10-29/30).
Nesses casos a etapa aqui é só conferida e completada no que faltar.

- [x] Etapa 0 — Contexto (`CLAUDE.md` com "Regras de trabalho")
- [x] Etapa 1 — Rotas quebradas no frontend + funções tipadas em `frontend/src/api/` (rotas já corrigidas na Etapa A da auditoria; criados `api/materials.ts`, `questions.ts`, `exercises.ts`, `career.ts`)
- [x] Etapa 2 — Rotas com backend novo (rotas dos itens 1, 2, 9 e 10 já criadas na Etapa A da auditoria, com testes
  em `ContractEndpointsIntegrationTest`; completado: desarquivar bloco já ativo retorna 409 e `SimuladoRequest`
  valida `quantity` entre 1 e 10 com Bean Validation. Itens 9 e 10 já tinham sido decididos e implementados, sem
  decisão pendente nova). Backend: 132 testes passando.
- [x] Etapa 3 — Agendamento de vagas (já ativado na Etapa B da auditoria; renomeado para `config/SchedulingConfiguration`
  e criado `SchedulingConfigurationConditionTest`: ativo por padrão e desligado com `app.scheduling.enabled=false`,
  valor do perfil de teste)
- [x] Etapa 4 — Limite de upload: multipart em 10MB (`UPLOAD_MAX_FILE_SIZE`/`UPLOAD_MAX_REQUEST_SIZE`), `MaterialFileStorage`
  usa o mesmo valor para PDF/DOCX/PPTX (texto segue em 2 MB), o tratador global de erros devolve 413 em português,
  textos do frontend e README corrigidos. Backend: 138 testes passando.
- [x] Etapa 5 — Tratamento de erros padronizado: `common/exception` (NotFound, BusinessRule, InvalidInput, estendendo
  `ResponseStatusException` para manter códigos e `catch` existentes) + `GlobalExceptionHandler` com resposta única
  `{ status, error, message, fields, timestamp }`, locale pt-BR fixo para validação, 500 sem stack trace. Services
  convertidos um pacote por commit e mensagens traduzidas. Erros de provedores externos (429/502/503/504) seguem
  como `ResponseStatusException`, no mesmo formato. `http.ts` já lia `message`. Backend: 145 testes.
- [x] Etapa 6 — Integração com o Gemini (plano e o que foi feito abaixo). Backend: 169 testes.
- [x] Etapa 7 — Vídeos do YouTube como fonte (plano abaixo; playlist: D2 decidida — vários links de uma vez). Backend: 212 testes.
- [x] Etapa 8 — Questões teóricas abertas com correção rigorosa (plano abaixo). Backend: 225 testes.
- [x] Etapa 9 — Limpeza: `.idea/` fora do Git e ignorada; `career.css` já tinha sido removido (29/10); tipos de fonte em
  `frontend/src/types/material.ts` e decisão D3 [!] (AddSourceModal sem uso); avisos set-state-in-effect corrigidos com
  `hooks/useDeferredEffect` (lint sem avisos).
- [x] Etapa 10 — Testes no frontend: Vitest + React Testing Library (jsdom), scripts `test` e `test:run`; 32 testes
  (http.ts, contrato das funções de API, player do YouTube, feedback da questão aberta, Button e utilitários).
- [x] Etapa 11 — CI no GitHub Actions: `.github/workflows/ci.yml` (backend com Java 21 Temurin, cache Maven e
  PostgreSQL 17 `estudos_test`; frontend com Node LTS, cache npm, `npm ci`, lint, build e `test:run`); `mvnw` executável
  no Git; badge no README.
- [ ] Etapa 12 — Telas de Metas e Sessões
- [ ] Etapa 13 — Documentação
- [ ] Etapa 14 — Decisões de arquitetura (só registrar)

## Plano da Etapa 6 — Gemini

O que já existe (Etapa F da auditoria): `ai/GeminiModelProvider` (REST `v1beta/models/{modelo}:generateContent`,
cabeçalho `x-goog-api-key`, JSON via `responseMimeType`) atrás de `ai/AiProviderRouter`, que implementa
`AiTutorProvider` e escolhe o provedor por operação (rota no banco, fallback, limite diário). Por isso o Gemini
continua como `AiModelProvider` atrás do roteador, em vez de um segundo `AiTutorProvider` (dois beans da mesma porta
quebrariam a injeção). O conteúdo das fontes já vai delimitado como dado, e o `SourceContextSelector` já escolhe
trechos por relevância (blocos de 1.200 caracteres, até 10 mil por fonte e 30 mil no total).

Documentação oficial conferida em 2026-10-30: endpoint e formato confirmados; `gemini-2.5-flash` agora é restrito
a quem já o usava (novos projetos: `gemini-3.8-flash` / `gemini-3.5-flash-lite`); `temperature` é ignorado no
Gemini 3.6+ e passará a dar 400; o 429 vem como `RESOURCE_EXHAUSTED` (RPM/TPM por minuto, RPD zera à meia-noite
do Pacífico).

1. `GeminiProperties` no padrão de `OpenAiProperties` (chave `GEMINI_API_KEY`, modelo, timeouts de conexão e de
   resposta, limite por minuto) + limite local por minuto no provedor + `HttpClient` injetável para testes.
   Resposta que não é JSON vira 502 (hoje vira "falha de rede"). Modelo padrão atualizado.
2. `app.ai.provider` (`AI_PROVIDER` = `openai` | `anthropic` | `gemini`): provedor padrão das operações sem rota
   salva no banco. Sem chave: 503 controlado (já existe no roteador).
3. 429 do Gemini: mensagem em português que diferencia cota diária do plano gratuito de limite por minuto.
4. Geração de questões, exercícios e extração de tópicos usam só fontes **ativas no chat** (hoje usam todas as
   ativas; o chat já filtrava).
5. Geração variada: o prompt recebe os enunciados já existentes do bloco/tópico com a instrução de não repetir;
   JSON continua validado no backend. (Questões teóricas sempre abertas: Etapa 8.)
6. Toda questão gerada fica vinculada a um tópico: sem tópico escolhido, o prompt lista os tópicos ativos do bloco,
   a IA devolve `topic` em cada questão e o backend associa ao tópico existente (fallback por similaridade).
7. Trechos relevantes: já atendido pelo `SourceContextSelector` (consulta = título + descrição do tópico); conferido,
   sem mudança.
8. Testes com HTTP do Gemini simulado: sucesso, sem chave, cota esgotada, timeout e JSON inválido; mais testes de
   `app.ai.provider`, filtro de chat, perguntas existentes no prompt e vínculo com tópico.
9. `.env.example` e README: como criar a chave gratuita no Google AI Studio e configurá-la no `.env` da raiz.

## Plano da Etapa 7 — Vídeos do YouTube

Hoje: `YoutubeUrlParser` já aceita youtu.be, watch, shorts, embed, live e m./www.; a importação
(`POST …/materials/youtube`, `{url}`) baixa a transcrição de forma síncrona (`HttpYoutubeTranscriptExtractor`, sem
marcações de tempo) e falha com 422 sem legendas. O frontend não tem prévia nem player.

Documentação oficial conferida em 2026-10-30: vídeo do YouTube por URL é *preview*, só vídeos **públicos**,
até 8 h de vídeo por dia no plano gratuito, ~100 tokens/s; o exemplo oficial usa a API `interactions`; no
`generateContent` usamos a parte `fileData.fileUri` (padrão de arquivo por URI). Qualquer falha cai na
transcrição. oEmbed devolve título, canal e miniatura (sem duração).

1. Parser: testes para youtu.be com `?si=`, watch com `&t=`/`&list=`, shorts, embed, live e m.; parâmetros de
   rastreamento ignorados (o vídeo é sempre salvo pela URL canônica).
2. Prévia: `GET …/materials/youtube/preview?url=` no backend, via oEmbed público (`YoutubeOEmbedClient`, sem
   chave) → id, título, canal, miniatura, URL do player e se já foi importado. Tela mostra antes de salvar.
3. Player: componente com a YouTube IFrame Player API em `youtube-nocookie.com`, com `seekTo(segundos)`; fontes
   YOUTUBE abrem o player na área do bloco.
4. Análise por IA: `YoutubeVideoAnalyzer` envia a URL ao Gemini uma única vez (operação nova
   `VIDEO_ANALYSIS`, registrada no consumo) pedindo JSON validado: resumo, tópicos e anotações com `MM:SS`,
   incluindo código mostrado. O texto montado vira o conteúdo da fonte (chat, tópicos e questões usam).
5. Plano B: Gemini indisponível, 429, vídeo privado/não listado, JSON inválido ou outra falha → transcrição
   (agora com marcações `[MM:SS]` a cada ~30 s). Campo `analysis_method` = `ANALISE_IA` | `TRANSCRICAO`, exibido.
6. Processamento assíncrono: a importação salva a fonte como `PROCESSING` e devolve na hora; um executor
   processa e marca `COMPLETED` ou `FAILED` (com mensagem). A tela acompanha e oferece "Tentar novamente"
   (`POST …/materials/{id}/reprocess`). No perfil de teste o executor é síncrono.
7. Minutos clicáveis: no chat e nos trechos de questões, `MM:SS`/`H:MM:SS` viram link que abre o player no
   ponto (vídeo da fonte citada; no chat, a fonte YouTube ativa).
8. Migração V26: canal, miniatura, duração e método de análise (o id do vídeo já existe: `external_video_id`).
9. Testes com Gemini, oEmbed e transcrição simulados: link com `?si=`, análise com sucesso, cota esgotada →
   transcrição, vídeo privado, JSON inválido, reprocessamento.
10. Aviso na tela: limite de ~8 h de vídeo por dia no plano gratuito e só vídeos públicos.
- [x] Ponto de decisão D2 (playlist): decidido em 2026-10-30 — colar vários links de uma vez (opção C), feito antes
  da Etapa 8 (`features/sources/YoutubeBatchImport.tsx`, até 50 links por envio, repetidos descartados).

## Plano da Etapa 8 — Questões teóricas abertas com correção rigorosa

Hoje: a geração aceita múltipla escolha, V/F e discursiva; a discursiva é corrigida por palavras-chave
(`evaluateDiscursiveCriteria`, 60% dos termos) dentro de `@Transactional`; `StudyQuestionResponse` e a revisão
global expõem `expectedAnswer`/`explanation` **antes** da resposta; `QuestionEvaluationStatus` só tem
CORRECT/INCORRECT/UNGRADABLE; a revisão espaçada só conhece acerto/erro; por tópico há contagem de acertos, sem
nota média.

Nomes: as classificações pedidas (CORRETA, PARCIAL, INCORRETA, PENDENTE_DE_AVALIACAO) viram os valores do enum
existente em inglês — `CORRECT`, `PARTIAL`, `INCORRECT`, `PENDING_EVALUATION` — e aparecem em português na tela.

1. Formato: a geração da área teórica (gerar, simulado e revisão) passa a criar só questões abertas
   (`DISCURSIVE`); o formato pedido é ignorado. Objetivas antigas e seus históricos continuam respondíveis; a
   criação manual com alternativas continua no backend. O seletor de formato sai do modal de simulado.
2. Geração com rubrica: JSON validado por questão — enunciado que exige explicação, resposta-modelo só com o
   material, rubrica (conceitos essenciais, complementares, termos do material, erros comuns), trecho literal
   (+ minuto, se vídeo), tópico e dificuldade. Questão cujo trecho não aparece no material é descartada.
   Campos novos em `study_questions` (V27): `model_answer`, `rubric_json`, `source_timestamp`.
3. Sigilo: para questões abertas, `expectedAnswer`, `explanation` e a rubrica não saem nas listagens (bloco e
   revisão global); só voltam no feedback, depois da resposta.
4. `DiscursiveAnswerEvaluator` (operação de IA nova `ANSWER_EVALUATION`): a IA dá notas 0–100 por critério
   (exatidão, cobertura dos essenciais, profundidade, terminologia, ausência de erros) e lista acertos, faltas,
   termos e erros; **o backend** calcula a nota geral (média ponderada) e aplica as regras de rigor
   (falta essencial → no máx. PARCIAL; erro conceitual → penalização forte; superficial → nunca CORRETA; sem a
   terminologia → no máx. PARCIAL no RIGOROSO; lista de palavras → INCORRETA; CORRETA só com ≥ 85 e todos os
   essenciais). Resposta delimitada como dado; tentativa de manipulação detectada também no backend.
   Temperatura baixa onde o modelo aceita (`AiTutorRequest.temperature`; OpenAI e Gemini 2.x; Gemini 3.6+
   ignora o parâmetro e ele não é enviado). Chamada de IA fora de transação.
5. Feedback na tela: classificação e nota, o que acertou, essenciais faltando, termos do material, erros,
   resposta-modelo, trecho (minuto clicável se vídeo) e orientação curta.
6. Persistência: tentativa guarda classificação, nota, notas por critério, feedback e nível de rigor (snapshot);
   desempenho por tópico ganha nota média; PARCIAL não conta como acerto; PENDENTE fica fora das estatísticas.
   Revisão: INCORRETA reinicia, PARCIAL mantém intervalo curto, CORRETA avança.
7. Responder de novo a mesma pergunta após o feedback (nova tentativa).
8. Sem IA: tentativa `PENDING_EVALUATION` (nada de palavras-chave), aviso na tela e reavaliação depois
   (`POST …/questions/{id}/attempts/{attemptId}/evaluate`).
9. Rigor em Configurações (`RIGOROSO` padrão, `MODERADO`), por usuário.
10. Migração V27 para os campos e a tabela de preferência.
11. Testes com IA simulada: completa (CORRETA), superficial (PARCIAL), sem terminologia (PARCIAL no RIGOROSO),
    palavras soltas (INCORRETA), afirmação errada, manipulação, IA indisponível (PENDENTE), JSON inválido e
    resposta-modelo não exposta antes da resposta.

## Avaliação prática (pedido do usuário em 2026-10-30, fora da sequência de etapas)

- [x] Tutoria por chat dentro do exercício: cada resposta da IA é classificada pelo nível de ajuda
  (`ExerciseHelpLevel`) e vira desconto de autonomia (`ExerciseAutonomyCalculator`; limite de 50%, solução
  revelada limita a 30% e não conta para domínio). Migração V28.
- [x] Exercícios gerados de qualquer bloco, com foco opcional em tópico ou material, divididos em etapas.
- [x] Correção automática em Docker (`runner/java`, `DockerCodeRunner`): testes JUnit gerados pela IA e validados
  com a solução de referência, checagens de conceito e regras de boas práticas (JavaParser). Nota
  (`PracticalScoreCalculator`): corretude 50% + conceito 25% + boas práticas 25%, × autonomia. Sem Docker ou sem
  a imagem, cai na autoavaliação. Migração V29.
- [x] Desempenho do tópico = 70% prática + 30% teoria (`TopicMasteryCalculator`); "dominado" exige prática ≥ 85%
  em 3 exercícios e teoria consistente ≥ 80%.
- Backend: 235 testes, 226 passando (em 2026-10-30, depois da Etapa 8, 249 testes e nenhuma falha); as 9 falhas (questões: `EstudosBackendApplicationTests`, `Etapa5…`, `Etapa7…`,
  `AiQuestionGenerationVarietyTest`) já existiam na `master` com a Etapa 8 em andamento.

## Melhorias da IA (pedido do usuário em 2026-10-30, fora da sequência de etapas)

Ordem combinada: 1 → 3 → 2 → 4. Decisão do usuário: a área teórica continua só com questões abertas (Etapa 8).

- [x] 1. Questões teóricas: feedback no formato "Parabéns" (CORRETA) ou "Correção → Resposta correta → Explicação"
  (PARCIAL/INCORRETA), com `explanation` e `commonMistakes` da rubrica no feedback; modo estudo
  (`GET …/questions/{id}/study-view`: resposta-modelo, explicação, conceitos essenciais e erros comuns, sem gravar
  tentativa nem mexer na revisão espaçada); prompt de geração pede erros comuns no formato "se você achou que…,
  …" e explicação detalhada. Sem migração.
- [x] 3. Limites da API: cache de respostas (`AiResponseCacheService`, migração V30, chave SHA-256 de usuário +
  operação + provedor + modelo + pedido, validade `app.ai.cache.ttl-hours` = 168 h) só nas operações
  `cacheable` (tópicos, resumos, análises e correção; geração, chat e tutoria nunca); 429 em todos os provedores
  vira mensagem clara; `GET/DELETE /api/ai/cache` e quadro "Cache de respostas" em Configurações. O lote já existe
  (N questões numa chamada) e o consumo já era registrado em `ai_usage_records`.
- [x] 2. Qualidade: modelo por operação na rota (migração V31, coluna "Modelo" em Configurações; vazio = modelo do
  provedor; no fallback vale o modelo do outro provedor), ex.: 3.5 Flash Lite para gerar e 3.8 Flash para corrigir.
  Temperatura/top_p: o Gemini 3.6+ ignora (já documentado no `GeminiModelProvider`); a correção já pede temperatura
  baixa onde o modelo aceita. Validações já existentes mantidas (trecho precisa estar no material, rubrica com
  conceito essencial, JSON forçado). Ajuste fino não feito: pouco dado e custo alto; o caminho é few-shot.
- [x] 4. Reinício: `AiProvidersHealthIndicator` (componente `aiProviders` em `/actuator/health`, sem chamar as APIs;
  fica UP sem IA porque há fallbacks) e detalhes do health ligados. `start-dev.ps1` sobe Docker Desktop e
  PostgreSQL, abre backend e frontend pelo `dev-restart-loop.ps1` (reinicia se cair; para após 5 quedas em
  10 min; Java com `-Xmx768m`) e espera o health antes de abrir o navegador. PM2/supervisord descartados: o
  supervisord não roda no Windows e o PM2 seria mais um processo Node na máquina com pouca RAM.

## Validação manual dos tópicos (pedido do usuário em 2026-10-30, fora da sequência de etapas)

Decisões do usuário: tópicos `ACTIVE` existentes continuam oficiais; com sugestões pendentes, as questões continuam
usando o bloco inteiro.

- [x] Modelo (migração V32): status `PENDING_REVIEW` (fora de questões, desempenho e carreira), relevância 0–100 e
  justificativa, rodada, decisão (`TopicReviewState`), comentário e título anterior; tabela `topic_review_decisions`.
- [x] Extração (`SourceTopicExtractor`): só tópicos técnicos, agrupados, com relevância; backend descarta relevância
  < 40, ruído de transcrição (saudação, curso, like), trecho que não está na fonte e títulos já excluídos. Sem IA, o
  fallback filtra as mesmas frases e dá relevância por tipo de evidência. Limite de saída explícito (8000 tokens).
- [x] Fluxo (`TopicReviewService`, `…/topics/review`): aprovar, excluir (o principal leva os filhos), comentar,
  renomear, aprovar em lote por relevância, revisar com IA os comentados (nova rodada, com "antes → depois" e
  subtópicos pedidos) e oficializar. Excluído nunca volta na extração, nem com o tópico principal renomeado.
- [x] Memória: os últimos excluídos e comentários do usuário entram no prompt da próxima extração; taxa de aprovação
  na 1ª rodada exibida como precisão da extração.
- [x] Tela: painel "Sugestões para validar" no topo da aba Mapeamento de Fontes (relevância colorida, trecho com
  minuto clicável se vídeo, progresso, atalhos A/E/C); o modal do simulado avisa quando há sugestões pendentes.

## Observações

- O hook Stop do projeto (`.claude/hooks/auto-commit-push.sh`) faz commit **e push** ao fim de cada resposta.
  Decisão D1 (2026-10-30): o usuário mantém o hook como está; os commits por etapa continuam manuais.
- A máquina de desenvolvimento tem pouca RAM livre: logs de build vão para arquivo e os testes do backend
  rodam com memória limitada (`maven-surefire-plugin` no `backend/pom.xml`).
