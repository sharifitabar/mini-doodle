# Conventional Commits v1.0.0

This project follows the [Conventional Commits v1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) specification for all Git commit messages.

## Format

```
<type>[optional scope]: <description>

[optional body>

[optional footer(s)>
```

## Types

| Type | Use when |
|---|---|
| `feat` | Adding a new feature |
| `fix` | Fixing a bug |
| `docs` | Documentation changes only |
| `style` | Code style/formatting (no logic change) |
| `refactor` | Code refactoring (no feature/bug change) |
| `perf` | Performance improvements |
| `test` | Adding/updating tests |
| `build` | Build system, dependencies, tooling |
| `ci` | CI/CD configuration changes |
| `chore` | Routine maintenance |
| `revert` | Reverting a previous commit |

## Rules

1. Use **imperative mood** in the description ("add", "fix", "remove", not "added", "fixed").
2. Don't end description with a period.
3. Use `!` after type/scope or `BREAKING CHANGE:` in footer for breaking changes.
4. Keep description short (50–72 characters ideal).
5. Use body/footer to explain why, not just what.
6. Reference issues in footers (e.g., `Closes #123`, `Refs #456`).
7. Make commits atomic and focused.

## Examples

```text
feat: add user profile page
fix(auth): prevent token refresh loop
docs: clarify setup instructions
refactor(api): simplify error handling
feat(api)!: remove legacy v1 endpoints

BREAKING CHANGE: All v1 API routes are removed.
```

## Validation

Any agentic AI assisting with commits should follow this format. Use `git log --oneline` or staged changes to infer the correct type/scope.