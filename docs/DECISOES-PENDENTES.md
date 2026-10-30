# Decisões pendentes

Cada item traz contexto, opções, prós e contras e uma recomendação. Nada aqui foi implementado.

## D1 — Hook de commit e push automático × regra "não faça push"

**Contexto:** o `docs/VISAO-PRODUTO.md` pede um commit por etapa e nenhum push. O hook Stop
`.claude/hooks/auto-commit-push.sh` (configurado em `.claude/settings.local.json`) faz commit de tudo e push para
`origin/master` ao fim de cada resposta do assistente.

| Opção | Prós | Contras |
|---|---|---|
| A. Manter o hook como está | Nada se perde se a sessão cair | Viola "não faça push"; commits `chore(auto)` misturados aos commits por etapa |
| B. Hook só faz commit, sem push | Mantém a rede de segurança local; push fica com você | Commits `chore(auto)` continuam aparecendo no histórico |
| C. Desativar o hook | Histórico limpo, um commit por etapa | Trabalho não commitado se perde se a sessão cair no meio |

**Recomendação:** B — tira o push automático (a regra do plano) e mantém a proteção contra perda de trabalho.
