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
- [ ] Etapa 6 — Integração com o Gemini (em andamento; plano abaixo)
- [ ] Etapa 7 — Vídeos do YouTube como fonte
- [ ] Etapa 8 — Questões teóricas abertas com correção rigorosa
- [ ] Etapa 9 — Limpeza
- [ ] Etapa 10 — Testes no frontend (Vitest)
- [ ] Etapa 11 — CI no GitHub Actions
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
7. Trechos relevantes: já atendido pelo `SourceContextSelector` (consulta = título + descrição do tópico); só conferir.
8. Testes com HTTP do Gemini simulado: sucesso, sem chave, cota esgotada, timeout e JSON inválido; mais testes de
   `app.ai.provider`, filtro de chat, perguntas existentes no prompt e vínculo com tópico.
9. `.env.example` e README: como criar a chave gratuita no Google AI Studio e configurá-la no `.env` da raiz.

## Observações

- O hook Stop do projeto (`.claude/hooks/auto-commit-push.sh`) faz commit **e push** ao fim de cada resposta,
  o que conflita com a regra "não faça push". Registrado em `docs/DECISOES-PENDENTES.md` (D1); até a decisão,
  os commits por etapa são feitos manualmente e o hook continua como está.
- A máquina de desenvolvimento tem pouca RAM livre: logs de build vão para arquivo e os testes do backend
  rodam com memória limitada (`maven-surefire-plugin` no `backend/pom.xml`).
