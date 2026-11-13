# Phase 6 — Finance (status: draft)

Module: `finance`. Double-entry, append-only, integer minor units.

## ledger_account (chart of accounts)
`organization_id`, `code` unique per org, `name`, `type` (`ASSET`,`LIABILITY`,`EQUITY`,`REVENUE`,`EXPENSE`), `active`. Seed: Cash, Bank, Mobile money, Inventory, Accounts payable, Sales revenue, Shipping revenue, Discounts, COGS, Refunds, expense accounts per category.

## journal_entry / journal_line (append-only)
- `journal_entry`: `organization_id`, `entry_date`, `description`, `source_type` (`ORDER`,`REFUND`,`EXPENSE`,`INCOME`,`PURCHASE`,`ADJUSTMENT`,`REVERSAL`), `source_id`, `reverses_entry_id` (-), `idempotency_key` unique, `posted_by` (-), `posted_at`.
- `journal_line`: `journal_entry_id`, `ledger_account_id`, `debit_minor` ≥ 0, `credit_minor` ≥ 0 (exactly one > 0), `currency`, `memo` (-). Invariant: per entry Σdebit = Σcredit (deferred constraint trigger + test).

## expense_category (lookup)
`name` (Fabric, Rent, Ads, Packaging, Salaries, Shipping, Software, …), `ledger_account_id`, `active`.

## expense
`expense_date` (Y), `category_id` (Y), `amount_minor` > 0 (Y), `currency` (Y), `description` (-; prototype `note`), `payee` (-), `supplier_id` (-), `payment_method` (`CASH`,`BANK`,`MOBILE_MONEY`,`CARD`) (Y), `reference` (-, invoice/receipt number), `receipt_media_id` (-), `tax_minor` (-), `location_id` (-), `recorded_by` (Y). Corrections: reversal + new expense.

## other_income
`income_date`, `source` (text), `amount_minor`, `currency`, `method`, `note` (-), `recorded_by`.

## supplier
`name` (Y), `contact_name` (-), `email` (-), `phone_e164` (-), `address` (-), `payment_terms` (-), `notes` (-), `active`.

## stock_purchase / stock_purchase_line
- `stock_purchase`: `supplier_id` (-), `purchase_date` (Y), `location_id` (Y), `reference` (-), `status` (`DRAFT`,`RECEIVED`,`CANCELLED`), `payment_method` (Y), `note` (-), `total_minor` (Y), `received_at` (-), `idempotency_key` unique.
- `stock_purchase_line`: `variant_id`, `quantity` > 0, `unit_cost_minor` > 0.
Receiving a purchase creates stock movements (`PURCHASE_RECEIPT`) and one journal entry **in the same transaction** (via the inventory and finance public interfaces).
