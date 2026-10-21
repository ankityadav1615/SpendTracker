# SpendTracker

An Android app that tracks your money automatically. It reads the transaction SMS your bank sends for every UPI, card, NEFT/IMPS, NACH/EMI and ATM transaction, works out where the money went, and shows how much is left. Cash spends, and anything your bank doesn't text you about, can be added by hand with the **+** button.

It works for everyone, not only salaried people: business owners, freelancers, students living on an allowance, and anyone with mixed income.

UPI apps and NPCI don't offer a personal transaction API, so bank SMS is the most reliable automatic source. Every bank sends an SMS for every debit and credit on a linked account, whichever UPI app you pay with.

## What it does

- **Captures transactions automatically.** New bank SMS are picked up the moment they arrive. When you turn SMS reading on, it also imports the last 180 days from your inbox.
- **Reads the details from each SMS:** amount, debit or credit, payee/payer name or UPI ID, bank, account/card ending, UPI/UTR reference and the available balance.
- **Fits how you earn.** On first launch you pick where your money comes from: salary, business, freelance work, an allowance or pocket money, or a mix. That sets how time is split:
  - **Payday (pay cycle):** from one salary credit to the next. The default for salaried people; it uses months until a salary is found.
  - **Calendar month:** for everyone else. Months can also start on another day, such as the 5th.
- **Any date range.** Tap the date at the top of Home or Activity to pick this or last pay cycle, this or last month, the last 3 months, or **any range on a calendar**. The arrows step back and forward by the same length.
- **Shows what's left.** Home shows how much is left of a **monthly budget** if you set one, otherwise of the money that came in during the period. Below that it shows where the money went, by category: EMI & Loans, Rent, Groceries, Fuel, Food & Dining, Bills & Recharges, Shopping, Travel, Health, Entertainment, Savings & Investments, Cash, Sent to people, Daily & Misc. It also shows the latest balance of each bank account.
- **Activity with search and filters.** Every transaction, grouped by day, with chips for money out, money in or a single category, and a search across payee, UPI ID, category, bank, account number and notes.
- **Add expenses and income by hand.** Tap the orange **+** in the middle of the bottom bar. Enter the amount, who you paid (optional), a category, how you paid (cash, UPI, card or other), the date and a note. Tap an added entry to change or delete it; *Undo* brings a deleted one back. Added entries are marked "Added by you" and are never changed by SMS reading. People who don't turn SMS reading on can track everything this way.
- **Reports with charts.** The **Reports** tab shows the period as a ring chart (tap a slice to see its share) or as bars over time (a bar per day, week or month, depending on the range). Switch between **Expenses** and **Income**. Each category card shows its share of the total, the number of payments, and how much it went up or down compared with the same days of the previous period. Tap a card to see its transactions.
- **Download a report as PDF or CSV.** Tap the download button on Reports, keep the dates or change them, choose all categories or just some, and pick a file type. Android then asks where to save it (Downloads, Google Drive, …). The PDF has a summary, a category table and every transaction grouped by category; the CSV opens in Excel or Google Sheets with one row per transaction.
- **Learns from your corrections.** Tap any transaction to change its category. Leave *Remember for every payment to …* on, and every past and future payment to that payee is filed the same way (for example, UPI transfers to your landlord → Rent).
- **Budgets and a savings goal.** Set a monthly budget and budgets for single categories in Settings; Home shows how much is left of each. A savings goal fills up with the money you move into *Savings & investments* after you set it.
- **Spots repeating payments and gives tips.** Payments to the same payee for a similar amount on a steady cycle (rent, EMIs, subscriptions) are detected. Home shows dismissible tips when one is due soon, when a category goes over its budget, or when a category jumps compared with the previous period.
- **Reminders and weekly summary.** Optional notifications the day before a repeating payment is due, and a weekly summary of your spending. Turn them on or off under *Settings → Reminders*.
- **Widget and shortcuts.** A home-screen widget shows this month's spending, top category and income. Long-press the app icon for *Add expense* and *Add income*.
- **Receipt photos.** Attach a photo to an entry you added, from the camera app or Android's photo picker. The app needs no camera or photo permission and keeps a smaller copy in its private storage.
- **Avoids double counting.** Credit card bill payments and transfers between your own accounts are shown but not counted as spending. Card spends are already counted when you swipe. Cash you add by hand is taken from your most recent ATM withdrawal (within 60 days), so withdrawn cash is counted once: under the categories you spent it on, with only the unaccounted rest left under *Cash withdrawal*.
- **Ignores noise:** OTPs, payment requests, "will be debited" reminders, bill-due alerts, failed transactions, offers, and SMS from personal numbers. If something slips through, open it and tap *Hide this transaction*.
- **Private by design.** All data stays in a local database on the phone. The app has no internet permission, no account, no ads and no analytics, and backups are disabled. Reports you download are saved only where you choose. You can stop SMS reading and delete all data from Settings. See the [privacy policy](PRIVACY.md).

## Install the APK

