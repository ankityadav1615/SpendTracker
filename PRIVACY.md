# SpendTracker privacy policy

**Effective date:** 20 October 2026

SpendTracker is an expense tracker for Android. It works out your spending from the SMS alerts your bank sends. This policy explains what the app reads, what it keeps, and where that data goes. In short: everything stays on your phone.

## What the app reads

- **SMS messages.** With your permission, SpendTracker reads SMS to find bank and UPI transaction alerts. It reads new messages as they arrive, and messages already in your inbox (the last 180 days the first time, then only recent ones).
- Each message is checked on your phone. Messages that are not completed bank transactions, such as personal chats, OTPs, offers, reminders and failed payments, are skipped straight away and are not stored. Messages from personal phone numbers are always ignored.

## What the app stores

For each SMS recognised as a transaction, the app saves in a local database on your phone:

- The amount, whether money went out or came in, and the date and time.
- The payee or payer name or UPI ID, the bank, the last digits of the account or card, the reference number and the available balance, if the SMS includes them.
- The category, including any category you choose yourself. If you ask it to, the app also remembers your choice for that payee.
- The sender ID and the full text of that SMS. The app uses these to show the transaction details and to avoid saving the same transaction twice.

If you hide a transaction, it is kept but marked as hidden, so it does not come back on the next scan. The app also keeps your settings, such as how your spending periods are shown and any budget you set, and notes when it last scanned your inbox.

## What the app does not do

- **No internet.** The app does not have the internet permission, so it cannot send your data anywhere.
- **No account, no analytics, no ads.** You don't sign in, and there are no tracking, crash-reporting or advertising tools in the app.
- **No sharing or selling.** Your data is never shared with or sold to anyone, including the developer.
- **No backups.** Android cloud backup and device-to-device transfer are turned off for this app, so its data is not copied off the phone.

## Your choices

- **Delete your data:** use the option to delete all data in the app's Settings, or uninstall the app. You can also clear it in Android **Settings → Apps → SpendTracker → Storage → Clear storage**. The SMS in your inbox are not changed.
- **Stop SMS reading:** in the app, open **Settings → SMS access** and choose to stop reading SMS. To also take away the permission itself, go to Android **Settings → Apps → SpendTracker → Permissions → SMS**. The app then stops reading messages.

## Children

SpendTracker is not directed at children.

## Changes

If this policy changes, the new version will be published here with a new effective date.

## Contact

Questions or concerns: open an issue at <https://github.com/ankityadav1615/SpendTracker/issues>.
