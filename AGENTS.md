# AGENTS.md

## Approval before committing or pushing

- NEVER run `git commit` or `git push` unless the user has explicitly approved it for the current work.
- Implement and verify first, then STOP and ask for approval (e.g. "shall I commit?"). Wait for an explicit yes.
- Approval is per-change; a previous approval does not carry over to later changes. Do not push without approval even if a commit was approved.
- When approval is given, commit and push exactly the approved changes — do not bundle unrelated work.
- Before pushing, run the full CI build locally (`./gradlew build --no-daemon --console=plain`) and confirm it passes. Never push with a red build.
- A versioned `.githooks/pre-push` hook runs that build and blocks a failing push. Enable it once per clone with `git config core.hooksPath .githooks`.

## Commit Conventions

All commits MUST follow [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

See [docs/CONVENTIONAL_COMMITS.md](docs/CONVENTIONAL_COMMITS.md) for full rules, types, and examples.

### Quick rules for agents

- Use format: `<type>[scope]: <imperative description>`
- Valid types: feat, fix, docs, style, refactor, perf, test, build, ci, chore, revert
- Mark breaking changes with `!` or `BREAKING CHANGE:` footer
- Keep description in imperative mood, no trailing period
- Add body/footer only when helpful (explain context, link issues)
- Infer type/scope from changed files when drafting
- Validate before proposing: type valid, format correct, imperative mood

### When asked to commit

1. Inspect staged/unstaged changes (`git diff --staged --stat`, `git diff --stat`)
2. Draft 1–3 candidate messages
3. Explain reasoning
4. Propose final message only after user confirmation (or as requested)