Billing Summary API - Payment fields

/GET /billings/{billId}/summary
- Adds `payment` object to response with fields:
  - `mode`: string enum ["CASH","WALLET","MIXED"]
  - `amountPaid`: number (total paid)
  - `cashPaid`: number
  - `walletUsed`: number
  - `paymentDetails`: object (optional raw payment object stored)

Example response:
{
  "billId": "BILL_123",
  "status": "COMPLETED",
  "createdAt": "2026-06-16T12:34:56",
  "createdBy": "user1",
  "discount": 0,
  "subTotal": 100.0,
  "taxAmount": 5.0,
  "totalAmount": 105.0,
  "items": [...],
  "payment": {
    "mode": "MIXED",
    "amountPaid": 105.0,
    "cashPaid": 50.0,
    "walletUsed": 55.0,
    "paymentDetails": { /* raw payment object saved */ }
  }
}

/POST /billings/{billId}/finalize
- Accepts a `payment` object in request body. Recommended shape:
{
  "mode": "CASH" | "WALLET" | "MIXED",
  "amountPaid": 105.0,
  "cashPaid": 105.0,
  "walletUsed": 0.0,
  "paymentDetails": { /* frontend raw object */ }
}

Behavior:
- `finalize` should persist `payment_mode`, `cash_paid`, `wallet_used`, and full `paymentDetails` JSON into `billing` table.
- `getSummary` should return `payment` object populated from persisted fields if available.

Migration:
- Add `payment_mode` (VARCHAR), `cash_paid` (NUMERIC), `wallet_used` (NUMERIC), `payment_details` (JSONB) to `billing` table.
- Provide MySQL variant note.
