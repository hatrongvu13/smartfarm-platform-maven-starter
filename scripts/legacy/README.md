# scripts/legacy/

Quarantine location for scripts that are **superseded but not yet safe to delete**.

Nothing has been moved here. The repository's existing scripts were reviewed
against the current source and the prior script register
(`docs/cleanup/08-script-register.md`): every one is still referenced, current,
or an active dev aid, so none qualified for demotion. See the "Script classification"
section of `scripts/README.md`.

When a script here is confirmed obsolete (no references in README/CI/Docker/build,
functionality fully replaced, or it targets a removed endpoint), it may be deleted
in a dedicated change. Until then it stays here, runnable, for reference.
