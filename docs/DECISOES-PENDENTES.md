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

## D2 — Importar uma playlist inteira do YouTube de uma vez (Etapa 7)

**Contexto:** hoje cada vídeo é adicionado pelo link e analisado uma vez pelo Gemini (ou pela transcrição).
Listar os vídeos de uma playlist exige a YouTube Data API v3 (`playlistItems.list`), com uma segunda chave
(Google Cloud) e cota própria (10.000 unidades/dia no plano gratuito; cada página de 50 vídeos custa 1 unidade).
O Gemini gratuito aceita cerca de 8 h de vídeo por dia, então playlists longas levariam vários dias para serem
analisadas por IA.

| Opção | Esforço | Prós | Contras |
|---|---|---|---|
| A. Não importar playlists (manter um vídeo por vez) | nenhum | Sem nova chave nem cota; controle total do que entra | Trabalhoso para cursos com muitas aulas |
| B. Importar com a YouTube Data API (`YOUTUBE_API_KEY`) e fila de processamento | médio (1–2 dias) | Um clique para o curso inteiro; a fila respeita o limite diário (os vídeos ficam "na fila" até haver cota) | Segunda chave para criar; mais um serviço externo; análise de playlists grandes leva dias |
| C. Colar vários links de uma vez (um por linha), sem API | baixo (algumas horas) | Sem nova chave; reaproveita a importação atual | O usuário precisa copiar os links da playlist |

**Recomendação:** C agora (resolve o caso comum sem nova dependência) e B depois, se as playlists forem frequentes.
