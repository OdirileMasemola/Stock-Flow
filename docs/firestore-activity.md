# Firestore activity / audit history (Part 3 NoSQL)

## Architecture choice

StockFlow authenticates API calls with a **custom JWT** (email/password and Google). Firebase Auth is used for Google Sign-In and FCM, but email/password users are **not** always signed into Firebase Auth.

Therefore this milestone uses:

| Direction | Mechanism |
|-----------|-----------|
| **Write** | Backend **Firebase Admin SDK** → Firestore (same credentials as FCM) |
| **Read** | Android → `GET /api/activity` (StockFlow JWT) → Admin SDK proxy → Firestore |

Data **lives in Firestore** and is visible in the Firebase Console. No service account is shipped on Android. No second offline sync for Firestore.

### Why not client Firestore writes?

Email/password sessions lack a reliable Firebase Auth UID for security rules. Putting Admin credentials on the device is unsafe. Backend writes cover every auth path.

## Collection structure

```
businesses/{businessId}/activity/{activityId}
```

| Field | Type | Notes |
|-------|------|--------|
| `type` | string | `PRODUCT_CREATED`, `PRODUCT_UPDATED`, `PRODUCT_DELETED`, `LOW_STOCK` |
| `message` | string | Human-readable summary |
| `userId` | number | Acting StockFlow user id |
| `productId` | number? | When related to a product |
| `productName` | string? | Denormalized for UI |
| `timestamp` | timestamp | Server timestamp |
| `metadata` | map? | e.g. stock levels for `LOW_STOCK` |

**businessId**: PostgreSQL `businesses.id` when the user has a store profile; otherwise `user-{userId}`.

Products, users, and sales remain in PostgreSQL — only activity/audit events are stored in Firestore.

## Write triggers (real actions only)

- `POST /api/products` → `PRODUCT_CREATED` (after PG success)
- `PUT /api/products/{id}` → `PRODUCT_UPDATED`
- `DELETE /api/products/{id}` → `PRODUCT_DELETED`
- Low-stock crossing (same path as FCM) → `LOW_STOCK`

Firestore failures are logged and **never** fail the PostgreSQL/product operation.

Offline product ops write activity when the pending sync successfully hits the API (same create/update/delete endpoints).

## Android UI

Dashboard → **Recent activity** list (loading / empty / error). Sourced from Firestore via the API, not PostgreSQL.

## API

```
GET /api/activity?limit=20
Authorization: Bearer <StockFlow JWT>
```

Response:

```json
{
  "businessId": "42",
  "items": [
    {
      "id": "...",
      "type": "PRODUCT_CREATED",
      "message": "Product created: Milk",
      "userId": 7,
      "productId": 10,
      "productName": "Milk",
      "timestamp": "2026-09-18T00:00:00Z",
      "metadata": null
    }
  ]
}
```

## Security rules

See repo root [`firestore.rules`](../firestore.rules). Client access is denied; Admin SDK bypasses rules.

### Deploy rules (Firebase Console)

1. Open [Firebase Console](https://console.firebase.google.com/) → project `stockflow-be90c` (or your project).
2. **Firestore Database** → **Rules**.
3. Paste contents of `firestore.rules` → **Publish**.

Or with Firebase CLI:

```bash
firebase deploy --only firestore:rules
```

## Console verification

1. Ensure Render (or local) has `FIREBASE_CREDENTIALS_JSON` / path (same as FCM — see [fcm-low-stock.md](fcm-low-stock.md)).
2. Sign in to the app (or call API with JWT).
3. Create a product.
4. Firebase Console → Firestore → `businesses` → `{businessId}` → `activity` → new document with `type=PRODUCT_CREATED`.
5. Open Dashboard → **Recent activity** shows the same event.

## Env / Render

No new secrets beyond existing Firebase Admin env vars used for FCM:

| Variable | Purpose |
|----------|---------|
| `FIREBASE_CREDENTIALS_JSON` | Service account JSON (preferred on Render) |
| or `FIREBASE_CREDENTIALS_PATH` | Path to JSON file |
| `FIREBASE_PROJECT_ID` | Optional |

## Out of scope

- Migrating products/users/sales to Firestore
- Play Store listing prep
- Client-side Firestore writes
- Second offline queue for activity
