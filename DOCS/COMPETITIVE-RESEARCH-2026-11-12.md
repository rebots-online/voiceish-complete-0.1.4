# Competitive research — SMS-from-any-screen, find-my-phone, family walkie (2026-11-12)

Method: deep-research workflow (5 search angles, 21 sources fetched, 25 claims checked by three independent votes; 24 confirmed, 1 refuted). Confidence and sources are listed with each finding.

## Summary

Almost every product in this category bills as a subscription with a freemium cap: a free tier with a hard usage limit, and a paid tier that removes it. Verified examples: MightyText gives free users 15 sends a month and Pro users unlimited. AirDroid Personal allows 200MB of remote data a month free, or $3.99/mo / $29.99/yr for Premium. Pushbullet capped free users at 100 messages a month when Pro launched at $4.99/mo / $39.99/yr in 2015. Gateway-style products (httpSMS, textbee) instead charge by message volume, from $10–$14.99/mo, or $8.33/mo billed yearly. None of them charges per message for SMS, because none runs its own SMS gateway. The user's own phone sends every message through its SIM, so the carrier bills it under the user's plan. Sending works like this: the client calls a cloud API, the API stores the message, a push service (Firebase/FCM) wakes the phone, the phone sends through Android's SMS API and reports sent, delivered or failed. Receiving runs the other way: the phone uploads the incoming SMS to the cloud, which forwards it to the web, desktop or extension clients (or a webhook) and saves it to history. The verified weak spots worth targeting are: RCS messages that third-party apps can't read; Microsoft Phone Link's limits on iPhone (no full history, no group chats) and its RCS support only on Samsung; free tiers that get capped or paywalled, which angers users; and Intel Unison's shutdown, which removes a free incumbent. A paid-only entrant at about $4.99 Pro / $7.99 Family would sit at Pushbullet's historical Pro price and above AirDroid's $2.50/mo annual rate. It needs end-to-end encryption, family walkie-talkie and reliable RCS handling to justify that price, since basic SMS mirroring alone is available elsewhere for less.

## Findings

### 1. (high confidence, vote 3-0 (x3))

Billing and message flow for phone-relay products (MightyText-style): the user's Android phone sends texts through its own SIM, so the carrier bills each SMS under the user's plan exactly as if it were sent from the phone. The vendor charges no per-message fee and makes money from a subscription that removes a free-tier cap: MightyText allows 15 free sends a month from its web or desktop apps, and Pro is unlimited.

*Evidence:* Merges claims 2, 3 and 4 (all 3-0). The vendor help page (modified May 2024) says: 'Non-Pro users can send 15 messages from MightyText each month', 'MightyText Pro users can send unlimited messages', and that the user is charged by the mobile provider 'the same as if you sent the SMS directly from the phone'. An older indexed copy said 25 a month; the live page says 15. A third-party site lists Pro at about $7.49/mo, but that price was not verified.

*Sources:* https://help.mightytext.com/support/solutions/articles/73000510542-how-much-does-it-cost-to-send-messages-using-mightytext, https://help.mightytext.net/knowledgebase/articles/148001-how-much-does-it-cost-to-send-messages-using-might

### 2. (high confidence, vote 3-0 (x3))

How sending and receiving work, step by step (from open-source gateway documentation that matches this product's architecture). Send: the client calls the REST API, the API stores the message and returns 202, a Firebase Cloud Messaging push wakes the phone, the phone fetches the message, sends it through Android's SMS API on its SIM, and reports sent, delivered or failed back to the API. Receive: the phone app uploads the incoming SMS to the API, which fires a webhook (MESSAGE_RECEIVED) or callback and saves the message to history.

*Evidence:* Merges claims 14, 15 and 18 (all 3-0). textbee docs: 'Your code calls the API with an API key. The API stores the message and pushes it to the phone, which hands it to the carrier through its SIM.' httpSMS README sequence: /v1/messages/send -> 202 Accepted -> push notification -> Fetch message -> Send using Android SMS API -> send result and delivery report. Both rely on Firebase FCM for wake-up, even when self-hosted. textbee is AGPL-3.0 and self-hostable (API, dashboard and app).

*Sources:* https://textbee.dev/docs, https://github.com/vernu/textbee, https://httpsms.com/, https://github.com/NdoleStudio/httpsms

### 3. (high confidence, vote 3-0 (x2))

SMS gateway competitors bill by usage volume, not per seat. httpSMS: free up to 200 SMS a month sent or received. Pro $10/mo or $100/yr for 5,000 a month. Higher tiers $20, $35, $89, $175 and $350 a month for 10K, 20K, 50K, 100K and 200K messages, plus a hidden $1,000 lifetime plan. textbee: free tier with no card required; Pro $14.99/mo, or $8.33/mo billed $99.99 a year (advertised as 44% off, '5 months free'). textbee offers no trial, only a 7-day money-back guarantee.

*Evidence:* Claims 16 and 17 (3-0). httpSMS prices were read from its live Nuxt JS bundles (pricing and billing chunks agree). textbee's pricing page confirms $99.99 a year (the claim's $99.96 was 8.33 x 12) and the $14.99 monthly price.

