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

StockFlow currently works as **one shared workspace**. Shop records are not separated by shop or owner.

- **Shared with every signed-in StockFlow user, whatever their role:** products, categories, prices,
  stock levels, product photos, suppliers (including the contact details entered for them), purchase
  orders, sales and the items sold, and dashboard and report totals. Signed-in users can also add
  categories and add, change or delete products, suppliers and purchase orders.
- **Sales** show the user ID of the account that recorded them, not your name or email.
- **Private to your account:** your profile (name, username, email), your store profile (store name,
  phone, email, address and map location) and your activity history.
- People without a StockFlow account cannot see shop records.

Do not enter information into shop records that you do not want other StockFlow users to see.

### Photos

Profile, store and product photos are stored at public web addresses so the app can display them.
Anyone who has a photo's address can view it, without signing in.

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

The app keeps a local copy of shop data so it works offline, and syncs changes when you are online.
Your sign-in session is stored encrypted and is excluded from Android backups. The offline copy of shop
data may be included in your device's Android backup.

## 5. Data retention

Your information is kept while your account is active. When you delete your account, it is deleted or
anonymised as described below. We review stored records yearly.

## 6. Deleting your account

You can delete your account in the app: **Settings → Delete account**. If you cannot open the app,
email odirilemasemola1@gmail.com from the email address on your account. Full details are at
https://stock-flow-trbq.onrender.com/account-deletion.

When you delete your account:

- Your sign-in, name, username and email address are removed.
- Your profile photo and store profile (including store location and store photo) are deleted.
- Notification tokens and activity history for your account are deleted.
- Sales you recorded are kept as shop records. The account they link to stays only as an anonymised
  record with no name, email, username or sign-in.
- Products, categories, suppliers, purchase orders and product photos are shared shop records and are not deleted.

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
