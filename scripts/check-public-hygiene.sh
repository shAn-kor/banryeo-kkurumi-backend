#!/usr/bin/env bash
set -euo pipefail

blocked='loop-pack|pawshop-worktrees|X-Loopers-Ldap|github\.com/.+/(pawshop|loopers)'
if git rev-parse --git-dir >/dev/null 2>&1; then
  tracked="$(git ls-files --cached --others --exclude-standard)"
else
  tracked="$(find . -type f -not -path './.git/*')"
fi
tracked="$(printf '%s\n' "$tracked" | grep -Ev '^(\./)?scripts/check-public-hygiene\.sh$')"
if printf '%s\n' "$tracked" | xargs -r grep -Eni "$blocked"; then
  echo "공개 금지 문자열을 발견했습니다." >&2
  exit 1
fi
if git rev-parse --git-dir >/dev/null 2>&1; then
  if [[ "${STRICT_NO_REMOTES:-false}" == "true" ]]; then
    test -z "$(git remote)"
  fi
  if git remote -v | grep -Ei "$blocked"; then
    echo "remote에서 공개 금지 문자열을 발견했습니다." >&2
    exit 1
  fi
  if git rev-list --objects --all | grep -Ei "$blocked"; then
    echo "Git object에서 공개 금지 문자열을 발견했습니다." >&2
    exit 1
  fi
fi
test ! -e LICENSE
test ! -e LICENSE.md
echo "Public hygiene: OK"
