# SpendTracker

Automatic Android expense tracker. It reads the transaction SMS that your bank sends for every UPI, card, NEFT/IMPS, NACH/EMI and ATM transaction, works out where the money went, and shows how much of your salary is left. There is no manual entry.

UPI apps and NPCI don't offer a personal transaction API, so bank SMS is the most reliable automatic source. Every bank sends an SMS for every debit and credit on a linked account, whichever UPI app you pay with.

## What it does

- **Captures transactions automatically.** New bank SMS are picked up the moment they arrive. On first launch it also imports the last 180 days from your inbox.
- **Reads the details from each SMS:** amount, debit or credit, payee/payer name or UPI ID, bank, account/card ending, UPI/UTR reference and the available balance.
- **Splits time into salary cycles.** Each salary credit starts a new cycle, and the dashboard shows:
  - Salary and other income for the cycle.
  - Total spent and what is **left to spend**.
  - A breakdown by category: EMI & Loans, Rent, Groceries, Fuel, Food & Dining, Bills & Recharges, Shopping, Travel, Health, Entertainment, Savings & Investments, Cash, Sent to people, Daily & Misc.
  - If no salary credit is found yet, it falls back to calendar months.
- **Learns from your corrections.** Tap any transaction to change its category. Leave *Remember for all payments to …* on, and every past and future payment to that payee is filed the same way (for example, UPI transfers to your landlord → Rent).
- **Avoids double counting.** Credit card bill payments and transfers between your own accounts are shown but not counted as spending. Card spends are already counted when you swipe.
- **Ignores noise:** OTPs, payment requests, "will be debited" reminders, bill-due alerts, failed transactions, offers, and SMS from personal numbers. If something slips through, open it and tap *Hide it*.
- **Private by design.** All data stays in a local database on the phone. The app has no internet permission and backups are disabled.

## Install the APK

1. Get the APK in one of these ways:
   - **From CI:** open the latest successful run of the *Android* workflow in the repo's **Actions** tab and download the `SpendTracker-debug-apk` artifact (a zip containing `app-debug.apk`).
   - **Build it yourself:** see [Build from source](#build-from-source). The APK ends up at `app/build/outputs/apk/debug/app-debug.apk`.
2. Copy `app-debug.apk` to your Android phone (Android 8.0 or newer) and open it. Allow your file manager or browser to *Install unknown apps* when prompted.

   Alternatively, with USB debugging on, run `adb install app-debug.apk`.

## Grant SMS permission

1. Open SpendTracker. On the **Allow SMS access** card, tap **Allow** and accept the SMS permission. The app then imports your recent bank SMS.
2. **Android 13 and newer:** the permission may show as *Restricted setting*, because the app was sideloaded. To allow it:
   1. Open **Settings → Apps → SpendTracker**, or tap **Open app settings** on the card.
   2. Tap the **⋮** menu (top-right) → **Allow restricted settings**, and confirm with your PIN or fingerprint.
   3. Open **Permissions → SMS → Allow**, then return to SpendTracker.

   Apps installed with `adb install` are not restricted.
3. Use the refresh button on the dashboard at any time to rescan the inbox. Duplicates are detected using the bank reference number, so rescanning is safe.

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

## How it works

| Piece | Where |
| --- | --- |
| SMS parsing: amount, type, payee, VPA, account, reference, balance; filters non-transactions | `app/src/main/java/com/spendtracker/parser/SmsParser.kt` |
| Default categories from merchant/UPI ID keywords and SMS hints (NACH, ATM, NEFT, rent…) | `parser/Categorizer.kt` |
| Remembered corrections and duplicate detection | `parser/TransactionBuilder.kt`, `data/TransactionRepository.kt` |
| Live SMS capture and inbox import | `sms/SmsReceiver.kt`, `sms/SmsInboxImporter.kt` |
| Salary cycles and per-category totals | `model/Periods.kt`, `model/Summary.kt` |
| UI (Jetpack Compose + Material 3) | `ui/` |

Tech stack: Kotlin, Jetpack Compose, Room, and coroutines/Flow. Parser, categorizer and summary logic are covered by JVM unit tests in `app/src/test`. The tests use real-world SMS formats from HDFC, ICICI, SBI, Axis, Kotak, IDFC FIRST and others.

If a bank SMS from your bank isn't recognised, add it as a test case in `SmsParserTest.kt` and adjust the patterns in `SmsParser.kt`.
