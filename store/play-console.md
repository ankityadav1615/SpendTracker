# Google Play Console: setup and answers

A step-by-step guide to publishing SpendTracker, with the answer to every *App content* question. Store listing text and graphics are in [listing.md](listing.md).

## 1. Developer account (one time)

1. Sign up at <https://play.google.com/console/signup> with the Google account that will own the app. Choose a **Personal** account unless you have a registered business with a D-U-N-S number.
2. Pay the one-time US$25 fee.
3. Complete identity verification (government ID, legal name and address). Verify the contact email and phone number. Play also asks you to sign in to the Play Console app on an Android phone to verify that you have one.
4. **Testing rule for new personal accounts:** before the app can go to production, it must run a **closed test with at least 12 testers who stay opted in for 14 days in a row**. Line up 12+ people (friends and family with Android phones and Gmail accounts) now. See [section 5](#5-releases-internal--closed--production).

## 2. Create the app

**Home → Create app**

| Field | Answer |
| --- | --- |
| App name | SpendTracker: Expense Tracker |
| Package name | `com.devcodearmy.spendtracker`. Click **Check availability**. It must match `applicationId` in `app/build.gradle.kts` and can never be changed after the app is created. (`com.spendtracker` belongs to another developer's app on Play.) |
| Default language | English (India) – en-IN |
| App or game | App |
| Free or paid | Free |
| Declarations | Tick the Developer Program Policies and US export laws boxes |

Then open **Grow users → Store presence → Main store listing** and fill it in from [listing.md](listing.md).

## 3. App signing

Play App Signing is on by default for new apps; leave it on. Google holds the **app signing key** that signs what users download. You sign each upload with your **upload key**:

| | |
| --- | --- |
| Keystore | `C:\Users\anyadav\keys\spendtracker-upload.jks` (PKCS12) |
| Alias | `upload` |
| Password | in `C:\Users\anyadav\keys\spendtracker-upload-password.txt` |
| SHA-1 | `6F:C0:27:EF:D2:82:C7:DE:5C:60:13:C8:2D:33:4A:F3:E4:47:0F:47` |
| SHA-256 | `32:0F:22:3E:68:26:17:FB:BC:92:DA:66:D8:FF:32:53:5E:66:40:B5:D4:34:59:7C:0B:D0:DF:0D:1F:75:92:BC` |
| Valid until | 8 March 2054 |

The same values are stored as GitHub Actions secrets (`SPENDTRACKER_KEYSTORE_BASE64`, `SPENDTRACKER_KEYSTORE_PASSWORD`, `SPENDTRACKER_KEY_ALIAS`, `SPENDTRACKER_KEY_PASSWORD`), so pushing a `v*` tag builds a signed `.aab` (see the README's *Publishing* section).

- **Back up** the `.jks` file and its password outside this PC, for example in a password manager and on a second drive. If the upload key is ever lost or leaked, the account owner can ask for an upload key reset in **Test and release → App integrity**.
- Apps installed from Play are signed with Google's key, while APKs from GitHub Releases are signed with the upload key. Android won't update one with the other, so a phone that switches between them must uninstall first, which deletes the app's data.
- Versions up to 3.1.0 used the app ID `com.spendtracker`. From 3.1.1 the ID is `com.devcodearmy.spendtracker`, so Android treats the new version as a different app: it installs next to the old one instead of updating it, and the old app's data doesn't carry over. Uninstall the old SpendTracker once the new one has imported your SMS, or both will keep reading them.

## 4. App content

**Policy and programs → App content** (Play shows these as tasks on the Dashboard too).

| Section | Answer |
| --- | --- |
| Privacy policy | `https://gist.github.com/ankityadav1615/0e29e25a7374097c857cf18d48d5ff4f` |
| App access | **All functionality is available without special access** (no login) |
| Ads | **No, my app does not contain ads** |
| Content rating | See [Content rating](#content-rating) |
| Target audience and content | Age groups: **18 and over** only. *Could your store listing unintentionally appeal to children?* **No** |
| News apps | **No** |
| Data safety | See [Data safety](#data-safety) |
| Government apps | **No** |
| Financial features | **Support services → Other**. It's a personal budget and expense tracker. It doesn't move money, lend, give financial advice or monitor credit. |
| Health apps | **My app does not have any health features** |
| Advertising ID | **No**. The app doesn't use the advertising ID, and the `AD_ID` permission isn't in the manifest. |
| Sensitive app permissions | SMS declaration. See [SMS permissions declaration](#sms-permissions-declaration). |

Not needed for this app: Photo and video permissions (receipts use the camera app and the Android photo picker, with no `READ_MEDIA_*` or `CAMERA` permission), foreground service types, exact alarms, location, contacts and Health Connect.

### Content rating

Start the questionnaire with your email and category **All other app types**. Answer **No** to every question:

- Violence, fear, sexuality, nudity, crude humour and bad language
- Drugs, alcohol and tobacco
- Gambling and simulated gambling
- *Do users interact or exchange content?* No
- *Does the app share the user's current physical location?* No
- *Does the app allow purchasing digital goods?* No
- *Unrestricted internet or web browser?* No

Expected rating: IARC 3+, PEGI 3, ESRB Everyone.

### Data safety

Under Play's definitions, data that is processed only on the phone is not "collected". The app has no `INTERNET` permission, and a PDF or CSV report reaches another app only when the user saves it through Android's file picker, so nothing is collected or shared.

| Question | Answer |
| --- | --- |
| Does your app collect or share any of the required user data types? | **No** |

That ends the form. The listing will show **"No data collected"** and **"No data shared with third parties"**. If the app ever gets the internet permission, analytics, crash reporting or backup, redo this section and update `PRIVACY.md`.

### SMS permissions declaration

Play asks for this the first time you upload a bundle that requests SMS permissions. You can't publish to any track, including internal testing, until it's done. Review is slower than usual and can take up to several weeks.

**Permissions requested:** `READ_SMS`, `RECEIVE_SMS`.

**Core functionality (use case):** tick **SMS-based money management** (example given by Play: "apps that track and manage budget").

**Description of how the app uses the permissions** (paste and adjust if the form asks for free text):

```text
SpendTracker is a personal expense tracker for India. Its core feature is building the user's spending history automatically from the transaction alerts that banks and UPI apps send by SMS.

RECEIVE_SMS: when a new SMS arrives, the app checks it on the phone. If it is a completed bank or UPI debit or credit, the app saves the amount, date, payee, bank and account ending to a local database. Everything else (personal messages, OTPs, offers, payment requests, reminders, failed transactions, and anything from a 10+ digit phone number) is discarded at once and never stored.

READ_SMS: when the user turns SMS reading on, the app imports existing bank alerts from the last 180 days so their history isn't empty, then reads only recent messages on later scans.

Without these permissions the app can't track spending automatically, which is the reason people install it. Users who decline can still add expenses by hand.

Before Android's permission dialog, the app shows a full-screen disclosure that explains which messages are used, that everything stays on the phone (no internet permission, no login, no ads) and how to stop reading SMS or delete all data. Users can stop SMS reading later in Settings → SMS access.

The app has no INTERNET permission. SMS content never leaves the device and is not shared with anyone, including the developer.
```

**Instructions for app review:**

```text
No login is needed. On first launch, choose any income type, then on the "Track spending automatically" screen tap "Allow SMS access" and allow the Android SMS permission.

The review device probably has no Indian bank SMS, so lists start empty. The demo video shows a bank SMS turning into a transaction. To try it yourself on an emulator, send an SMS from a short code (personal 10-digit numbers are ignored), for example in the emulator console:
sms send 56767 "Rs.450.00 debited from A/c XX1234 on 21-10-26 to VPA greenbasket@okaxis UPI Ref 612345678901. Avl Bal Rs.12,345.00"
The payment then appears on Home and in Activity.

You can also add expenses with the orange + button, set a budget in Settings, and open Reports.
```

**Video:** upload `store/demo/sms-permission-demo.mp4` to YouTube as **Unlisted** (or to Google Drive shared as *Anyone with the link*) and paste the link. The video shows:

1. The app opening and the SMS disclosure screen.
2. Tapping **Allow SMS access** and Android's permission dialog.
3. A bank SMS arriving and appearing as a transaction on Home and in Activity.
4. Settings, with *SMS access* and *Delete all data*.

Tick the confirmation boxes and save.

## 5. Releases: internal → closed → production

Build the signed bundle first: push a `v*` tag so GitHub Actions makes `SpendTracker-vX.Y.Z.aab`, or build it locally (README → *Publishing*). Every upload needs a higher `versionCode` than the last one in `app/build.gradle.kts`.

1. **Internal testing** (optional but quick): **Test and release → Testing → Internal testing → Create new release**, upload the `.aab`, add yourself as a tester, roll out and install from the opt-in link. Play shows the SMS declaration and the remaining App content tasks here.
2. **Closed testing** (required for new personal accounts): **Testing → Closed testing → Create track** (or use *Alpha*). Set countries to India, add **at least 12 testers** by email list or Google Group, upload the same `.aab` (or promote it from internal), and send for review. Once approved, share the opt-in link. Testers must accept it and **stay opted in for 14 days in a row**. Ask them to really use the app. Play asks how testing went, and you can ship fixes during the test.
3. **Apply for production:** after 14 days the Dashboard unlocks **Apply for production**. Answer the questions about your test (who tested, what feedback you got, what you changed). Google usually replies within about 7 days.
4. **Production:** **Production → Countries/regions → India**, then **Create new release**, add the `.aab` and the release notes from [listing.md](listing.md), and roll out. A staged rollout (for example 20%) lets you halt it if something goes wrong. The first review of a new app with an SMS declaration can take several days or longer.

## 6. After launch

- Keep `PRIVACY.md` and the gist in step. After editing the file, run `gh gist edit 0e29e25a7374097c857cf18d48d5ff4f PRIVACY.md`. Update the Data safety section if the app's data handling changes.
- Bump `versionCode` (and `versionName`) for every release.
- Watch **Quality → Android vitals** for crashes and ANRs, and reply to reviews.
- Play needs the target API level to be within about a year of the newest Android version. Today that's API 36, which the app already targets. Expect to bump it each year around August.