*Sources:* https://httpsms.com/, https://textbee.dev/docs, https://textbee.dev/pricing

### 4. (high confidence, vote 3-0 (x2))

AirDroid Personal bills Premium at $3.99/mo or $29.99/yr (about $2.50/mo, marked 'Best Value'), with no multi-year plan. The free tier caps remote-network data at 200MB a month and files at 30MB each. Premium gives unlimited remote data and files up to 1GB. Local-network transfer is unlimited on both tiers.

*Evidence:* Merges claims 0 and 1 (3-0). Both A/B pricing pages agree. The help center confirms the 200MB quota resets monthly. A 10% promo code was shown, and it was unclear whether listed prices already include it.

*Sources:* https://www.airdroid.com/pricing/airdroid-personal/, https://www.airdroid.com/pricing/airdroid-personal-a/, https://www.airdroid.com/personal/airdroid-personal-premium/

### 5. (high confidence, vote 3-0 (x3))

Pushbullet's pricing history shows the risk of capping a free tier after users rely on it. Pro launched in November 2015 at $4.99/mo or $39.99/yr. Free users were capped at 100 messages a month (including SMS from a PC) and 25MB files, against unlimited messages and 1GB files on Pro. Features that had been free (mirrored notification actions, universal copy and paste) moved behind the paywall, and Pro added no new features, only higher limits. The coverage was openly critical ('too expensive', 'bad news for free users').

*Evidence:* Merges claims 19, 20 and 21 (3-0). Several outlets confirm $4.99 / $39.99 with billing starting Dec 1, 2015. The 'per month' detail comes from TNW. The current price could not be checked because pushbullet.com/pro returned an error. A 2026 third-party listing still shows about 100 messages a month free. Note: Pushbullet's historical Pro price equals the planned $4.99 Pro price.

*Sources:* https://9to5google.com/2015/11/17/pushbullet-pro-tier-too-expensive/, https://thenextweb.com/news/pushbullet-launches-a-5-pro-tier-but-thats-bad-news-for-free-users, https://blog.pushbullet.com/2015/11/17/introducing-pushbullet-pro/

### 6. (high confidence, vote 3-0 (x2))

RCS is a structural blind spot for third-party SMS-mirroring apps. Default messaging apps switch to RCS when both sides support it, and RCS messages are not stored in Android's public SMS/MMS database, which has no public third-party API. Pushbullet admits RCS messages are 'sometimes not there', leaving gaps in threads, and its only workaround is to turn RCS off.

*Evidence:* Merges claims 10 and 11 (3-0). The vendor says this itself, and SyncTech independently confirms the mechanism. The source says 'sometimes', not 'often'. RCS problems were still current in 2026 (an Android Authority report of an October 2026 RCS bug). This affects any new entrant that reads the SMS store, including the proposed product.

*Sources:* https://help.pushbullet.com/articles/why-do-text-messages-from-some-contacts-not-show-in-pushbullet/, https://synctech.com.au/?p=2295

### 7. (high confidence, vote 3-0 / 3-0 / 3-0 / 2-1)

Microsoft Phone Link, the free built-in default on Windows, has verified gaps. On iPhone it shows only messages sent and received through Phone Link while the devices are paired and connected over Bluetooth, so messages sent from the iPhone itself never appear and there is no full history. Group messages are not supported on iOS: they may show up in the individual sender's thread, and replies go only to that person. On Android, RCS works only on select Samsung devices and carriers with Samsung Messages as the default app, has no typing indicators or read receipts, and image downloads are capped at 2MB.

