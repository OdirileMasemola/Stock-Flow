# StockFlow Privacy Policy

**Effective date:** 9 October 2026

**Published at:** https://stock-flow-trbq.onrender.com/privacy-policy

**Contact:** odirilemasemola1@gmail.com

StockFlow is an Android inventory and point-of-sale app for small retail businesses. It is published by
Odirile Masemola, an individual developer. This policy explains what information StockFlow collects, how it
is used and shared, and how you can delete your account.

## 1. Information we collect

### Information you give us

| Information | Why it is collected |
| :--- | :--- |
| Full name, username, email address | Create and identify your account |
| Password | Sign in. It is stored only as a BCrypt hash, never in plain text |
| Google account email, name and Firebase user ID (when you use Google Sign-In) | Sign you in with Google |
| Role (Owner, Staff or Supplier) | Show the features for your role |
| Profile photo (optional) | Show on your profile |
| Store name, phone, email, address and store photo (optional) | Show your store profile |
| Store map location (optional) | Save your store's location on the store profile |
| Products, categories, prices, stock levels, SKUs/barcodes and product photos | Run your inventory |
| Sales and the items sold | Record transactions and build reports |
| Suppliers (name, contact person, phone, email and address you enter) and purchase orders | Manage purchasing |

### Information collected automatically

| Information | Why it is collected |
| :--- | :--- |
| Firebase Cloud Messaging device token | Send low-stock notifications to your device |
| Activity history (for example "product added" or "sale recorded", with your user ID and the product involved) | Show recent activity |

StockFlow does not include advertising, analytics or crash-reporting SDKs, and does not track you across other apps or websites.

## 2. Device permissions

- **Camera:** used to scan barcodes. Scanning runs on your device with Google ML Kit; camera images are not uploaded.
- **Location (approximate and precise):** used only when you tap the button to capture your store location on the Business Info screen. One location reading is taken and saved with the store profile. StockFlow does not track location in the background.
- **Notifications:** used to show low-stock alerts.
- **Photos:** when you add a profile, store or product photo, you choose a single image with the Android photo picker. StockFlow does not get access to your whole photo library.

## 3. Who can see your information

Each StockFlow account is its own shop. Shop records are kept separate for each account.

- **Private to your shop:** products, prices, stock levels, SKUs/barcodes, suppliers (including the
  contact details you enter for them), purchase orders, sales and the items sold, and dashboard and
  report totals. Other StockFlow users cannot see or change them.
- **Staff accounts:** StockFlow cannot yet link Staff accounts to an Owner's shop. A Staff account is
  currently its own separate shop: the Staff user does not see the Owner's records, and the Owner does
  not see the Staff user's records.
- **Supplier accounts** cannot view or change any shop's products, sales, suppliers, purchase orders,
  dashboard or reports.
- **Categories are shared:** category names (for example "Beverages") form one list shared by all
  StockFlow users. Any signed-in user can see the list, and Owner and Staff users can add to it. Do not
  put personal or confidential information in a category name.
- **Private to your account:** your profile (name, username, email), your store profile (store name,
  phone, email, address and map location) and your activity history.
- People without a StockFlow account cannot see shop records.

### Photos

Profile, store and product photos are stored at public web addresses so the app can display them.
Anyone who has a photo's address can view it, without signing in. This applies to product photos even
though the product itself is private to your shop.

## 4. How information is stored and shared

Your data is sent over encrypted HTTPS connections to the StockFlow server. It is processed by these service providers on our behalf:

| Provider | Purpose |
| :--- | :--- |
| Render | Hosts the StockFlow server |
| Supabase | Hosts the database and stores uploaded photos |
| Google Firebase (Authentication, Cloud Messaging, Cloud Firestore) | Google Sign-In, push notifications and activity history |
| Google Play services (Sign-In, Location, ML Kit) | Sign-in, store location capture and on-device barcode scanning |

We do not sell your personal information and do not share it with third parties for advertising.

### On your device

The app keeps a local copy of your shop data on your device so it works offline. Changes you make
offline are queued on the device and sent to the server when you are online and signed in. If your
session expires, queued changes stay on the device and are sent after you sign in again with the same
account. Signing out removes your account's offline copy from the device, including changes that have
not been sent yet.

Your sign-in session is stored encrypted and is excluded from Android backups. The offline copy of shop
data may be included in your device's Android backup.

## 5. Data retention

Your information is kept while your account is active. When you delete your account, your personal
account details are deleted or anonymised, and your shop records are kept under the anonymised account,
as described below. We review stored records yearly.

## 6. Deleting your account

You can delete your account in the app: **Settings → Delete account**. If you cannot open the app,
email odirilemasemola1@gmail.com from the email address on your account. Full details are at
https://stock-flow-trbq.onrender.com/account-deletion.

When you delete your account:

- Your sign-in, name, username and email address are removed.
- Your profile photo and store profile (including store location and store photo) are deleted.
- Notification tokens and activity history for your account are deleted.
- Your account stays in the database only as an anonymised record with no name, email, username or
  sign-in.
- Your shop records are **not** deleted. Products, product photos, suppliers (including the contact
  details you entered for them), purchase orders and their items, and sales and the items sold stay in
  the database, linked to the anonymised account, so stock, sales and purchasing history stay
  consistent. Nobody can sign in to the anonymised account, so these records are no longer shown to any
  StockFlow user. Product photos remain at their public web addresses.
- Categories you added stay in the shared category list.

## 7. Age requirement

StockFlow is a business tool for adults. It is not intended for anyone under 18, and you must be at least
18 years old to use it.

## 8. Security

Passwords are hashed with BCrypt, API access requires a signed token, and the app only connects to the
server over HTTPS. No method of transmission or storage is completely secure.

## 9. Changes to this policy

We will update this page when our practices change and revise the effective date.

## 10. Contact

Questions about this policy or your data: odirilemasemola1@gmail.com
