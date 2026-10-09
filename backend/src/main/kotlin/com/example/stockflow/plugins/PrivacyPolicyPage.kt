package com.example.stockflow.plugins

/**
 * Public privacy policy. Keep in sync with docs/privacy-policy.md.
 */
object PrivacyPolicyPage {
    val html: String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>StockFlow Privacy Policy</title>
          <style>
            body { font-family: Georgia, serif; line-height: 1.5; margin: 0; color: #1c1c1c; background: #f7f5f2; }
            main { max-width: 40rem; margin: 0 auto; padding: 2rem 1.25rem 3rem; }
            h1 { font-size: 1.75rem; line-height: 1.2; }
            h2 { font-size: 1.15rem; margin-top: 1.75rem; }
            h3 { font-size: 1rem; margin-top: 1.25rem; }
            table { border-collapse: collapse; width: 100%; font-size: 0.95rem; }
            th, td { text-align: left; vertical-align: top; padding: 0.4rem 0.5rem; border-bottom: 1px solid #ddd6cc; }
          </style>
        </head>
        <body>
          <main>
            <h1>StockFlow Privacy Policy</h1>
            <p><strong>Effective date:</strong> 9 October 2026</p>
            <p><strong>Contact:</strong> <a href="mailto:odirilemasemola1@gmail.com">odirilemasemola1@gmail.com</a></p>
            <p>StockFlow is an Android inventory and point-of-sale app for small retail businesses. It is published by Odirile Masemola, an individual developer. This policy explains what information StockFlow collects, how it is used and shared, and how you can delete your account.</p>

            <h2>1. Information we collect</h2>
            <h3>Information you give us</h3>
            <table>
              <tr><th>Information</th><th>Why it is collected</th></tr>
              <tr><td>Full name, username, email address</td><td>Create and identify your account</td></tr>
              <tr><td>Password</td><td>Sign in. It is stored only as a BCrypt hash, never in plain text</td></tr>
              <tr><td>Google account email, name and Firebase user ID (when you use Google Sign-In)</td><td>Sign you in with Google</td></tr>
              <tr><td>Role (Owner, Staff or Supplier)</td><td>Show the features for your role</td></tr>
              <tr><td>Profile photo (optional)</td><td>Show on your profile</td></tr>
              <tr><td>Store name, phone, email, address and store photo (optional)</td><td>Show your store profile</td></tr>
              <tr><td>Store map location (optional)</td><td>Save your store's location on the store profile</td></tr>
              <tr><td>Products, categories, prices, stock levels, SKUs/barcodes and product photos</td><td>Run your inventory</td></tr>
              <tr><td>Sales and the items sold</td><td>Record transactions and build reports</td></tr>
              <tr><td>Suppliers (name, contact person, phone, email and address you enter) and purchase orders</td><td>Manage purchasing</td></tr>
            </table>
            <h3>Information collected automatically</h3>
            <table>
              <tr><th>Information</th><th>Why it is collected</th></tr>
              <tr><td>Firebase Cloud Messaging device token</td><td>Send low-stock notifications to your device</td></tr>
              <tr><td>Activity history (for example "product added" or "sale recorded", with your user ID and the product involved)</td><td>Show recent activity</td></tr>
            </table>
            <p>StockFlow does not include advertising, analytics or crash-reporting SDKs, and does not track you across other apps or websites.</p>

            <h2>2. Device permissions</h2>
            <ul>
              <li><strong>Camera:</strong> used to scan barcodes. Scanning runs on your device with Google ML Kit; camera images are not uploaded.</li>
              <li><strong>Location (approximate and precise):</strong> used only when you tap the button to capture your store location on the Business Info screen. One location reading is taken and saved with the store profile. StockFlow does not track location in the background.</li>
              <li><strong>Notifications:</strong> used to show low-stock alerts.</li>
              <li><strong>Photos:</strong> when you add a profile, store or product photo, you choose a single image with the Android photo picker. StockFlow does not get access to your whole photo library.</li>
            </ul>

            <h2>3. Who can see your information</h2>
            <p>Each StockFlow account is its own shop. Shop records are kept separate for each account.</p>
            <ul>
              <li><strong>Private to your shop:</strong> products, prices, stock levels, SKUs/barcodes, suppliers (including the contact details you enter for them), purchase orders, sales and the items sold, and dashboard and report totals. Other StockFlow users cannot see or change them.</li>
              <li><strong>Staff accounts:</strong> StockFlow cannot yet link Staff accounts to an Owner's shop. A Staff account is currently its own separate shop: the Staff user does not see the Owner's records, and the Owner does not see the Staff user's records.</li>
              <li><strong>Supplier accounts</strong> cannot view or change any shop's products, sales, suppliers, purchase orders, dashboard or reports.</li>
              <li><strong>Categories are shared:</strong> category names (for example "Beverages") form one list shared by all StockFlow users. Any signed-in user can see the list, and Owner and Staff users can add to it. Do not put personal or confidential information in a category name.</li>
              <li><strong>Private to your account:</strong> your profile (name, username, email), your store profile (store name, phone, email, address and map location) and your activity history.</li>
              <li>People without a StockFlow account cannot see shop records.</li>
            </ul>
            <h3>Photos</h3>
            <p>Profile, store and product photos are stored at public web addresses so the app can display them. Anyone who has a photo's address can view it, without signing in. This applies to product photos even though the product itself is private to your shop.</p>

            <h2>4. How information is stored and shared</h2>
            <p>Your data is sent over encrypted HTTPS connections to the StockFlow server. It is processed by these service providers on our behalf:</p>
            <table>
              <tr><th>Provider</th><th>Purpose</th></tr>
              <tr><td>Render</td><td>Hosts the StockFlow server</td></tr>
              <tr><td>Supabase</td><td>Hosts the database and stores uploaded photos</td></tr>
              <tr><td>Google Firebase (Authentication, Cloud Messaging, Cloud Firestore)</td><td>Google Sign-In, push notifications and activity history</td></tr>
              <tr><td>Google Play services (Sign-In, Location, ML Kit)</td><td>Sign-in, store location capture and on-device barcode scanning</td></tr>
            </table>
            <p>We do not sell your personal information and do not share it with third parties for advertising.</p>
            <h3>On your device</h3>
            <p>The app keeps a local copy of your shop data on your device so it works offline. Changes you make offline are queued on the device and sent to the server when you are online and signed in. If your session expires, queued changes stay on the device and are sent after you sign in again with the same account. Signing out removes your account's offline copy from the device, including changes that have not been sent yet.</p>
            <p>Your sign-in session is stored encrypted and is excluded from Android backups. The offline copy of shop data may be included in your device's Android backup.</p>

            <h2>5. Data retention</h2>
            <p>Your information is kept while your account is active. When you delete your account, your personal account details are deleted or anonymised, and your shop records are kept under the anonymised account, as described below. We review stored records yearly.</p>

            <h2>6. Deleting your account</h2>
            <p>You can delete your account in the app: <strong>Settings &rarr; Delete account</strong>. If you cannot open the app, email <a href="mailto:odirilemasemola1@gmail.com">odirilemasemola1@gmail.com</a> from the email address on your account. Full details are at <a href="https://stock-flow-trbq.onrender.com/account-deletion">https://stock-flow-trbq.onrender.com/account-deletion</a>.</p>
            <p>When you delete your account:</p>
            <ul>
              <li>Your sign-in, name, username and email address are removed.</li>
              <li>Your profile photo and store profile (including store location and store photo) are deleted.</li>
              <li>Notification tokens and activity history for your account are deleted.</li>
              <li>Your account stays in the database only as an anonymised record with no name, email, username or sign-in.</li>
              <li>Your shop records are <strong>not</strong> deleted. Products, product photos, suppliers (including the contact details you entered for them), purchase orders and their items, and sales and the items sold stay in the database, linked to the anonymised account, so stock, sales and purchasing history stay consistent. Nobody can sign in to the anonymised account, so these records are no longer shown to any StockFlow user. Product photos remain at their public web addresses.</li>
              <li>Categories you added stay in the shared category list.</li>
            </ul>

            <h2>7. Age requirement</h2>
            <p>StockFlow is a business tool for adults. It is not intended for anyone under 18, and you must be at least 18 years old to use it.</p>

            <h2>8. Security</h2>
            <p>Passwords are hashed with BCrypt, API access requires a signed token, and the app only connects to the server over HTTPS. No method of transmission or storage is completely secure.</p>

            <h2>9. Changes to this policy</h2>
            <p>We will update this page when our practices change and revise the effective date.</p>

            <h2>10. Contact</h2>
            <p>Questions about this policy or your data: <a href="mailto:odirilemasemola1@gmail.com">odirilemasemola1@gmail.com</a></p>
          </main>
        </body>
        </html>
    """.trimIndent()
}
