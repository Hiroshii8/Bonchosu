# Bonchosu for developers

Kotlin · Jetpack Compose · Room · DataStore · WorkManager · ML Kit text recognition · PDFBox · min SDK 26 ·
English + Bahasa Indonesia

## Build and run

1. In Android Studio: **File → Open** → pick the project folder, and let Gradle sync.
   If Studio offers to upgrade AGP/Kotlin, bump `kotlin` and `ksp` together in `gradle/libs.versions.toml`.
2. Press ▶ with a phone or emulator connected.
3. Unit tests: right-click `app/src/test` → **Run tests**, or `gradlew testDebugUnitTest`.

The code package and `applicationId` are both `com.hiroshi.bonchosu` (sources in `app/src/*/java/com/hiroshi/bonchosu`).
Builds before 0.1.0 used the ID `com.example.moneyapp`, which Android treats as a different app: data moves across
through **Backup & restore** (save a backup in the old app, restore it in the new one).

## Theming

The app is named **Bonchosu** (`app_name`).

`ui/theme/Theme.kt`: a theme is **base** (Light / Dark / AMOLED) × **accent** (Emerald, Blue, Violet, Orange), or the
wallpaper's colours when dynamic colour is on (Android 12+). Four settings drive it: `theme`, `theme_amoled`,
`theme_accent`, `theme_dynamic` (DataStore, so they're part of backups).

- Screens read only the semantic tokens in `Brand` (`Bg`, `Surface`, `Surface2`, `Text`, `Muted`, `Border`,
  `Income`, `Expense`, `Accent`, `OnAccent`, `AccentSoft`, `Hero`, `OnHero`, amber for "needs attention") or the
  matching `MaterialTheme.colorScheme` roles. Never raw hex in a screen.
- `MainActivity` collects the settings and re-themes live. Android's night mode always follows the phone; the app
  decides dark/light itself, so changes never restart the screen.
- Category colours (`CategoryPalette`) are fixed solid colours with a white letter, the same in every theme.
- Type, shapes and the font live in `ui/theme/Type.kt` (`AppFont` is the one place to change the font).
- Shared building blocks are in `ui/components/Components.kt`: `ScreenScaffold` / `ScreenTopBar`,
  `RoundIconButton`, `SectionCard`, `FilterPill`, `SegmentedChoice`, `PrimaryButton`, `TransactionItem` with its
  `SourceChip` (AUTO · BCA, SCAN, MANUAL, REPEAT, REVIEW).
- Launcher icon: `res/drawable/ic_launcher_foreground.xml` (the cat), `ic_launcher_monochrome.xml` for Android 13
  themed icons, background `@color/icon_background`.

## Layout

```
ui/screens/*        Compose screen + its ViewModel
data/Repositories   service layer, the only thing ViewModels call
data/db             Room entities + DAOs, SQL lives in Daos.kt
data/settings       DataStore settings + feature flags
capture/            notifications → parser → category guess → review inbox
statement/          e-statement reading (PDF text or OCR) and parsing
bill/               receipt parsing, receipt scanning, split bill
core/               pure Kotlin logic (money, periods, budgets, recurrence, bill split)
backup/             backup file format and restore
AppContainer        manual dependency injection
```

- Money is whole rupiah in `Long`, never floats.
- Balances are computed in SQL from the starting balance plus confirmed transactions, so they can't drift.
- Anything read automatically is saved as `PENDING_REVIEW` and doesn't touch balances or budgets until confirmed.

## Category guessing

`capture/Categorizer.kt`, used through `TransactionRepository.categoryHints()` by capture, statements and receipts.
First match wins:

1. **Learned**: a merchant you confirmed or fixed before (`merchant_rules`, written on confirm).
2. **Your keyword rules** (`keyword_rules`, Settings → Manage → Keyword rules). Whole words, matched against the
   merchant and the raw text, only for the category's side (expense/income). The longest matching word wins.
   Adding a rule also fills in review items that still have no category.
3. **Built-in word lists** for the default categories.

## Auto-capture

Off by default (`AppSettings.autoCaptureEnabled`). With the flag off, the listener component is disabled in the
manifest, so Android never starts it and the app isn't even listed under Notification access.

Only apps picked in **Apps to capture from** are read; with none picked, nothing is. `ParserRegistry` only adds
suggestions for the picker, default wallet types, and dedicated parsers (Superbank). Installs from before the picker
get the known banking apps they have picked once (`InstalledApps.pickKnownAppsOnce`).

Package names and wording are best guesses. To check them against real notifications:

1. Set `DEBUG_CAPTURE = true` in `TransactionListenerService`, run a debug build, make a few payments.
2. In Logcat filter `NotifCapture`, copy the texts, add them to `NotificationParserTest`, tune until green.
3. Set `DEBUG_CAPTURE` back to `false` (it logs every app's notifications).

## E-statements and receipts

Both use `StatementImportScreen` (receipt mode via `Routes.RECEIPT_SCAN`) and `StatementImporter`, which checks
for duplicates and saves rows as `PENDING_REVIEW`.

- `statement/StatementReader`: PDF text layer (PDFBox), or on-device OCR (ML Kit) for screenshots and scans.
- `statement/StatementParser`: lines → rows with no per-bank templates. Opening + rows = closing is checked when
  both balances are printed.
- `bill/ReceiptScan`: one photo → one expense. Needs a printed total or at least two priced items; uses the
  receipt's date, else the photo's date. Repeats within a batch are unticked.

## Split bill

- `core/BillSplit`: tax, service and discounts by order size; delivery and packing equally; whole rupiah that add up.
- `bill/ReceiptParser`: OCR lines → orders and fees, checked against the printed total.
- `data/BillRepository`: nothing is written until you confirm.

## Backup

`backup/BackupManager` writes a zip with `manifest.txt`, `settings.txt` and `money.db` through Android's file picker.
Restoring stages the file, restarts the app and swaps the database before Room opens it; older backups are migrated
by Room as usual. Everything in the database, keyword rules included, is part of the backup.

## Database changes

Schemas are exported to `app/schemas`. Every schema change needs a migration, because there's deliberately no
destructive fallback (version 4 added `keyword_rules`). Run `MigrationTest` without `connectedAndroidTest`, which
uninstalls the app and wipes its data:

```
gradlew :app:installDebug :app:installDebugAndroidTest
adb shell am instrument -w -e class com.hiroshi.bonchosu.data.db.MigrationTest com.hiroshi.bonchosu.test/androidx.test.runner.AndroidJUnitRunner
```

## README screenshots

`docs/screenshots/*.png` are taken on the emulator (light Emerald, except `appearance.png` in AMOLED Violet) and
resized to 540×1200.
