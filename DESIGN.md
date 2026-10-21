# Design

SpendTracker's visual system, "Warm Cards". It is built in Jetpack Compose on top of Material 3. All tokens are in
[`ui/theme/Theme.kt`](app/src/main/java/com/spendtracker/ui/theme/Theme.kt), and the shared building blocks are in
[`ui/screens/Components.kt`](app/src/main/java/com/spendtracker/ui/screens/Components.kt). Read
[PRODUCT.md](PRODUCT.md) first for who the app is for.

## Direction

The app should feel like a calm, friendly notebook about your money, not a bank dashboard:

- **Cream background and white cards.** Each topic (money left, where it went, balances, a group of settings) gets
  one white card. Cards are never nested inside other cards.
- **One headline number per screen.** On Home, this is what is left (or what was spent). It is set large in the
  rounded display face so it can be read at a glance.
- **Category colour is earthy and muted.** It appears on icon tiles and in the spend strip, never as large fills.
  Debits and credits also carry a sign (− / +), so meaning never depends on colour alone.
- **Plain words.** Write "went out", "came in" and "left", not "debit", "credit" and "net".

## Colour

Material 3 roles in the `LightColors` and `DarkColors` schemes:

| Role | Light | Dark | Used for |
|---|---|---|---|
| `primary` | `#CC4614` | `#FF9C70` | Primary buttons, selected radio, calendar selection |
| `primaryContainer` | `#FBE3D6` | `#5A2A14` | Nav-bar indicator |
| `secondaryContainer` | `#2B241F` (ink) | `#F3E9DF` | Selected filter and category chips |
| `background` / `surface` | `#FFF6EC` | `#1C1714` | Screen background, sheets, dialogs |
| `onSurface` | `#2B241F` | `#F3E9DF` | Body text, amounts |
| `onSurfaceVariant` | `#6A5D54` | `#CBBBAE` | Meta lines under titles |
| `error` | `#B3261E` | `#FFB4AB` | "Delete all data" |

Extra colours Material has no role for (`SpendTheme.colors`):

| Token | Light | Dark | Used for |
|---|---|---|---|
| `card` | `#FFFFFF` | `#28211D` | Card fill, nav bar |
| `cardBorder` | `#F3E7D8` | `#342A24` | 1dp card outline, row dividers |
| `tint` | `#F8ECDD` | `#3A2F29` | SMS bubble, subtle fills |
| `line` | `#EEDFCC` | `#41362F` | Pill and round-button outlines, nav divider |
| `actionText` | `#B53C0F` | `#FF9C70` | Text buttons ("Save", "Privacy policy") |
| `credit` | `#2F7A4B` | `#7BCB95` | Money-in amounts |
| `muted` | `#7C6F66` | `#A8998C` | Captions, secondary labels |
| `stripLeftA/B` | `#F6EADB` / `#FFF8EF` | `#3A2F29` / `#2E2621` | Hatched "left" part of the spend strip |

**Category palette** (`Category.color`). These are mid-tone, low-chroma hues; in dark mode
`SpendColors.category()` lightens each one by 35% toward white. Icon tiles use the category colour at 14% alpha
(20% in dark mode) over the card colour.

| Category | Hex | Category | Hex |
|---|---|---|---|
| EMI & loans | `#8A5F86` | Entertainment | `#6B67A8` |
| Rent | `#B9773F` | Savings & investments | `#3F7F6E` |
| Groceries | `#6E8F55` | Cash withdrawal | `#7A8B4F` |
| Fuel | `#4F7896` | Sent to people | `#7D7D7D` |
| Food & dining | `#C58A2E` | Daily spends | `#8F7D63` |
| Bills & recharges | `#8C6A4F` | Salary | `#2F7A4B` |
| Shopping | `#B5597A` | Refunds & cashback | `#5E9A6B` |
| Travel & transport | `#4D8A8A` | Money received | `#3F8C77` |
| Health | `#B5524A` | Own account transfer / Credit card bill | `#8A8A8A` / `#6F7A86` |

