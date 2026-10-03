# V2.3 Inventory Classification

## Initial categories

- FEED — thức ăn chăn nuôi
- MEDICATION — thuốc men
- SUPPLEMENT — thực phẩm bổ sung cho chăn nuôi

## Policy

Each storage location can define allowed categories.

Examples:

- [ ] Feed-only.
- [ ] Medication-only.
- [ ] Supplement-only.
- [ ] Mixed storage only when explicitly allowed.

## Checklist

- [ ] Product category.
- [ ] Storage policy.
- [ ] Validation on receive.
- [ ] Validation on transfer.
- [ ] Expiry tracking for medication/supplement.
- [ ] Batch/lot tracking where required.
- [ ] Audit rejected placement.

## Test

- [ ] Wrong category rejected.
- [ ] Expired product blocked where policy requires.
- [ ] Transfer respects policy.