*Evidence:* Merges claims 5, 7 (iPhone history), 8 (iOS group) and 6, 9 (RCS; 3-0 and 2-1). The Microsoft doc is footer-dated 2026, and 2026 Q&A and press confirm the limits still apply. Samsung Messages reached end of service in July 2026 (US) and cannot be installed on the S26 and later, so Phone Link RCS is probably gone in practice. A broader claim that Phone Link lacks group messaging on every platform was refuted 0-3, so the group limit is iOS-only.

*Sources:* https://support.microsoft.com/help/4539634, https://support.microsoft.com/en-us/help/4539634/troubleshooting-for-messages-in-the-your-phone-app

### 8. (high confidence, vote 3-0 (x2))

Intel Unison, a free phone-to-PC app for SMS, calls and files, was discontinued on June 30, 2025 (Lenovo Aura devices kept it through the end of 2025). Windows Central points its users to Phone Link as the default, to KDE Connect as a cross-platform option (Linux and Mac), and to LocalSend for file transfer only. This leaves an opening on macOS, Linux and ChromeOS, and with iPhone users.

*Evidence:* Merges claims 22 and 23 (3-0). Tom's Hardware, Neowin, Notebookcheck and Dell's OEM notice all confirm the shutdown. The research question's 'Samsung/Intel Unison' is a mislabel: it was Intel's product, built on technology from Screenovate.

*Sources:* https://www.windowscentral.com/software-apps/intel-unison-for-windows-11-shuts-down-encourages-users-to-switch-to-another-multi-device-experience, https://9to5google.com (2025-04-03 Unison coverage), Dell KB 000337603

### 9. (high confidence, vote 3-0 (x2))

The free OS-level 'ring my phone' baseline: Google Find Hub (renamed from Find My Device in 2025) rings a device at full volume for five minutes even on silent, and can remotely lock it with a custom lock-screen message (Mark as Lost) or erase it. A paid ring feature therefore needs something Find Hub lacks, such as one-click ringing from a Chrome extension or PWA, family-member-triggered ringing, or integration with messaging.

*Evidence:* Merges claims 12 and 13 (3-0). Google's own article (Dec 16, 2025), backed by Verizon support docs. The phone must be powered on, reachable and signed in with Find Hub enabled.

*Sources:* https://www.android.com/articles/what-is-find-hub-and-how-to-use-it/

### 10. (medium confidence, vote derived)

Price positioning (synthesis, inferred from the verified prices above): $4.99/mo Pro is above AirDroid ($3.99/mo, $2.50/mo annual) and equal to Pushbullet's 2015 Pro, below textbee monthly ($14.99) and httpSMS Pro ($10). Strong free incumbents (Phone Link, Google Messages for web, KDE Connect, Find Hub) set the expectation that basic mirroring is free. Without a free tier, the product has to win on what verified sources show competitors lack: end-to-end encryption, real RCS handling, full history and group chats across platforms (including iPhone, Mac, Linux and ChromeOS via PWA/extension), and family features such as walk-talkie and remote ring. A trial and an annual discount are standard (AirDroid about 37% off, textbee 44% off).

*Evidence:* Synthesized from the verified findings above. It is an inference, not a single sourced claim. The 37% figure is computed from $29.99 vs 12 x $3.99 = $47.88.

*Sources:* https://www.airdroid.com/pricing/airdroid-personal/, https://textbee.dev/pricing, https://httpsms.com/, https://9to5google.com/2015/11/17/pushbullet-pro-tier-too-expensive/, https://support.microsoft.com/help/4539634

## Refuted

