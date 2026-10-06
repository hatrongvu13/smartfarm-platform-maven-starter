# Service: Finance (`services/smartfarm-finance-service`)

> Verified @ HEAD `eaaa112`. gRPC :9094. Status: **PARTIAL** (ít đường expose). ← [System Overview](../architecture/system-overview.md)

## Trách nhiệm
Ledger thật: expense/income, payable/receivable/debt, batch cost, cash flow.

## Inbound — gRPC `FarmFinanceService`
RecordExpense, ReverseExpense, RecordIncome, GetTransaction, ListTransactions, RecordPayable, RecordReceivable, SettleDebt, GetBatchCost, GetCashFlow.

→ RecordExpense/ReverseExpense là **downstream của order saga** (verified). 6 RPC còn lại (income/debt/transaction) **không có caller ngoài saga**.

## Inbound — qua gateway
Chỉ 2 GraphQL query **dev-only**: `batchCost`, `cashFlow`. **Không REST, không prod path** (GW-02).

## Database
`V1__finance_baseline.sql` (1 migration).

## Known limitations
- Không có đường gateway prod cho finance.
- 6/10 RPC không có consumer.

## TODO
- [ ] Expose finance query/report qua gateway prod (nếu cần UI).

← [Gateway Mapping §4](../architecture/gateway-mapping.md#4-endpoint-có-trong-service-nhưng-chưa-mapping-qua-gateway)
