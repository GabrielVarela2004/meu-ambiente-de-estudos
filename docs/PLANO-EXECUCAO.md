# Plano de execução — docs/VISAO-PRODUTO.md

Legenda: `[ ]` pendente · `[x]` concluída · `[!]` aguardando decisão (ver `docs/DECISOES-PENDENTES.md`).

Linha de base (2026-10-30, antes da Etapa 0): backend com 131 testes passando; frontend sem testes automatizados.

Várias etapas já tinham sido cobertas pelo plano do `docs/AUDITORIA.md` (Etapas A–J, concluídas em 2026-10-29/30).
Nesses casos a etapa aqui é só conferida e completada no que faltar.

- [ ] Etapa 0 — Contexto (`CLAUDE.md` com "Regras de trabalho")
- [ ] Etapa 1 — Rotas quebradas no frontend + funções tipadas em `frontend/src/api/`
- [ ] Etapa 2 — Rotas com backend novo (desarquivar, simulado; itens 9 e 10)
- [ ] Etapa 3 — Agendamento de vagas
- [ ] Etapa 4 — Limite de upload (10MB, 413)
- [ ] Etapa 5 — Tratamento de erros padronizado
- [ ] Etapa 6 — Integração com o Gemini
- [ ] Etapa 7 — Vídeos do YouTube como fonte
- [ ] Etapa 8 — Questões teóricas abertas com correção rigorosa
- [ ] Etapa 9 — Limpeza
- [ ] Etapa 10 — Testes no frontend (Vitest)
- [ ] Etapa 11 — CI no GitHub Actions
- [ ] Etapa 12 — Telas de Metas e Sessões
- [ ] Etapa 13 — Documentação
- [ ] Etapa 14 — Decisões de arquitetura (só registrar)

## Observações

- O hook Stop do projeto (`.claude/hooks/auto-commit-push.sh`) faz commit **e push** ao fim de cada resposta,
  o que conflita com a regra "não faça push". Registrado em `docs/DECISOES-PENDENTES.md` (D1); até a decisão,
  os commits por etapa são feitos manualmente e o hook continua como está.
- A máquina de desenvolvimento tem pouca RAM livre: logs de build vão para arquivo e os testes do backend
  rodam com memória limitada (`maven-surefire-plugin` no `backend/pom.xml`).