1. Get the APK in one of these ways:
   - **From CI:** open the latest successful run of the *Android* workflow in the repo's **Actions** tab and download the `SpendTracker-debug-apk` artifact (a zip containing `app-debug.apk`).
   - **Build it yourself:** see [Build from source](#build-from-source). The APK ends up at `app/build/outputs/apk/debug/app-debug.apk`.
2. Copy `app-debug.apk` to your Android phone (Android 8.0 or newer) and open it. Allow your file manager or browser to *Install unknown apps* when prompted.

   Alternatively, with USB debugging on, run `adb install app-debug.apk`.

Debug APKs, from CI or built locally, are signed with the shared `app/debug.keystore`, so either can be installed over the other. A debug APK signed with a different key, such as one built before this keystore was added, must be uninstalled first; uninstalling deletes the app's data.

## Grant SMS permission

1. Open SpendTracker, choose where your money comes from, and tap **Continue**.
2. The next screen explains what SpendTracker reads and keeps. Tap **Allow SMS access**, then allow SMS in Android's prompt. The app then imports your recent bank SMS. (Tap **Not now** to look around first; you can turn it on later from Home or **Settings → SMS access**.)
3. **Android 13 and newer:** if the app was installed from an APK file, Android may block the permission as a *Restricted setting*. To allow it:
   1. Open **Settings → Apps → SpendTracker**, or tap **Open app settings** on the *SMS access is off* card in the app.
   2. Tap the **⋮** menu (top-right) → **Allow restricted settings**, and confirm with your PIN or fingerprint.
   3. Open **Permissions → SMS → Allow**, then return to SpendTracker.

   Apps installed with `adb install` or from Google Play are not restricted.
4. Use **Settings → Scan inbox again** at any time to look for bank messages the app missed. Duplicates are detected using the bank reference number, so rescanning is safe.

To stop, use **Settings → SMS access → Stop reading**, and **Settings → Delete all data** to remove everything the app saved.

## Build from source

Requirements: Android Studio (or JDK 17+ and the Android SDK with `ANDROID_HOME` set).

```shell
# Windows (PowerShell), using Android Studio's bundled JDK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest assembleDebug

# macOS / Linux
./gradlew testDebugUnitTest assembleDebug
```

Or open the project in Android Studio and run the `app` configuration on a connected phone.

## Publishing

Releases are signed with an upload key that you create once. If no key is configured, `assembleRelease` still works but produces an unsigned `app-release-unsigned.apk`. The release build is minified with R8.

### 1. Create an upload keystore

`keytool` comes with the JDK (on Windows: `& "$env:JAVA_HOME\bin\keytool.exe" ...`):

```shell
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```

The keystore is PKCS12, so the key password is the same as the keystore password. Keep `upload.jks` and its password outside the repository and backed up. `*.jks`, `*.keystore` and `keystore.properties` are git-ignored; never commit them. The only exception is `app/debug.keystore`, a public debug key that is never used for releases. APKs on GitHub Releases are signed with this key, so if it is lost, people who installed from GitHub must uninstall before they can install a newer version. With Play App Signing, a lost upload key can be reset through Play Console.

### 2. Build a signed release locally

Set these environment variables:

```powershell
$env:SPENDTRACKER_KEYSTORE = "C:\keys\upload.jks"
$env:SPENDTRACKER_KEYSTORE_PASSWORD = "<password>"
$env:SPENDTRACKER_KEY_ALIAS = "upload"
$env:SPENDTRACKER_KEY_PASSWORD = "<password>"
.\gradlew.bat assembleRelease bundleRelease
```

Or create `keystore.properties` in the repository root. Use forward slashes in the path; a relative path is resolved from the repository root.

```properties
storeFile=C:/keys/upload.jks
storePassword=<password>
keyAlias=upload
keyPassword=<password>
```

Environment variables take precedence over the file. The signed outputs are `app/build/outputs/apk/release/app-release.apk` and `app/build/outputs/bundle/release/app-release.aab`.

### 3. Release from GitHub Actions

The *Release* workflow (`.github/workflows/release.yml`) runs the unit tests, builds a signed APK and AAB, and publishes a GitHub release with `SpendTracker-<tag>.apk` and `SpendTracker-<tag>.aab` attached, plus generated release notes. Tags containing a `-`, such as `v2.1.0-beta1`, are marked as pre-releases.

1. Add these repository secrets under **Settings → Secrets and variables → Actions**. The workflow fails with an error that lists any missing ones.

   | Secret | Value |
   | --- | --- |
   | `SPENDTRACKER_KEYSTORE_BASE64` | The keystore file, base64-encoded |
   | `SPENDTRACKER_KEYSTORE_PASSWORD` | Keystore password |
   | `SPENDTRACKER_KEY_ALIAS` | Key alias, e.g. `upload` |
   | `SPENDTRACKER_KEY_PASSWORD` | Key password |

   To set the keystore secret with the GitHub CLI:

   ```shell
   # Windows (PowerShell)
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\keys\upload.jks")) | gh secret set SPENDTRACKER_KEYSTORE_BASE64

   # macOS / Linux
   base64 < upload.jks | tr -d '\n' | gh secret set SPENDTRACKER_KEYSTORE_BASE64
   ```

2. Raise `versionCode` and `versionName` in `app/build.gradle.kts`. Google Play rejects a `versionCode` it has already seen. Commit and push the change.
3. Tag the commit and push the tag:

   ```shell
   git tag v2.0.0
   git push origin v2.0.0
   ```

To rebuild an existing tag, run the workflow from the **Actions** tab and pick the tag under *Use workflow from*. Delete the existing GitHub release first.

### Direct download (GitHub Releases)

People can install `SpendTracker-<tag>.apk` from the release page. Because the APK is sideloaded, Android 13 and newer block the SMS permission until **Allow restricted settings** is turned on; see [Grant SMS permission](#grant-sms-permission). An APK from GitHub and the Play Store version are signed with different keys when Play App Signing is used, so people must uninstall one before installing the other.

### Google Play checklist

Everything Play asks for is in [`store/`](store): the listing text and graphics in [`store/listing.md`](store/listing.md), and the account setup, every *App content* answer and the release steps in [`store/play-console.md`](store/play-console.md).

- [ ] **Developer account:** a personal account created after 13 November 2023 must run a **closed test with at least 12 testers opted in for 14 days in a row** before it can apply for production. Find the testers early.
- [ ] **Upload the AAB** (`SpendTracker-<tag>.aab`), not the APK. Keep Play App Signing on, with `upload.jks` as the upload key.
- [ ] **Store listing:** title, short and full description, icon (`store/graphics/icon-512.png`), feature graphic (`store/graphics/feature-graphic-1024x500.png`) and phone screenshots (`store/screenshots/`). Category Finance, country India.
- [ ] **Permissions Declaration Form** (Play Console → *App content → Sensitive app permissions*): declare `READ_SMS` and `RECEIVE_SMS` and choose the **SMS-based money management** exception. Play's [Use of SMS or Call Log permission groups](https://support.google.com/googleplay/android-developer/answer/10208820) policy lists it for apps that track and manage budgets. It is an exception, so approval is subject to Play's review and policy changes.
- [ ] **Prominent disclosure:** Play requires an in-app explanation of why SMS are read, shown before the Android permission prompt and needing an explicit "agree". The app's SMS explanation screen does this; keep it in place.
- [ ] **Demo video:** the declaration needs a video that shows the disclosure, the permission prompt and the core feature (a bank SMS turning into a tracked transaction). Upload it to YouTube as *Unlisted* and paste the link. The recording is kept in `store/demo/`, which is git-ignored, so it isn't committed.
- [ ] **Privacy policy URL:** <https://gist.github.com/ankityadav1615/0e29e25a7374097c857cf18d48d5ff4f> is a public copy of [PRIVACY.md](PRIVACY.md), because this repository is private. After changing `PRIVACY.md`, update it with `gh gist edit 0e29e25a7374097c857cf18d48d5ff4f PRIVACY.md`.
- [ ] **Data safety form:** answer that the app collects no data and shares no data. All data stays on the device, and the app has no internet permission. Data that never leaves the device does not count as "collected".
- [ ] Complete the other *App content* sections: ads (none), target audience (18 and over), content rating (all "No"), advertising ID (not used), and the financial features declaration (**Support services → Other**).

## How it works

| Piece | Where |
| --- | --- |
| SMS parsing: amount, type, payee, VPA, account, reference, balance; filters non-transactions | `app/src/main/java/com/spendtracker/parser/SmsParser.kt` |
| Default categories from merchant/UPI ID keywords and SMS hints (NACH, ATM, NEFT, rent…) | `parser/Categorizer.kt` |
| Remembered corrections and duplicate detection | `parser/TransactionBuilder.kt`, `data/TransactionRepository.kt` |
| Live SMS capture and inbox import | `sms/SmsReceiver.kt`, `sms/SmsInboxImporter.kt` |
| Pay cycles, months, calendar ranges and their labels | `model/Periods.kt`, `model/PeriodChoice.kt`, `model/DateRanges.kt` |
| Per-category totals, money left (budget or income), bank balances merged per account | `model/Summary.kt`, `model/MoneyLeft.kt`, `model/Accounts.kt` |
| Entries added by hand, and matching cash spends to ATM withdrawals | `model/ManualEntry.kt` |
| Report totals, change vs the previous period, chart bars | `model/Report.kt` |
| PDF and CSV downloads | `export/ReportDocument.kt`, `export/PdfReport.kt`, `export/CsvReport.kt` |
| Activity filters, search and day groups | `model/Activity.kt` |
| Settings and SMS consent | `data/SettingsStore.kt` |
| UI (Jetpack Compose + Material 3); design notes in [DESIGN.md](DESIGN.md) | `ui/` |

Tech stack: Kotlin, Jetpack Compose, Room, and coroutines/Flow. Parser, categorizer, period, summary, activity, manual entry, report and export logic are covered by JVM unit tests in `app/src/test`. The tests use real-world SMS formats from HDFC, ICICI, SBI, Axis, Kotak, IDFC FIRST and others.

If a bank SMS from your bank isn't recognised, add it as a test case in `SmsParserTest.kt` and adjust the patterns in `SmsParser.kt`.
