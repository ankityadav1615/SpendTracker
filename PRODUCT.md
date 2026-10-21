# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Anyone in India with a bank account whose bank sends transaction SMS: salaried employees, self-employed and business owners, freelancers, students, and homemakers. Money arrives in different rhythms (a monthly salary, irregular client payments, pocket money, business receipts) and leaves through many small UPI payments, card swipes, EMIs, and rent. The job: open the app and see, for a chosen period, how much came in, how much went out, where it went, and what is left, without typing a single entry.

## Product Purpose

SpendTracker reads the bank and UPI transaction SMS already on the phone, turns each one into a transaction (amount, payee or VPA, bank, account, reference), sorts it into a category (EMI, rent, groceries, fuel, food, bills, and so on), and shows a period summary. Success means the user stops losing track of money between one income and the next, and can answer "where did it go?" in seconds.

## Positioning

Fully automatic from bank SMS, with no manual entry as the main flow, and nothing leaves the phone: no internet permission, no login, no cloud, no analytics. Corrections the user makes to a category are remembered for that payee. Manual entry is the fallback for what SMS can't see (cash spends, payments from accounts without SMS alerts) and the whole flow for people who choose not to share SMS.

## Operating Context

- Captured from SMS: a one-time import of the existing inbox plus live capture of every new bank SMS.
- Periods: the user chooses the window to look at (this month, last month, an income cycle, or any custom date range).
- Typical moments: checking the balance the morning after payday, mid-month before a big purchase, and at month end to see what got spent where.

## Capabilities and Constraints

- Android only (minSdk 26). Indian bank and UPI SMS formats. Indian rupees (₹) only. English UI.
- Requires the READ_SMS and RECEIVE_SMS permissions. Google Play permits them only under the "SMS-based money management" exception, which requires a Permissions Declaration Form, a privacy policy, and an in-app prominent disclosure before the permission prompt.
- Distribution: Google Play Store and direct APK download from GitHub Releases.
- Free to use.
- All data is stored on-device in a local database; the user can delete all of it from inside the app.
- Reports can be downloaded as PDF or CSV for a chosen range and categories, saved wherever the user picks with Android's file picker.
- Undecided: additional UI languages (Hindi and others), budgets per category, on-device AI categorisation.

## Brand Commitments

- Name: SpendTracker.
- Privacy promise: everything stays on the phone.

## Evidence on Hand

No real users, testimonials, ratings, download counts, or bank partnerships exist. Do not fabricate any. Sample transactions in designs and screenshots must be clearly synthetic.

## Product Principles

1. Automatic first: anything the app can read from SMS, the user should never have to type.
2. Income-agnostic: work the same for a salaried person, a freelancer with irregular income, and a student with no income at all.
3. Private by construction: no network, no account, data deletable in one step.
4. Honest numbers: show exactly what the SMS said; when the app guessed (a category, a merged account), make it easy to correct.
5. One glance answers "where did my money go?".

## Accessibility & Inclusion

Numbers must stay legible at large system font sizes; amounts never depend on color alone (debits and credits also carry a sign or label).