Body text must keep at least 4.5:1 contrast against both `card` and `background`. Check this whenever a colour changes.

## Type

There are two families, both bundled under the SIL Open Font License (see `app/src/main/assets/licenses/`). Both cover
Devanagari, ready for a future Hindi UI.

- **Baloo 2** (variable, weights 500–700) is the display face. It is used for headings, screen titles, the headline
  amount, and `AmountStyle` (17/22sp) for amounts in list rows.
- **Mukta** (400/500/600) is the body face. It is used for everything else.

| Style | Font | Size / line | Typical use |
|---|---|---|---|
| `displaySmall` | Baloo 600 | 36/42 | Amount in the transaction sheet |
| `headlineMedium` | Baloo 600 | 28/34 | Onboarding titles |
| `headlineSmall` | Baloo 600 | 24/30 | Screen titles ("Activity", "Settings") |
| `titleLarge` | Baloo 600 | 20/26 | Card titles ("Where it went") |
| `titleMedium` | Mukta 600 | 16/22 | Row titles |
| `bodyLarge` / `bodyMedium` / `bodySmall` | Mukta 400 | 16/24, 14/20, 13/18 | Copy, meta lines |
| `labelLarge` | Mukta 600 | 16/20 | Buttons |

The Home headline amount is a one-off: Baloo 700 at about 56sp, with "left" or "spent" set smaller beside it.

## Shape, spacing and elevation

- **Radii:**
  - Cards: 24dp.
  - Shapes scale: `extraSmall` 8, `small` 12, `medium` 16, `large` 24, `extraLarge` 28.
  - Icon tiles: 35% of their size.
  - Pills, chips and round buttons: fully rounded.
- **Spacing:**
  - Screen side margin: 16dp.
  - Gap between cards: 16dp.
  - Card padding: 20dp, or 16dp horizontal for cards that hold lists.
  - List rows: at least 64dp tall, with a 14dp gap after the leading tile.
- **Elevation:**
  - Light cards: a soft warm shadow (6dp, brown-tinted at 12–18% alpha) plus a 1dp `cardBorder`.
  - Dark cards: no shadow, only the border.
- **Touch targets:** at least 48dp. The date pill, round prev/next buttons and nav items are 56dp tall.

## Components

| Component | Where | Notes |
|---|---|---|
| `SpendCard` | Everywhere | The only container. Never nest one inside another. |
| `ListRow` / `TransactionRow` | Home, Activity, Settings | Tile + title over meta + right-aligned amount. Rows inside a card are separated by `RowDivider`. |
| `CategoryTile` / `Tile` | Rows, sheet header | 40dp (48dp in the sheet header). Icons come from local vector drawables (`res/drawable/ic_*.xml`). |
| `DatePill` + `RoundIconButton`s | Home, Activity | The calendar icon, the period name and dates, and a chevron. Prev/next buttons step through periods. |
| `PrimaryButton` / `OutlineButton` | Onboarding, empty states, period sheet | Full width, 56dp tall, pill-shaped. |
| Filter chips | Activity | A horizontal row. The selected chip is ink (`secondaryContainer`) with a check icon. |
| `ChoiceChip` | Add sheet, transaction sheet, download sheet | The same ink-when-selected chip, used for single or multi choice. |
| `SegmentedToggle` / `IconToggle` | Reports, add sheet | A sunken track (`#F3E6D6`) with the selected segment raised as a card. Text segments are 48dp tall; icon segments 44×36dp. |
| `OptionCard` | Download sheet | A selectable card with a tinted tile, title and subtitle; selected adds a 2dp primary border and a check. |
| `ChangeBadge` | Reports | A small pill with ↑/↓ and a percentage. For expenses, up is red and down is green; for income it is reversed. |
| Add button | Bottom bar | A 56dp primary circle with a plus, centred between Activity and Reports. |
| Spend strip | Home | One bar split by category colour; the unspent remainder is hatched with `stripLeftA/B`. |
| `EmptyNote` | Lists | A title plus one sentence that says what to do next. |

