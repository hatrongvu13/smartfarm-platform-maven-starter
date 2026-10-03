# V2.2 QR Location

## Human code

Example:

A 00 02 04 10

## QR principle

QR should resolve to a stable location identifier or a controlled location code.

QR is NOT:
- inventory primary key
- stock transaction identity
- 3D identity

## Checklist

- [ ] QR parser.
- [ ] Format validation.
- [ ] Location resolver.
- [ ] Unknown QR handling.
- [ ] Duplicate QR detection.
- [ ] Reassignment workflow.
- [ ] Import from photographed labels.
- [ ] Preview before commit.
- [ ] Audit imported mapping.

## Test

- [ ] Invalid format.
- [ ] Wrong rack.
- [ ] Unknown location.
- [ ] Duplicate location.
- [ ] Re-import same QR idempotently.
