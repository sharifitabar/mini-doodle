# CLAUDE.md

## Commit Conventions

Follow [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/). See `docs/CONVENTIONAL_COMMITS.md` for details.

When drafting commits:
- Format: `<type>[optional scope]: <description>`
- Use imperative mood (e.g., "add", "fix", "update")
- Valid types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert
- Breaking: add `!` after scope/type or `BREAKING CHANGE:` in footer
- Description ≤ 72 chars; wrap body/footer at 72
- References: `Closes #123`, `Fixes #123`, `Refs #123`

## Agent skills

### Issue tracker

Issues and specs live as GitHub issues and are managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.