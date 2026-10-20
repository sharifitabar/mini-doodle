# Domain Documentation

Engineering skills should use the domain documentation when they examine this codebase.

## Before exploring, read these

- **`GLOSSARY.md`** at the repository root, or
- **`GLOSSARY-MAP.md`** at the repository root if it exists: it points at one `GLOSSARY.md` per context. Read each one relevant to the topic.
- **`docs/adr/`**: read ADRs that touch the area you're about to work in. In multi-context repositories, also examine `src/<context>/docs/adr/` for context-scoped decisions.

If a file does not exist, **continue silently**. Do not report its absence or propose its creation at this stage.
The `/domain-modeling` skill creates these files when terms or decisions are resolved.
The `/grill-with-docs` and `/improve-codebase-architecture` skills call this skill.

## File structure

Single-context repository (most repositories):

```
/
├── GLOSSARY.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

Multi-context repository (presence of `GLOSSARY-MAP.md` at the root):

```
/
├── GLOSSARY-MAP.md
├── docs/adr/                          ← system-wide decisions
└── src/
    ├── ordering/
    │   ├── GLOSSARY.md
    │   └── docs/adr/                  ← context-specific decisions
    └── billing/
        ├── GLOSSARY.md
        └── docs/adr/
```

## Use the glossary's vocabulary

Use the term defined in `GLOSSARY.md` when you name a domain concept.
This applies to issue titles, refactor proposals, hypotheses, and test names.
Do not use synonyms that the glossary excludes.

If the glossary does not contain a concept, find out if the project uses it.
Reconsider a new term. If the glossary has a gap, record it for `/domain-modeling`.

## Identify ADR conflicts

If your output contradicts an existing ADR, state the conflict explicitly:

> _Contradicts ADR-0007 (event-sourced orders), but worth reopening because…_
