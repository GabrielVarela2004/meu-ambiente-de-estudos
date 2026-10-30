#!/usr/bin/env bash
# Hook "Stop" do Claude Code: ao fim de cada resposta, se houver alterações
# no repositório, faz commit de tudo e envia para o remoto.
# Arquivos ignorados pelo .gitignore (.env, node_modules, target...) nunca entram.

cd "$CLAUDE_PROJECT_DIR" 2>/dev/null || cd "$(dirname "$0")/../.." || exit 0

# Alterações não commitadas → commit automático de tudo.
if [ -n "$(git status --porcelain)" ]; then
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
fi

# Envia os commits à frente do remoto, inclusive os feitos manualmente ao longo da resposta.
branch=$(git rev-parse --abbrev-ref HEAD)
ahead=$(git rev-list --count "origin/$branch..HEAD" 2>/dev/null || echo 1)
[ "$ahead" = "0" ] && exit 0

if git push -q origin "$branch" 2>/tmp/claude-auto-push.err; then
  echo "{\"systemMessage\": \"Auto-push: $ahead commit(s) enviados para origin/$branch.\"}"
else
  reason=$(head -1 /tmp/claude-auto-push.err | tr -d '"\\')
  echo "{\"systemMessage\": \"Auto-push: o push de $ahead commit(s) para origin/$branch falhou ($reason). Se o GitHub tiver commits novos, faça git fetch e merge antes.\"}"
fi
exit 0
