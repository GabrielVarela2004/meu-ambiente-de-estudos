#!/usr/bin/env bash
# Hook "Stop" do Claude Code: ao fim de cada resposta, se houver alterações
# no repositório, faz commit de tudo e envia para o remoto.
# Arquivos ignorados pelo .gitignore (.env, node_modules, target...) nunca entram.

cd "$CLAUDE_PROJECT_DIR" 2>/dev/null || cd "$(dirname "$0")/../.." || exit 0

# Sem alterações → nada a fazer.
if [ -z "$(git status --porcelain)" ]; then
  exit 0
fi

git add -A

files=$(git diff --cached --name-only)
count=$(printf '%s\n' "$files" | wc -l | tr -d ' ')
first=$(printf '%s\n' "$files" | head -3 | xargs -n1 basename 2>/dev/null | paste -sd, - | sed 's/,/, /g')
[ "$count" -gt 3 ] && first="$first e mais $((count - 3))"

msg="chore(auto): atualiza $first

Commit automático gerado pelo hook Stop do Claude Code.
Arquivos alterados: $count

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"

if ! git commit -q -m "$msg"; then
  echo '{"systemMessage": "Auto-commit: falha ao criar o commit (veja git status)."}'
  exit 0
fi

branch=$(git rev-parse --abbrev-ref HEAD)
if git push -q origin "$branch" 2>/tmp/claude-auto-push.err; then
  echo "{\"systemMessage\": \"Auto-commit: $count arquivo(s) enviados para origin/$branch.\"}"
else
  echo "{\"systemMessage\": \"Auto-commit: commit criado, mas o push para origin/$branch falhou. Rode git push manualmente.\"}"
fi
exit 0
