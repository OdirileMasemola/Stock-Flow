# StockFlow Privacy Policy (DRAFT)

> **Draft for review — not yet published.** This draft describes what the StockFlow code currently does.
> Before publishing, the owner must confirm every item marked **[CONFIRM]**, add an effective date,
> and host the final text at a public URL for the Google Play Console.

**Effective date:** [CONFIRM: date of publication]

**Contact:** [odirilemasemola1@gmail.com](mailto:odirilemasemola1@gmail.com)

StockFlow is an Android inventory and point-of-sale app for small retail businesses. It is published by
Odirile Masemola [CONFIRM: individual developer or business name as shown on the Play Console account].

## 1. Information we collect



### Information you give us


| Information                                                                   | Why it is collected                                              |
| ----------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| Full name, username, email address                                            | Create and identify your account                                 |
| Password                                                                      | Sign in. It is stored only as a BCrypt hash, never in plain text |
| Google account email, name and Firebase user ID (when you use Google Sign-In) | Sign you in with Google                                          |
| Role (Owner, Staff or Supplier)                                               | Show the features for your role                                  |
| Profile photo (optional)                                                      | Show on your profile                                             |
| Store name, phone, email, address and store photo (optional)                  | Show your store profile                                          |
| Store map location (optional)                                                 | Save your store's location on the store profile                  |
| Products, categories, prices, stock levels, SKUs/barcodes and product photos  | Run your inventory                                               |
| Sales and the items sold                                                      | Record transactions and build reports                            |
| Suppliers (name and contact details you enter) and purchase orders            | Manage purchasing                                                |




### Information collected automatically


| Information                                                                                                   | Why it is collected                         |
| ------------------------------------------------------------------------------------------------------------- | ------------------------------------------- |
| Firebase Cloud Messaging device token                                                                         | Send low-stock notifications to your device |
| Activity history (for example "product added" or "sale recorded", with your user ID and the product involved) | Show recent activity in your store          |


StockFlow does not include advertising, analytics or crash-reporting SDKs, and does not track you across other apps or websites.

## 2. Device permissions

- **Camera:** used to scan barcodes. Scanning runs on your device with Google ML Kit; camera images are not uploaded.
- **Location (approximate and precise):** used only when you tap the button to capture your store location on the Business Info screen. One location reading is taken and saved with the store profile. StockFlow does not track location in the background.
- **Notifications:** used to show low-stock alerts.
- **Photos:** when you add a profile, store or product photo, you choose a single image with the Android photo picker. StockFlow does not get access to your whole photo library.



## 3. How information is stored and shared

Your data is sent over encrypted HTTPS connections to the StockFlow server. It is processed by these service providers on our behalf:


| Provider                                                           | Purpose                                                        |
| ------------------------------------------------------------------ | -------------------------------------------------------------- |
| Render                                                             | Hosts the StockFlow server                                     |
| Supabase                                                           | Hosts the database and stores uploaded photos                  |
| Google Firebase (Authentication, Cloud Messaging, Cloud Firestore) | Google Sign-In, push notifications and activity history        |
| Google Play services (Sign-In, Location, ML Kit)                   | Sign-in, store location capture and on-device barcode scanning |


We do not sell your personal information and do not share it with third parties for advertising.

Photos you upload are stored at public web addresses so the app can display them. Anyone who has a photo's
address can view it. [CONFIRM: acceptable, or move to a private bucket before launch]

Information in a store (products, sales, suppliers, purchase orders) is visible to other users of the
same StockFlow service according to their role. [CONFIRM: describe who can see shop data]

### On your device

The app keeps a local copy of your shop data so it works offline, and syncs changes when you are online.
Your sign-in session is stored encrypted and is excluded from Android backups. The offline copy of shop
data may be included in your device's Android backup.

## 4. Data retention and deletion

You can delete your account in the app: **Settings → Delete account**. If you cannot open the app,
email [odirilemasemola1@gmail.com](mailto:odirilemasemola1@gmail.com) from the email address on your account.

When you delete your account:

- Your sign-in, name, username and email address are removed.
- Your profile photo and store profile (including store location and store photo) are deleted.
- Notification tokens and activity history for your account are deleted.
- Sales you recorded are kept as shop records. The account they link to stays only as an anonymised
record with no name, email, username or sign-in.
- Products, categories, suppliers, purchase orders and product photos are shared shop records and are not deleted.

The account-deletion page is available at `https://stock-flow-trbq.onrender.com/account-deletion`.
[CONFIRM: final public URL]

How long kept shop records and server backups are retained: [CONFIRM — do not publish a period that is not actually enforced]

## 5. Children

StockFlow is a business tool and is not directed at children. [CONFIRM: minimum age]

## 6. Security

Passwords are hashed with BCrypt, API access requires a signed token, and release builds only connect
over HTTPS. No method of transmission or storage is completely secure.

## 7. Changes to this policy

We will update this page when our practices change and revise the effective date.

## 8. Contact

Questions about this policy or your data: [odirilemasemola1@gmail.com](mailto:odirilemasemola1@gmail.com)