- Microsoft Phone Link does not support sending group messages; group messages may appear in one-to-one threads, and replies go to the individual sender instead of the group. (vote 0-3, https://support.microsoft.com/help/4539634)

## Caveats

Coverage is incomplete. Of the competitors named, only MightyText, AirDroid, Pushbullet (2015 history), Microsoft Phone Link, Intel Unison, Google Find Hub, textbee and httpSMS have verified claims. Nothing survived verification for Join (joaoapps), Pulse SMS, KDE Connect pricing, Google Messages for web, Samsung's own phone-link options, Zello, Life360, Mysms or Handcent, so their pricing, features and reviews are missing from this report. No Play Store, Reddit, Trustpilot or Chrome Web Store review sentiment survived verification, so the complaints listed here come from vendor docs admitting their own limits and from press coverage, not from user reviews. Time-sensitivity: Pushbullet figures are from 2015 and its current pricing page could not be fetched. MightyText's help page was last updated in May 2024, and its free cap changed from 25 to 15 at some point. The Phone Link doc is undated, and its RCS section is probably stale since Samsung Messages ended service in July 2026. AirDroid showed a 10% promo whose effect on listed prices is unclear. The httpSMS prices come from its JS bundle, not rendered page text. The price positioning finding is my own synthesis, not a verified claim.

## Open questions

- What do Join (one-time purchase, about $4.99?), Pulse SMS (subscription or lifetime), Mysms, Handcent, Zello (Zello Work per-user pricing) and Life360 (Silver/Gold/Platinum family tiers) currently charge, and how do Life360 and Zello price family plans, which matter most for the $7.99 Family tier?
- What are the most common review complaints (Play Store, Reddit, Trustpilot, Chrome Web Store) for MightyText, Pushbullet, AirDroid and Pulse: reliability, battery drain, missed messages, privacy or data handling?
- Can any third-party app reliably read or send RCS in 2026, for example through notification-listener workarounds or a Google API, or must a new entrant accept SMS/MMS-only threads?
- Do any competitors offer real end-to-end encryption for mirrored messages (Pulse has claimed it historically), and is Google Messages for web's E2EE strong enough to undercut a paid E2EE pitch?

## All sources

- https://www.airdroid.com/pricing/airdroid-personal/ (primary, Pricing & tiers of SMS-from-PC apps)
- https://help.mightytext.net/knowledgebase/articles/148001-how-much-does-it-cost-to-send-messages-using-might (primary, Pricing & tiers of SMS-from-PC apps)
- https://sourceforge.net/software/product/Pulse-SMS/ (forum, Pricing & tiers of SMS-from-PC apps)
- https://www.getapp.com/marketing-software/a/mightytext/pricing/ (unreliable, Pricing & tiers of SMS-from-PC apps)
- https://9to5google.com/2015/11/17/pushbullet-pro-tier-too-expensive/ (secondary, Pricing & tiers of SMS-from-PC apps)
- https://www.androidauthority.com/sms-pc-app-shootout-583369/ (unreliable, Pricing & tiers of SMS-from-PC apps)
- https://support.microsoft.com/help/4539634 (primary, Big-platform free incumbents and features)
- https://chromeunboxed.com/google-messages-account-login-is-great-but-it-still-requires-a-connected-phone/ (blog, Big-platform free incumbents and features)
- https://www.windowscentral.com/software-apps/intel-unison-for-windows-11-shuts-down-encourages-users-to-switch-to-another-multi-device-experience (secondary, Big-platform free incumbents and features)
- https://bugs.kde.org/show_bug.cgi?id=464654 (forum, Big-platform free incumbents and features)
- https://answers.microsoft.com/en-us/windows/forum/all/phone-link-group-chat-ongoing-issue/509d3e4f-5d02-4d40-b795-8e344c831c03 (unreliable, User reviews and complaints)
- https://support.microsoft.com/en-us/help/4539634/troubleshooting-for-messages-in-the-your-phone-app (primary, User reviews and complaints)
- https://reddit.sudovanilla.org/r/PushBullet (unreliable, User reviews and complaints)
- https://help.pushbullet.com/articles/why-do-text-messages-from-some-contacts-not-show-in-pushbullet/ (primary, User reviews and complaints)
- https://www.elevenforum.com/t/phone-link-groups-not-syncing.44727/latest (forum, User reviews and complaints)
- https://www.android.com/articles/what-is-find-hub-and-how-to-use-it/ (primary, Adjacent: family walkie-talkie and find-my-phone)
- https://www.capterra.com/p/198898/Zello/pricing/ (secondary, Adjacent: family walkie-talkie and find-my-phone)
- https://northlabapps.com/blog/is-life360-free/ (blog, Adjacent: family walkie-talkie and find-my-phone)
- https://textbee.dev/docs (primary, SMS gateway apps and technical/innovation angle)
- https://httpsms.com/ (primary, SMS gateway apps and technical/innovation angle)
- https://dev.to/capcom6/sms-gateway-for-android-a-simple-and-private-sms-gateway-for-your-projects-1c0m (blog, SMS gateway apps and technical/innovation angle)
