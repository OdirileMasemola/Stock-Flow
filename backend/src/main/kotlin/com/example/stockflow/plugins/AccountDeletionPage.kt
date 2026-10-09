package com.example.stockflow.plugins

/**
 * Public explanation of account deletion.
 */
object AccountDeletionPage {
    val html: String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Delete your StockFlow account</title>
          <style>
            body { font-family: Georgia, serif; line-height: 1.5; margin: 0; color: #1c1c1c; background: #f7f5f2; }
            main { max-width: 40rem; margin: 0 auto; padding: 2rem 1.25rem 3rem; }
            h1 { font-size: 1.75rem; line-height: 1.2; }
            h2 { font-size: 1.15rem; margin-top: 1.75rem; }
          </style>
        </head>
        <body>
          <main>
            <h1>Delete your StockFlow account</h1>
            <p>You can delete your StockFlow account from the Android app. No login is required to read this page.</p>
            <h2>How to request deletion</h2>
            <ol>
              <li>Open the StockFlow app and sign in.</li>
              <li>Open Settings.</li>
              <li>Choose Delete account.</li>
              <li>Read the confirmation and confirm. The app sends the deletion only after that confirmation.</li>
            </ol>
            <p>If you cannot open the app, email <a href="mailto:odirilemasemola1@gmail.com">odirilemasemola1@gmail.com</a> from the email address on your StockFlow account and ask for your account to be deleted.</p>
            <h2>What is deleted</h2>
            <ul>
              <li>Your sign-in: password and Google link.</li>
              <li>Your name, username and email address.</li>
              <li>Your profile photo, when that file belongs only to your account.</li>
              <li>Your store profile: store name, phone, email, address, map location and store photo, when that file belongs only to your account.</li>
              <li>Notification tokens registered for your account.</li>
              <li>Activity history stored for your account.</li>
            </ul>
            <h2>What is kept</h2>
            <p>Sales already recorded stay in the shop records. Each sale must keep a link to an account, so the account row remains with the name, email, username and sign-in removed. Products, categories, suppliers and purchase orders are shared shop records and are not deleted with an account. Product photos belong to those shared products and are not deleted with an account.</p>
            <h2>How long it takes</h2>
            <p>The account is closed during the deletion request. If activity or photo cleanup does not finish, the app reports the failure and does not tell you the account was fully deleted. Sign in and choose Delete account again to retry the remaining cleanup.</p>
            <h2>Contact</h2>
            <p>For questions or problems with account deletion, email <a href="mailto:odirilemasemola1@gmail.com">odirilemasemola1@gmail.com</a>.</p>
          </main>
        </body>
        </html>
    """.trimIndent()
}