## Screens

- **Onboarding (Welcome → Disclosure).** There is a 2-step progress bar.
  - Welcome asks "Where does your money come from?" and offers five income options as selectable cards.
  - Disclosure is the Play-required prominent disclosure. It shows a real SMS example and the entry it becomes, then
    three plain promises, then "Allow SMS access" / "Not now" / "Privacy policy". The example must keep matching
    what the parser really extracts; `SmsParserTest.onboardingExampleGivesTheEntryItShows` guards this.
- **Home.** From top to bottom:
  1. The period bar.
  2. The money-left card: the headline, one sentence of context, the spend strip and a legend.
  3. "Where it went": categories sorted by amount, each showing its count and share.
  4. Bank balances: the latest balance per account, merged across mask lengths.
  5. An optional budget card.

  Tapping a category opens Activity filtered to it.
- **Activity.** It shows:
  - A title and a search toggle.
  - The period pill.
  - Filter chips.
  - Day headers with the net for that day.
  - One card of transaction rows for each day.
- **Transaction sheet** (a modal bottom sheet). It contains, in order:
  1. A header with the payee, the "Paid/Received · when" line, and the large amount.
  2. A detail card.
  3. Category chips, and a "Remember for every payment to …" checkbox.
  4. The original SMS in a bubble.
  5. "Hide this transaction".

  For an entry added by hand, the detail card shows how it was paid, "Added: By you" and the note; there is no
  remember checkbox or SMS bubble, and the sheet ends with "Edit" and a red "Delete" (with Undo in a snackbar).
- **Reports.** From top to bottom:
  1. The title with a download button.
  2. The period bar, then an Expenses / Income toggle.
  3. The chart card with a ring / bars toggle. The ring has a slice per category with small gaps; tapping a slice
     raises it, dims the rest and shows its amount and share in the centre. The bars show a day, week or month per
     bar depending on the range length; tapping one shows a tooltip.
  4. A card per category: tile, name, "42% of total · N payments", amount, the change badge against the same days
     of the previous period, and a thin share bar in the category colour. Tapping a card opens Activity filtered to it.
- **Add sheet.** An Expense / Income toggle, a large centred amount, then "Paid to", category chips (8 plus
  "More…"), "Paid with" chips for expenses with a one-line hint under Cash (taken out of ATM withdrawals) and UPI or
  Card (these usually arrive by SMS), a date and note row, and "Save expense". Errors appear only after the
  first save attempt. In lists, entries added by hand show how they were paid ("Groceries · Cash").
- **Download sheet.** The period pill, an "All" chip plus a chip per category in the period, PDF and CSV option
  cards, and "Download PDF/CSV", which opens Android's save screen. When saved, a snackbar offers "Open".
- **PDF report.** A4 pages: a header with the dates, the totals (in, out, and left or "over by"), a ring chart
  beside a category table for expenses and for income, then every transaction grouped by category, with
  "Page X of Y" in the footer.
- **Period sheet and calendar.**
  - The bottom sheet lists the period options as radio rows, plus "Pick dates on a calendar".
  - The calendar is a full-screen, edge-to-edge Material `DateRangePicker` limited to today and earlier.
- **Settings.** It has three grouped cards:
  - "Your money": income, periods, budget.
  - "Bank messages": SMS access, rescan.
  - "Privacy": policy, licences, delete all data (in red).

  Each setting opens a small dialog in the app's colours.

## System and accessibility

- **Edge to edge.** Screens draw behind the system bars:
  - A solid background-coloured scrim sits under the status bar on the main tabs.
  - Onboarding and document screens use `safeDrawingPadding`.
- **Dark theme.** It follows the system setting, and every token has a dark value.
- **Large text.** Amounts and row titles truncate with an ellipsis rather than overlap, and rows grow in height.
- **Icons.** Decorative icons have `contentDescription = null`. Icon-only buttons (search, prev/next, close) have a
  label.
