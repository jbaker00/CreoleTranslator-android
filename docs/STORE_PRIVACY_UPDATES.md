# Store privacy declarations — what to change and exactly where

**Why:** since 2026-09-12 the api-proxy stores every translation's input text, output
text, direction, provider and a model confidence score for 90 days (no device
identifier) so named human reviewers can check quality; users can rate a
translation 👍/👎 with an optional comment; reviewer corrections are kept
permanently in a dictionary. Both apps use the same proxy, so both store
declarations must change.

**When:** with the next release of each app — Android **v1.8 (versionCode 11)**,
iOS next build. Both consoles tie the declaration to the submission, so do this
as part of that release, not before.

**Policy URLs — unchanged, but the *content* must be pushed first:**

| App | Policy URL | Published from |
|---|---|---|
| Android | https://jbaker00.github.io/CreoleTranslator-android/privacy-policy.html | `CreoleTranslator-android` `main:/docs/privacy-policy.html` (GitHub Pages) |
| iOS | https://jbaker00.github.io/CreoleTranslator-iOS/privacy-policy | `CreoleTranslator-iOS` `main:/docs/privacy-policy.md` (GitHub Pages) |

Both files are updated and committed locally. **`git push` of `main` in each repo is
what publishes them** — do that before submitting either store form, because the
reviewers at Google/Apple open the URL. (The Android update is on the
`claude/android-app-disable-backend-0f7lo2` branch; merge to `main` to publish.)

The Play listing text (`fastlane/metadata/android/*/full_description.txt`) no longer
says "Nothing is stored on our servers" — that line is corrected in en-US, fr-FR
and fr-CA and ships with the v1.8 fastlane upload.

---

## A. Google Play Console → Data safety

Play Console → **Haitian Creole Translator** → left nav **Monitor and improve → App
content** (or **Policy → App content**) → **Data safety** card → **Manage**.

The form is a wizard. Answers below; anything not mentioned keeps its current value.

### 1. Overview
- *Does your app collect or share any of the required user data types?* → **Yes**
- *Is all of the user data collected by your app encrypted in transit?* → **Yes**
- *Do you provide a way for users to request that their data is deleted?* → **Yes**
  - It then asks for a **deletion request URL**. Use the policy URL for the app
    (section 7 / "Contact" explains emailing `privacy@creoletranslator.app`).
    If Play insists on a dedicated page, add a short "Delete my data" section
    to `docs/privacy-policy.html` and point to `…/privacy-policy.html#delete`.
- *Account creation* → **No, users can't create an account** (unchanged).

### 2. Data types — tick these

**Personal info → none.** (Translation text *could* contain personal info a user
types, but Play's category is for data the app is *designed* to collect, e.g. a
name field. Do not tick "Name"/"Email". The policy already warns users not to
submit personal info.)

**App activity**
- ☑ **Other user-generated content** — this is the translation text.
  - *Is this data collected, shared, or both?* → **Collected** only. (It is
    processed by Groq/OpenRouter to produce the translation — Play counts a
    processor acting on your behalf as *not* "sharing"; it is not sold or given to
    other parties for their own use.)
  - *Is this data processed ephemerally?* → **No** (it is retained 90 days).
  - *Is this data required for your app to function, or can users choose whether
    it's collected?* → **Required** (translation cannot work without sending the
    text; the *retention* is not optional either).
  - *Why is this user data collected?* → ☑ **App functionality**, ☑ **Analytics**
    (quality review is analytics in Play's taxonomy).
- ☑ **App interactions** — already true (Firebase Analytics events). Collected,
  ephemeral **No**, optional **No**, purpose **Analytics**. Keep as currently
  declared.

**Audio → "Voice or sound recordings": TICK IT.** As of 2026-09-12 the proxy keeps
every recording for up to 7 days and, when the translation is flagged (confidence
≤ 3 or 👎), for up to 30 days for reviewer listening.
- *Is this data collected, shared, or both?* → **Collected** (Groq/OpenAI are
  processors; tick Shared too only if you want the conservative reading).
- *Is this data processed ephemerally?* → **No**.
- *Is this data required or optional?* → **Optional** (users can type instead of
  recording).
- *Why?* → **App functionality**, **Analytics**.

**Device or other IDs**
- ☑ **Device or other IDs** — already true (AdMob advertising ID; plus the
  app-generated `x-device-id` used for proxy rate limiting). Collected **and
  Shared** (AdMob), purpose **Advertising or marketing** + **Analytics**. Keep as
  currently declared; if it is not currently ticked, tick it.

**Messages / Photos / Files / Location / Contacts / Financial / Health / Calendar /
Web browsing / Installed apps →** none.

### 3. Data usage and handling — the 👍/👎 rating and comment
No separate category: a rating is part of **Other user-generated content**. The
optional free-text comment is also user-generated content. Nothing extra to tick.

### 4. Security practices → "Independent security review" → **No** (unchanged).

Save → **Submit** (it becomes part of the next release's review).

---

## B. App Store Connect → App Privacy

App Store Connect → **My Apps → Creole Translator: Haitian AI → App Privacy**
(left sidebar) → **Edit** next to *Data Types*.

Current declaration (from `docs/APP_STORE_SUBMISSION.md`): Identifiers (Device ID —
tracking + not linked), Usage Data, Diagnostics. **Add one data type:**

- **User Content → Other User Content**
  - *Usage:* ☑ **App Functionality**, ☑ **Analytics**. (Not Product Personalization,
    not Advertising, not Third-Party Advertising.)
  - *Is the data linked to the user's identity?* → **No** — stored with no
    identifier of any kind.
  - *Do you use this data for tracking purposes?* → **No**.

Add **Audio Data** (as of 2026-09-12 recordings are retained: 7 days, or 30 when
flagged for review). Purposes: **App Functionality**, **Analytics**. *Linked to the
user's identity?* → **No**. *Used for tracking?* → **No**.
Leave Identifiers / Usage Data / Diagnostics as they are.

Also tick, in the *Data Collection Practices* preamble, that data is collected
**from this app**.

Apple's "Other User Content" tooltip covers "other user-generated content" —
translation text and free-text comments fit; a 👍/👎 rating is Usage Data, already
declared.

Publish the privacy answers → they attach to the next version you submit for
review. Update `docs/APP_STORE_SUBMISSION.md` §"App Store Privacy Nutrition Labels"
to match when you do.

---

## Judgment calls to confirm

1. **"Collected, not shared"** for translation text on Play. Groq/OpenRouter process
   it on your behalf (service providers), which Play excludes from "sharing". If
   you prefer the conservative reading, tick **Shared** too and name the purpose
   as App functionality.
2. **Audio is now declared** on both stores (retention live since 2026-09-12).
   The policies say 7 days / 30 days when flagged; the bucket lifecycle rules
   enforce exactly that.
3. **Deletion mechanism = email.** Play accepts a URL to instructions; if a
   reviewer bounces it, add a `#delete` section to the Android policy page.
4. **`privacy@creoletranslator.app`** is the contact address in the Android
   policy — make sure it actually delivers. The iOS policy lists GitHub only; it
   now says "contact us" for deletions, so consider adding the same email there.
