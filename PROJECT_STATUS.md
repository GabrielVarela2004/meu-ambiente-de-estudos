# Status do Projeto — Plataforma de Estudos com IA

Atualizado em 2026-10-31. Referências: plano em `docs/VISAO-PRODUTO.md`, andamento em `docs/PLANO-EXECUCAO.md`,
problemas conhecidos em `docs/AUDITORIA.md`, decisões em `docs/DECISOES-PENDENTES.md`.

## Números

| Item | Valor |
|---|---|
| Testes do backend | 237 passando, 1 ignorado (linha de base em 30/10: 131) |
| Testes do frontend | 46 passando (antes da Etapa 10 não havia testes) |
| Migrações Flyway | V1 a V32 |
| Lint (oxlint) e build | sem erros; 1 aviso antigo de fast refresh em `GoalsSessionsPage.tsx` |
| CI | GitHub Actions em push e PR na `master` |

## Etapas do plano (`docs/VISAO-PRODUTO.md`)

| Etapa | Estado |
|---|---|
| 0 Contexto (`CLAUDE.md`) | ✅ |
| 1 Rotas quebradas no frontend + funções tipadas | ✅ |
| 2 Rotas com backend novo | ✅ |
| 3 Agendamento de vagas | ✅ ativo por padrão (`SCHEDULING_ENABLED`) |
| 4 Limite de upload (10 MB; texto e código 2 MB; 413 em português) | ✅ |
| 5 Tratamento de erros padronizado | ✅ |
| 6 Integração com o Gemini | ✅ |
| 7 Vídeos do YouTube (análise por IA, transcrição como plano B) | ✅ playlist inteira: não (D2) |
| 8 Questões teóricas abertas com correção rigorosa | ✅ |
| 9 Limpeza | ✅ `AddSourceModal` sem uso aguarda D3 |
| 10 Testes no frontend | ✅ |
| 11 CI | ✅ |
| 12 Telas de Metas e Sessões | ✅ |
| 13 Documentação | ✅ (este arquivo, `README.md` e `ARCHITECTURE.md`) |
| 14 Decisões de arquitetura | pendente |

## Pronto e funcionando

- Blocos, fontes (arquivos, página web, YouTube), chat do bloco e universal com citações.
- IA com OpenAI, Anthropic e Gemini, roteada por operação, com fallback, limite diário, consumo e cache.
- Questões abertas com rubrica e correção Rigorosa/Moderada; pendentes quando não há IA.
- Exercícios práticos com tutoria e correção automática em Docker (Java).
- Repetição espaçada, Desempenho Global por bloco e análises semanais/mensais.
- Metas e Sessões com cronômetro.
- Carreira: vagas Remotive/Arbeitnow, compatibilidade por bloco, análise por IA, perfil, vaga → bloco.

## Mudanças fora do plano (pedidos do usuário)

- Avaliação prática com tutoria e autonomia; melhorias da IA (cache, mensagens de limite, modelo por operação).
- 2026-10-31: **Mapeamento de Fontes removido** (tópicos, subtópicos e validação). Desempenho, questões, exercícios e
  Carreira passaram a trabalhar só por bloco. As tabelas antigas ficaram no banco sem uso (D4).

## Falta / limitações

- Sem autenticação: o frontend não envia `X-Study-User-Id`; tudo é do `default-user` (Etapa 14).
- Fórum só visual e fora do menu (Etapa 14).
- Decisões aguardando o usuário: D3 (`AddSourceModal`), D4 (apagar tabelas de tópicos). D1 e D2 já decididas.
