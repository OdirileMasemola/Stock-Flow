# FCM low-stock push notifications (Part 3 Stage 4)

## Flow

Backend stock write → detect **crossing into** low stock → Firebase Admin SDK → FCM → Android `FirebaseMessagingService` → system notification.

## Dedup rule

Notify only when `previousStock > minStockLevel && currentStock <= minStockLevel`.

Further sales while already low do **not** re-notify. Restocking above min, then dropping again, will notify again.

## Trigger points

- Sale stock deduction (`POST /api/sales`)
- Product create/update when stock/min causes a crossing (`POST/PUT /api/products`)
- Purchase-order receive increases stock (does not trigger low-stock alerts)

## Recipients

Active FCM tokens for **Owner**-role users plus the acting authenticated user (shared inventory has no per-product owner).

## Token API

- `POST /api/notifications/device-token` — JWT required; body `{ "fcmToken": "...", "platform": "android" }`
- `DELETE /api/notifications/device-token` — JWT required; body `{ "fcmToken": "..." }`
- `userId` always taken from JWT (never trusted from the client body)

## PostgreSQL

Table `device_tokens`: `id`, `user_id` → users, `token` UNIQUE, `platform`, `active`, `created_at`, `updated_at`. Index on `user_id`. Multiple devices per user allowed.

## Render / production env

Set on https://stock-flow-trbq.onrender.com (and local `.env.local` — never commit):

| Variable | Purpose |
|---|---|
| `FIREBASE_CREDENTIALS_JSON` | Full service-account JSON (preferred on Render) |
| **or** `FIREBASE_CREDENTIALS_PATH` / `GOOGLE_APPLICATION_CREDENTIALS` | Path to JSON file |
| `FIREBASE_PROJECT_ID` | Optional; e.g. `stockflow-be90c` |

Android: keep existing `google-services.json` for project `stockflow-be90c` / `com.example.stockflow`. Do **not** put service-account keys in the app.

## Physical device checklist

1. `google-services.json` present under `app/`
2. Render has Firebase Admin credentials
3. Login on a physical device / emulator with Google Play
4. Grant notification permission (Android 13+)
5. Confirm Settings → Notifications shows push enabled
6. Create a product above min stock, then sell/update until stock crosses ≤ min
7. Expect a system notification titled “Low stock alert”

## Out of scope (not started)

- Play Store listing prep
- Stage 5+

Firestore activity history is documented in [firestore-activity.md](firestore-activity.md).
