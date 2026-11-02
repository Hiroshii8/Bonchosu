# MoneyApp

A money manager for Android (Money Lover-style) with an optional **auto-capture** feature that logs
bank and e-wallet payments from their notifications. Auto-capture sits behind a feature flag in
**Settings → Labs** and is **off by default**.

Kotlin · Jetpack Compose · Room · DataStore · WorkManager · min SDK 26 · English + Bahasa Indonesia

## Run it

1. Unzip, then in Android Studio: **File → Open** → pick the `MoneyApp` folder.
2. Let Gradle sync finish (first sync downloads a lot). If Studio offers to upgrade AGP/Kotlin, you can accept,
   but bump `kotlin` and `ksp` together in `gradle/libs.versions.toml` (the KSP version starts with the Kotlin version).
3. Press ▶ with your phone connected.
4. Unit tests: right-click `app/src/test` → **Run tests** (no phone needed).

The package is `com.example.moneyapp`. To rename: change `namespace`/`applicationId` in `app/build.gradle.kts`,
then right-click the `com.example.moneyapp` package → **Refactor → Rename**.

## What's in v1

| Area | Features |
|---|---|
| Wallets | Cash / bank / e-wallet / credit card, starting balance (can be negative), exclude from total, archive, live balances |
| Transactions | Expense / income / transfer, category, date, note, exclude from reports, search, filter by wallet/type/month |
| Categories | 18 built-in (translated), add your own, pick a colour, built-ins are hidden instead of deleted |
| Budgets | Per category or all expenses, per wallet or all, weekly/monthly, "safe to spend per day", near/over warnings |
| Recurring | Daily/weekly/monthly/yearly every N, start/end date, pause; missed runs are caught up; "Repeat" on a new transaction |
| Reports | Week/month/year, income vs expense, category donut, 6-month trend |
| Settings | Language switch (system / English / Indonesian), hide balances, feature flags |
| **Auto-capture (flag)** | Notification listener → parser per app → de-dup → category guess → review inbox (or auto-save when confident) |

## How the feature flag works

`AppSettings.autoCaptureEnabled` (DataStore) is the flag. Turning it on or off does three things:

1. **The listener component itself is enabled/disabled** (`CaptureController.applyFlag`). The service is declared
   `android:enabled="false"` in the manifest, so with the flag off Android never starts it and the app doesn't even
   appear in the system "Notification access" list.
2. `CaptureProcessor.handle` checks the flag again before reading anything (belt and braces).
3. The UI hides everything capture-related: the review banner on Home, the capture source on wallets,
   and the capture settings.

Turning it on sends you to a setup screen that explains what's read and opens Android's permission page.

## Architecture (backend terms)

```
ui/screens/*        Compose screen + its ViewModel (controller + view model)
data/Repositories   service layer, the only thing ViewModels call
data/db             Room entities + DAOs (JPA-like), SQL lives in Daos.kt
data/settings       DataStore key-value settings + feature flags
capture/            notification consumer → parser (strategy per app) → processor
core/               pure Kotlin logic (money, periods, budgets, recurrence), unit-tested
AppContainer        manual dependency injection (one place that wires everything)
```

- Money is stored as whole rupiah in `Long`. Never floats.
- Balances are computed in SQL from the starting balance + confirmed transactions, so they can't drift.
- Captured transactions are saved as `PENDING_REVIEW` and don't touch balances or budgets until confirmed.
- When you confirm or fix a capture, the merchant → category choice is learned (`merchant_rules` table).

## Before you rely on auto-capture

The parser wording and the bank **package names are best guesses** (`capture/NotificationParser.kt`):

1. Set `DEBUG_CAPTURE = true` in `TransactionListenerService`, run a debug build, make a few real payments.
2. In Logcat filter `NotifCapture` and copy the real notification texts and package names.
3. Fix `ParserRegistry` package names, add the texts to `NotificationParserTest` as fixtures, tune until green.
4. Set `DEBUG_CAPTURE` back to `false` (it logs every app's notifications).

## E-statement import

Settings → Manage → Import e-statement. Pick a PDF or screenshots; everything runs on the device.

```
statement/StatementReader    PDF text layer (PDFBox), or on-device OCR (ML Kit) for screenshots and scans
statement/StatementParser    text lines → rows, without knowing the bank (pure Kotlin, unit-tested)
statement/StatementImporter  duplicate check + save as PENDING_REVIEW, source IMPORT
```

- There are no per-bank templates: a row is a line starting with a date and carrying an amount.
- Direction comes from running balance arithmetic, then DB/CR markers, +/− signs, debit/credit columns, then wording.
- When the statement prints both, opening + rows = closing is checked and shown on the preview.
- The samples in `StatementParserTest` are made up. Add real ones, with names and account numbers removed.

## Split bill

Home → Split bill. Scan or type a receipt, set how many people, assign each order, then tick the orders that are yours.

- `core/BillSplit`: tax, service and discounts by order size, delivery and packing equally; whole rupiah that always add up.
- `bill/ReceiptParser`: OCR lines → orders and fees, checked against the printed total (pure Kotlin, unit-tested).
- `data/BillRepository`: nothing is written until you confirm. If you paid, your orders are a normal expense and the
  rest is an expense hidden from reports until repaid. If someone else paid, nothing moves until you pay them back.

## Database changes

Schemas are exported to `app/schemas`. Every schema change needs a migration, because there's deliberately no
destructive fallback. `MigrationTest` uses its own database file. Run it without `connectedAndroidTest`,
which uninstalls the app and wipes its data afterwards:

```
gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w com.example.moneyapp.test/androidx.test.runner.AndroidJUnitRunner
```

## Next (v2 ideas)

Savings goals, debts/loans, bill reminders, CSV export and backup,
dark theme, multi-currency.
