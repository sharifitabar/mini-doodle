# Documentation writing rules

## Standard and scope

Write project documentation in ASD-STE100 Simplified Technical English (STE), Issue 9.
Apply this rule to README files, ADRs, specifications, plans, research, tasks, and procedures.
Apply it to contributor and agent instructions.

Use the [local Issue 9 PDF](ASD-STE100_ISSUE9.pdf) as the authority.
The PDF contains the writing rules in Part 1 and the dictionary in Part 2.
This guide gives project instructions. It does not replace the standard.
The [official STE website](https://www.asd-ste100.org/) gives information about the standard.

Read these parts before you change documentation:

| Subject | Reference in the PDF |
|---|---|
| Dictionary words, meanings, and parts of speech | Rules 1.1–1.4, pages 1-1-1 through 1-1-6 |
| Technical nouns | Rules 1.5–1.11, pages 1-1-6 through 1-1-13 |
| Technical verbs | Rules 1.12–1.13, pages 1-1-13 through 1-1-17 |
| Verb forms and active voice | Rules 3.1–3.7, Section 3 |
| Sentences and articles | Rules 4.1–4.5, Section 4 |
| Procedures | Rules 5.1–5.5, Section 5 |
| Descriptions | Rules 6.1–6.6, Section 6 |
| Punctuation and word count | Rules 8.1–8.7, Section 8 |
| Sentence construction and consistent terms | Rules 9.1–9.4, Section 9 |
| Word meanings and permitted alternatives | Part 2, Dictionary |

## Words and technical terms

- Use approved dictionary words with their approved meaning and part of speech.
- Use American English spelling.
- Use technical nouns only in the categories in rule 1.5.
  Use technical verbs only in the categories in rule 1.12.
  A glossary entry alone does not make a term permitted.
- Use an approved dictionary verb if it gives the correct meaning.
  Do not use a technical noun as a verb (rule 1.7).
  Do not use a technical verb as a noun (rule 1.13).
- Use the same term for the same concept throughout a document.
  Use a glossary definition if the project has one.
- Explain a new technical term or abbreviation before you use it without an explanation.
  Keep official product names and interface names exact.
- Use the complete term if a short form could have more than one meaning.

The project uses these technical terms:

| Term | Meaning |
|---|---|
| Time slot | A calendar interval with a start time, duration, and availability status. |
| Meeting | A booking on a time slot. |
| Caller | The user identified by an API request. |
| Aggregate | A domain root and its related entities, which are saved as one unit. |
| Port | An interface between the application or domain and another component. |
| Adapter | A component that implements or calls a port for an external interface. |
| Coverage floor | The minimum permitted test coverage ratio. |
| Complexity ceiling | The maximum permitted value of a code complexity metric. |
| Gate | A check that must pass before an operation can continue. |
| Fixture | Test data or a test type for a check of a defined condition. |
| Contention | Concurrent operations competing for a database resource. |

These are software technical nouns, not additional dictionary words.
They identify system concepts, software components, processes, and measurements (rule 1.5).
Use them only with these software meanings.

Software technical verbs must identify specific computer operations (rule 1.12, category 2).
For example:

| Technical verb | Computer operation |
|---|---|
| Commit | Record changes as one Git revision or finish a database transaction. State which operation applies. |
| Query | Send a database query or an API query. |
| Parse | Convert input text to its structured representation. |
| Serialize | Convert structured data to its transfer or storage format. |
| Provision | Prepare the configuration or data for a software component. |
| Seed | Put the specified initial data in the database. |
| Scrape | Collect the published metrics from a software endpoint. |

Use these verbs only for the specified operations.
Do not use a general word as a technical term to bypass a dictionary restriction.

### Dictionary examples

The dictionary permits `check` and `test` as nouns, not as general verbs.
It gives alternatives for `verify`, `confirm`, `ensure`, and `follow`.
Select the alternative that gives the original meaning:

| General wording | STE construction |
|---|---|
| Check the response status. | Do a check of the response status. |
| Verify that the build passes. | Make sure that the build passes. |
| Test the service. | Do a test of the service. |
| Follow the instructions. | Obey the instructions. |

These examples apply to prose. Keep command names and identifiers unchanged.

## Sentences and paragraphs

- Use short sentences. Limit procedural sentences to 20 words.
  Limit descriptive sentences to 25 words.
  Apply rules 8.4–8.7 when you count words.
  Count parenthesized text as one word.
  Count numbers with units, identifiers, abbreviations, quotations, and hyphenated words as specified by those rules.
- Give one instruction in each sentence. Rule 5.2 permits combined actions only when they occur at the same time.
- Use the active voice in procedures.
  In descriptions, use the passive voice only when the actor is unknown (rule 3.6).
- Start an instruction with an imperative verb.
  Put a necessary condition before the instruction.
- State the subject. Keep articles and necessary connecting words.
- Use the verb forms in rules 3.1–3.4.
  Do not use perfect or progressive tenses.
  Use an `-ing` form only as a technical noun or as its modifier (rule 3.5).
- Limit noun groups to three words (rule 2.1).
  For a longer official technical noun, use the methods in rule 2.2.
- Keep instructions out of notes (rule 5.5).
  Put an instruction in the procedure where the reader must do it.
- Keep one topic in each paragraph. Use no more than six sentences per paragraph.
- Use lists for separate conditions, alternatives, or steps.
  Use a number for each step that must occur in sequence.
- Use punctuation permitted by STE. Do not use semicolons in prose.

## Keep technical meaning

A language edit must keep all technical meaning.

- Keep requirement IDs, priorities, acceptance conditions, and task completion marks.
- Keep formal requirement words such as `MUST`, `MUST NOT`, `SHOULD`, and `MAY` with their original force.
  Treat them as specification tokens.
  The dictionary suggests `MUST` for general `should` and `CAN` for general `may`.
  Do not apply these replacements to formal tokens: they would change the requirement.
- Keep numbers, units, limits, comparisons, dates, measurements, and version numbers.
- Keep commands, code blocks, identifiers, file paths, links, and literal interface values exact.
- Keep recorded user input and direct quotations exact.
- Keep ADR status, alternatives, reasons, and consequences.
- Keep historical plans and measurements in their original time context.
  Report a technical inconsistency separately. A language edit does not authorize a technical change.

## Required review

1. Read the full passage and identify its technical meaning.
2. Rewrite the prose with the official STE rules and dictionary.
3. Compare the changed text with the original.
4. Examine each condition, exception, obligation, number, and technical reference.
5. Examine sentence length, voice, terminology, and dictionary entries.
   Make sure that each word has a permitted meaning and part of speech.
6. Examine the Markdown structure and the diff.
7. Correct errors before you finish. Report any part of the STE review that you could not complete.

A readability score or word-replacement script cannot prove STE compliance.
Claim full compliance only after a complete review against the official rules and dictionary.
