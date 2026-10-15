---
name: conventional-commits
description: Helps write, validate, and explain Git commit messages following the Conventional Commits v1.0.0 specification. Use when writing a commit, improving a commit message, validating a message, generating changelog-friendly commits, or following commit conventions.
---

# Conventional Commits

You help write, review, and enforce Git commit messages that follow the [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) specification.

## When to use

Use this when:
- User says "write a commit message", "craft commit", or "commit this"
- There are staged/unstaged changes and a proper commit message is needed
- User wants to validate or fix an existing commit message
- User asks about Conventional Commits format, types, or rules
- User wants consistent, changelog-friendly commits

## Core Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)>
```

## Rules

- **type**: Required, lowercase, from the allowed types list
- **scope**: Optional, enclosed in parentheses, describes the affected section (e.g., `auth`, `api`, `ui`)
- **description**: Required, short, imperative mood (present tense: "add", "fix", "change" not "added", "fixed", "changed")
- **!**: Add `!` after type/scope to indicate breaking change (e.g., `feat(api)!: ...`)
- **BREAKING CHANGE:**: Can also appear in footer with `BREAKING CHANGE: description`
- **body**: Free-form, explains motivation/context. Separate from description with blank line
- **footers**: Reference issues (`Closes #123`, `Refs #45`) or other metadata. Separate with blank line
- **length**: Keep description concise (ideally 50–72 chars). Body/footers wrap at ~72 chars

## Allowed Types

| Type | Purpose | Example |
|---|---|---|
| `feat` | New feature | `feat: add user authentication` |
| `fix` | Bug fix | `fix: handle null session` |
| `docs` | Documentation only | `docs: update README setup steps` |
| `style` | Formatting, whitespace, missing semicolons (no code change) | `style: format with prettier` |
| `refactor` | Code change that neither fixes bug nor adds feature | `refactor: extract auth helpers` |
| `perf` | Performance improvement | `perf: reduce bundle size` |
| `test` | Adding/updating tests | `test: add auth middleware tests` |
| `build` | Build system or dependencies | `build: bump vite to v7` |
| `ci` | CI/CD config changes | `ci: update GitHub Actions` |
| `chore` | Maintenance tasks | `chore: update .gitignore` |
| `revert` | Revert a previous commit | `revert: revert "feat: add dark mode"` |

## Workflow

When helping with a commit:

1. **Understand context**: If changes exist, inspect staged/unstaged files
   - Suggest: `git diff --staged --stat` and `git diff --staged` (or `--name-only`)
   - If nothing staged, check unstaged with `git diff --stat`
   - Ask user to stage files if preferred

2. **Infer appropriate type/scope**:
   - Look at file paths and change nature
   - Pick most specific type
   - Suggest logical scope from directory (e.g., `src/auth/*` → `auth`)
   - Avoid generic scopes

3. **Draft commit message**:
   - Write clear, imperative description
   - Add body only if non-obvious or needs context
   - Add footers for issues/breaking changes
   - Follow all rules

4. **Validate**:
   - Check format matches spec
   - Ensure type is valid
   - Verify imperative mood
   - Flag breaking changes

5. **Present options**: Offer 2–3 alternatives if ambiguous

## Examples

**Simple feature:**
```
feat: add password reset flow
```

**With scope:**
```
fix(api): prevent race in session refresh
```

**With body and issue ref:**
```
feat(auth): add OAuth2 login

Supports Google and GitHub providers.
Adds token refresh logic.

Closes #42
```

**Breaking change:**
```
feat(api)!: change auth endpoints to v2

BREAKING CHANGE: /auth/login now requires client_id
```

**Revert:**
```
revert: revert "fix: handle empty response"
```

## Quick Validation Checklist

- [ ] Starts with valid type (or `revert:`)
- [ ] Follows `type(scope): description` structure
- [ ] Description uses imperative mood
- [ ] No trailing period in description
- [ ] Breaking changes clearly marked (`!` or footer)
- [ ] Body/footer separated by blank line if present

## Tips

- Prefer `fix` over `chore` for actual bugs
- Use `refactor` when restructuring without behavior change
- Don't use `feat`+`fix` together; pick primary change
- For multi-package repos, scope helps clarity
- Keep commits atomic and focused

Always produce messages that satisfy the exact Conventional Commits v1.0.0 specification. When in doubt, explain reasoning and ask for confirmation before suggesting the final message.