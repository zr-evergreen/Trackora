# Trackora — product model

The reference for what this app is, who it is for, and what it will not become.
Read this before adding a feature. If a proposal does not serve the core
workflow below, the default answer is no.

## What Trackora is

> Trackora helps self-employed workers and small businesses record the work they
> produce, track it through to delivery, and know what is still outstanding —
> in Persian, on the Jalali calendar, entirely offline.

It is a **day-book of work done**, not a list of things to do. The distinction
drives every decision in this document.

## Target user

A self-employed Iranian tradesperson or a one- to three-person workshop paid per
piece or per job: tailoring and alterations, shoe and phone repair, handicraft
production, small-batch manufacture. Works on an inexpensive Android phone,
frequently on Android 7–11. Keeps records in a paper notebook today, and the
notebook is what Trackora replaces.

## Jobs to be done

1. *"I finished a batch — let me record it before I forget."* — capture, in seconds.
2. *"What have I finished that the customer hasn't collected yet?"* — the daily question.
3. *"How much did I produce this month?"* — periodic totals.
4. *"Did I ever deliver that order?"* — history, searchable.

## Core workflow

```
capture work  →  track quantity  →  progress  →  complete  →  deliver  →  history
```

Every screen exists to serve one of those steps. A screen that serves none of
them should not exist.

## Core entities

| Entity | Notes |
| --- | --- |
| `WorkEntry` | The only aggregate. Title, quantity, status, date, description, photo, three custom fields. |
| `Status` | `IN_PROGRESS → COMPLETED → DELIVERED`. A fulfilment pipeline, not a done flag. |
| `date` | The day the work **was done**. Deliberately not a due date. |
| custom fields 1–3 | User-renamed free text. In practice: customer, job code, note. |

There is intentionally **no due date, no deadline and no priority** in the
schema. Trackora records what happened; it does not schedule what should.

## Primary screens

- **Today** — what needs attention now: work finished but not yet delivered, then today's entries.
- **All Work** — the searchable history.
- **Reports** — totals over day, week, month.
- **Settings** — language, theme, custom field names, export, notifications.
- **Add/Edit** — the capture form, reached from Today or by opening an entry.

## Differentiators

These are the only things Trackora does that no mainstream task app does. They
are protected; nothing may dilute them.

1. **Quantity per entry** — work is counted in units.
2. **The delivery pipeline** — completed and delivered are different states, and the gap between them is where money is lost.
3. **Persian-first** — Jalali calendar, Persian-Indic digits, Vazirmatn, Saturday-first week, full RTL. Not a translation layer.
4. **Offline by construction** — no network permission, no analytics, no accounts, no third-party SDKs.

## Deliberately not building

Each is present in most competitors, and each would make Trackora a worse
version of a product that already exists.

- Projects, areas, folders
- Priorities
- Recurring entries
- Subtasks and checklists
- Natural-language date parsing
- Focus mode, Pomodoro, streaks, gamification
- Cloud sync, accounts, collaboration

## Technical constraints

- `minSdk 24`. `java.time` works only via core library desugaring — already
  configured in all eight modules and **must not be removed**.
- Seven-module Clean Architecture. `core/domain` has no Android dependencies.
- Room + Hilt + Compose + WorkManager. No new third-party dependency without a
  clear justification.
- No network permission. Adding one is a product decision, not a technical one.

## Localization constraints

Persian is the primary locale, English secondary. Every user-visible string
lives in both `values/strings.xml` and `values-fa/strings.xml`. New work must
preserve, and is tested against:

- Vazirmatn at all weights, `letterSpacing = 0`, raised line heights
- Persian-Indic digits in `fa`, Western digits in `en`, `٬` as the thousands separator
- Jalali dates with Persian month names in `fa`, Gregorian in `en`
- Saturday-first weeks and Persian weekday names
- `Icons.AutoMirrored` for directional glyphs; `autoMirrored` on directional vectors
- `TextDirection.Content` on any text the user typed
- Dark mode contrast, and no colour literals outside the theme
