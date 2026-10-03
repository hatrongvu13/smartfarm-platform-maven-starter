# V2.4 Warehouse Vertical Slice

## Flow

Receive
→ assign StorageLocation
→ scan QR
→ put-away
→ stock ledger

Pick task
→ resolve StorageLocation
→ worker scans location
→ issue stock
→ complete task

## Checklist

- [ ] Receive.
- [ ] Put-away.
- [ ] Move.
- [ ] Pick.
- [ ] Count.
- [ ] Adjustment.
- [ ] Location validation.
- [ ] Worker confirmation.
- [ ] Manager exception review.

## Test

- [ ] Concurrent picking.
- [ ] Wrong location.
- [ ] Empty location.
- [ ] Out of stock.
- [ ] Location blocked.
- [ ] Duplicate scan.
