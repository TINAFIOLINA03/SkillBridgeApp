
# SkillBridge

**Learning to Applied Skill Tracker** — an MVP web app built on **Zoho Catalyst** that helps you track what you learn and how you apply it (e.g. projects, practice, teaching).

---

## Overview

SkillBridge lets users:

- **Add learnings** (topic, category, source)
- **View all learnings** in a list split into “Needs application” and “Applied”
- **Open a learning** to see details and **add applied skills** (type, notes, applied action)
- **See a dashboard** with counts (total / applied / pending), progress %, and the oldest 3 pending items in “Today’s focus” and “Needs an application”

Data is stored in **Zoho Catalyst Data Store** and accessed via **ZCQL**. Authentication and user scoping are handled by **Catalyst Hosted Authentication** (no custom auth in the app).

---

## Project structure

```
SkillBridgeApp/
├── catalyst.json              # Catalyst project config (functions + client)
├── .catalystrc                # Catalyst CLI config
├── client/                    # Web client (static frontend)
│   ├── index.html             # Single-page app (dashboard, list, detail, modals)
│   ├── main.js                # API calls, navigation, UI logic
│   ├── main.css               # Styles (dark theme, cards, modals)
│   └── client-package.json
└── functions/
    └── SkillBridge/           # Advanced I/O function (Java)
        ├── SkillBridge.java   # All HTTP routing and backend logic
        ├── catalyst-config.json
        ├── .classpath
        └── .project
```

- **Backend:** One Advanced I/O function, `SkillBridge`, implemented in `functions/SkillBridge/SkillBridge.java`. It handles all API routes and uses Catalyst Data Store (ZCQL + table APIs).
- **Frontend:** Static client in `client/`: `index.html` (structure), `main.js` (behavior), `main.css` (layout and theme).

---

## Data model

### Learning

| Column       | Type     | Notes                          |
|-------------|----------|---------------------------------|
| ROWID       | bigint   | Primary key (auto)              |
| CREATORID   | bigint   | User scope (auto)               |
| CREATEDTIME | datetime | Auto                            |
| MODIFIEDTIME| datetime | Auto                            |
| topic       | varchar  | Required                        |
| category    | varchar  | Required                        |
| source      | varchar  | Optional                        |

### AppliedSkill

| Column       | Type        | Notes                          |
|-------------|-------------|---------------------------------|
| ROWID       | bigint      | Primary key (auto)              |
| CREATORID   | bigint      | User scope (auto)               |
| CREATEDTIME | datetime    | Auto                            |
| MODIFIEDTIME| datetime    | Auto                            |
| learning_id | foreign key | → Learning.ROWID, required     |
| type        | varchar     | Required                        |
| notes       | text        | Optional                        |
| applied_action | text     | Optional (e.g. “Applied action”)|

**Relationships:** One **Learning** → many **AppliedSkill**. Each applied skill belongs to one learning.

Tables must exist in the Catalyst project’s Data Store (created in the Catalyst console). Queries are scoped by the current user via Catalyst (CREATORID).

---

## API (backend)

Base path for the function: **`/server/SkillBridge`** (or your deployed function URL).

| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/learning` | List all learnings for the user (with applied count and status). |
| POST   | `/api/learning` | Create a learning. Body: `{ "topic", "category", "source"? }`. |
| GET    | `/api/learning/{id}` | Get one learning and its applied skills. |
| DELETE | `/api/learning/{id}` | Delete a learning and its applied skills. |
| POST   | `/api/learning/{id}/applied` | Add an applied skill. Body: `{ "type", "notes"?, "applied_action"? }`. |

Responses are JSON. Errors use HTTP status codes and a JSON body with an `error` field.

---

## Frontend (client)

- **Dashboard:** “View Learning” and “+ Add Learning” in the header; “At a glance” (counts + progress); “Today’s focus” and “Needs an application” each show up to **3 oldest pending** learnings, labeled “Not started”.
- **Learning list:** “Your Learning” with “Needs application” and “Applied” sections; “← Dashboard” to go back. No “View Learning” / “Add Learning” in the header here.
- **Learning detail:** One learning’s info and list of applied skills; “+ Add Applied Skill” opens a modal; “← Learning” back to list. Header actions hidden.
- **Modals:** “Add learning” (topic, category dropdown, source); “Add applied skill” (Applied action, type dropdown, notes). Category and type use fixed options (e.g. Technical, Project, Practice).

The client calls the backend with `fetch` to `window.API_BASE || '/server/SkillBridge'` and uses `credentials: 'include'` for auth.

---

## Tech stack

- **Backend:** Java 17, Zoho Catalyst Advanced I/O, Catalyst Java SDK (ZCObject, ZCTable, ZCRowObject, ZCQL), `org.json.simple` for JSON.
- **Frontend:** HTML5, vanilla JS, CSS (custom, dark theme, responsive).
- **Infra:** Zoho Catalyst (serverless function + client hosting, Data Store, Hosted Authentication).

---

## Running and deploying

1. **Prerequisites:** Node.js (for Catalyst CLI), Java 17, Catalyst CLI, and a Zoho Catalyst project with Data Store tables **Learning** and **AppliedSkill**.
2. **Deploy:** From the project root run:
   ```bash
   catalyst deploy
   ```
   This deploys the **SkillBridge** function and the **client**; the client URL will be shown (e.g. `https://<project>-<id>.development.catalystserverless.in/app/index.html`).
3. **Local development:** Run the client from `client/` (e.g. any static server). Set `window.API_BASE` to your deployed function URL if the client is not served from the same Catalyst app.

---

## Configuration

- **`catalyst.json`:** `functions.targets` = `["SkillBridge"]`, `functions.source` = `"functions"`, `client.source` = `"client"`.
- **`functions/SkillBridge/catalyst-config.json`:** Deployment name `SkillBridge`, stack `java17`, type `advancedio`, main class `SkillBridge`.
- **Client API base:** In `client/main.js`, `API_BASE` is `window.API_BASE || '/server/SkillBridge'`. Override `window.API_BASE` if the client is served from a different origin.

---

## Notes

- Authentication and user scoping are handled by Catalyst; the backend does not implement login or validate tokens.
- ROWID, CREATORID, CREATEDTIME, MODIFIEDTIME are managed by Catalyst; the app only sets topic, category, source (Learning) and learning_id, type, notes, applied_action (AppliedSkill).
- The backend uses the table name **AppliedSkill** in ZCQL and `getTable()`; ensure the Data Store table name matches.

---
