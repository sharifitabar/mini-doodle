# AGENTS.md

## Approval before committing or pushing

- NEVER run `git commit` or `git push` unless the user has explicitly approved it for the current work.
- Implement and verify first, then STOP and ask for approval (e.g. "shall I commit?"). Wait for an explicit yes.
- Approval is per-change; a previous approval does not carry over to later changes. Do not push without approval even if a commit was approved.
- When approval is given, commit and push exactly the approved changes — do not bundle unrelated work.
- Before pushing, run the full CI build locally (`./gradlew build --no-daemon --console=plain`) and confirm it passes. Never push with a red build.
- A versioned `.githooks/pre-push` hook runs that build and blocks a failing push. Enable it once per clone with `git config core.hooksPath .githooks`.

## Style gate

Linting and formatting are wired into the standard `check` lifecycle, so `./gradlew build`
(the same command the pre-push hook and CI run) already enforces them. Do not add a separate
hook or CI step for style.

- **Before asking to commit**, run `./gradlew spotlessApply` to auto-fix formatting, then
  `./gradlew check` to confirm the gate is green. A red gate is never pushed.
- Spotless owns formatting (indentation, wrapping, whitespace, import order, unused imports);
  Checkstyle owns naming/brace/block/bug-pattern rules in `config/checkstyle/checkstyle.xml`.
  Never "fix" a formatting complaint by hand-editing for style — use `spotlessApply`.
- Generated jOOQ sources under `build/generated-sources` are excluded from both tools; never add
  manual formatting there or lift the exclusion.
- Versions are pinned (`build.gradle.kts`). Do not bump Spotless, google-java-format, or
  Checkstyle casually: google-java-format is capped at `1.36.0` because 1.37.0 breaks Spotless's
  reflective glue (see `specs/005-checkstyle-enforcement/research.md`).

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