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

## Evaluated and deferred: a Customer entity

Considered for V1, and deliberately **deferred to V2**. The evaluation is
recorded here so it does not have to be repeated.

### The proposal

Promote the free-form customer text into a real entity:

```
Customer → Work entries → quantity → status → delivery → history
```

### What it would genuinely enable

| Capability | Available today? |
| --- | --- |
| Find everything for one customer | **Yes** — search on All Work already does this |
| Per-customer totals | No |
| Autocomplete while typing a name | No |
| Rename a customer once, everywhere | No |

Only two of the four are actually missing, and neither is part of the core
loop. The one users ask for first — "show me this customer's work" — was
delivered by search at a fraction of the cost.

### What it would cost

**The fields are not semantically customers.** All three custom fields are
user-renamed; field one ships with the placeholder *"e.g. Client Name"* as a
*suggestion*. A user is free to name it Fabric, Machine or Invoice, and some
will. Promoting field one to a Customer entity silently reinterprets data the
app explicitly told the user was theirs to define, and breaks the flexibility
that makes the fields worth having.

**Deduplication is not mechanisable.** Real logs contain «کریمی» and «كریمی» —
Persian keheh against Arabic kaf, visually identical, different code points —
along with «آقای کریمی» and bare «کریمی». Folding can *find* those (see
`PersianSearch`), but deciding whether two spellings are one person is a
judgement only the user can make. A migration that guesses merges records
wrongly and silently.

**The UI grows a whole second object.** Picker, create, edit, delete, and a
merge-duplicates flow — plus an answer to "what happens to this customer's
orders when the customer is deleted". That is a CRM, and this app is a
day-book.

### Decision

Defer. The V1 product manages **work**, and a customer is an attribute of a
piece of work rather than a thing to be administered.

### The cheaper step that comes first

If customer handling needs improving before a full entity is justified, the
next move is **suggesting previously-used values** while typing a custom
field. It removes typos and drift, makes per-customer search reliable, and
needs no schema change, no migration and no new screens. That belongs in V1.1
and should be tried before anything heavier.

### What would change the decision

A Customer entity earns its place when users need per-customer **money** —
what is owed, what has been paid. That is a different product promise from a
work log, and it should be entered deliberately rather than arrived at by
promoting a text field.
