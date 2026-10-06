# scripts/legacy/

Quarantine location for scripts that are **superseded but not yet safe to delete**.

## Archived 2026-10-07 (superseded by the structured suite + Bruno collection)

These one-off manual `curl` scripts predate the structured test suite
(`scripts/{health,smoke,rest,graphql}` driven by `scripts/run-all.sh`) and the
`bruno/` API collection. They are referenced only by historical audit docs
(`docs/cleanup/*`), never by CI, Docker, the build, or the active suite. Kept here,
runnable, for reference until confirmed deletable in a dedicated change.

- `phase2-curl-test.sh` — early phase-2 REST walkthrough → `scripts/rest/*` + Bruno `03-05`.
- `phase3-saga-curl-test.sh` — order-saga curl flow → Bruno `04 - order/*`.
- `livestock-registry-test.sh` / `livestock-lifecycle-test.sh` → Bruno `05 - livestock/*`.
- `curl-06-cancel-completed.sh` / `curl-07-multiline.sh` — ad-hoc order curls → Bruno `04 - order/*`.

CI (`.github/workflows/build-push.yml`) does not invoke `scripts/`, so archiving these
changes no automated behaviour.

When a script here is confirmed obsolete (no references in README/CI/Docker/build,
functionality fully replaced, or it targets a removed endpoint), it may be deleted
in a dedicated change. Until then it stays here, runnable, for reference.
