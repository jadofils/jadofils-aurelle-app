# Contract changelog

Record every change to a contract file after it is `frozen`. Newest first.

| Date | Phase | File | Change | Breaking? | Proposal |
| --- | --- | --- | --- | --- | --- |
| 2026-11-14 | 2 | `graphql/02-catalog.graphqls`, `data-model/02-catalog-media.md`, `errors.md` (Phase 2 codes) | **Frozen.** Last review change: `SizeSuggestion.confidence` is the enum `SizeConfidence` (HIGH, MEDIUM, LOW) instead of a string. Stock fields live in `03-inventory.graphqls`, ratings in `07-engagement.graphqls` | — (first freeze) | — |
| 2026-11-13 | 1 | `graphql/01-identity.graphqls`, `data-model/01-identity-access.md`, `errors.md` (Phase 1 codes) | **Frozen.** Served by the backend (`serverInfo.contractPhase` = 1). Last review changes: single-`input` mutations, `acceptStaffInvite`, per-member overrides, `archiveRole`, `locations`, `refreshSession`, code-or-link verification and reset, `INVITED` status | — (first freeze) | — |
| 2026-11-13 | 0 | `graphql/00-common.graphqls`, `errors.md`, `data-model/00-conventions.md` | **Frozen.** `contractPhase` description: "highest phase the server implements" | — (first freeze) | — |
| 2026-11-13 | all | all | Initial drafts seeded from the prototype (not frozen) | — | — |
