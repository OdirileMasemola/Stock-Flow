<p align="center">
  <img src="docs/images/stockflow_logo.png.png" width="180" alt="StockFlow Logo">
</p>

# StockFlow

### Inventory and Business Management for South African Small Retail Businesses

StockFlow is a full-stack inventory management and Point of Sale (POS) system designed for small businesses in South Africa. It provides a professional mobile solution to track stock, manage sales, and monitor business health in real time.

[![StockFlow CI](https://github.com/OdirileMasemola/Stock-Flow/actions/workflows/ci.yml/badge.svg)](https://github.com/OdirileMasemola/Stock-Flow/actions/workflows/ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-purple.svg)
![Android](https://img.shields.io/badge/Android-SDK%2035-green.svg)
![Ktor](https://img.shields.io/badge/Ktor-3.0-orange.svg)
![Supabase](https://img.shields.io/badge/Supabase-PostgreSQL-blue.svg)

---

## Table of Contents

- [Overview](#overview)
- [Objectives](#objectives)
- [Current Status](#current-status)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [App Screenshots](#app-screenshots)
- [System Architecture](#system-architecture)
- [Database Schema](#database-schema)
- [Authentication and Security](#authentication-and-security)
- [Testing and CI](#testing-and-ci)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Production Deployment](#production-deployment)
- [Future Roadmap](#future-roadmap)
- [Known Limitations](#known-limitations)
- [Development Practices](#development-practices)
- [AI-Assisted Development](#ai-assisted-development)
- [Author](#author)
- [License](#license)

---

## Overview

Small retail businesses often struggle with manual stock tracking and disjointed sales records. StockFlow bridges this gap with an integrated Android application and high-performance backend. Shop owners can manage inventory via barcode scanning, process sales through a dedicated POS interface, and generate performance reports — all from a mobile device.

---

## Objectives

- **Modernize inventory**: Replace paper-based tracking with a digital, searchable database.
- **Streamline sales**: Provide a fast POS interface for daily transactions.
- **Data-driven insights**: Use real-time dashboards and PDF reports to monitor growth.
- **Accessibility**: Make professional-grade tools available to small businesses through affordable mobile technology.

---

## Current Status

StockFlow is in an advanced implementation phase, with an MVVM architecture and production backend in place.

- **Implemented**: Core inventory, POS, auth (JWT + Google), dashboard, reports, scanning, and production deployment.
- **Planned (Part 3)**: Offline synchronization, cloud storage, and push notifications.

---

## Features

### Authentication and Identity

- **Secure registration**: Signup with role selection (Owner, Staff, Supplier).
- **Email/password login**: Standard authentication secured with BCrypt.
- **Google Sign-In**: One-tap authentication using Firebase and Google Identity Services.
- **Role-based access**: Specialized views and permissions by user role.

### Business Intelligence

- **Live dashboard**: Real-time summary of total sales, inventory value, and active suppliers.
- **Weekly sales chart**: Sales trends over the last 7 days.
- **Low-stock management**: Tracking and alerts when products reach critical levels.
- **Professional reports**: Export performance data to PDF.

### Inventory and Operations

- **Product management**: CRUD for products with image support.
- **Barcode/QR scanning**: Camera-based lookup using Google ML Kit.
- **POS system**: Cart-based checkout for rapid transactions.
- **Suppliers and purchasing**: Vendor relationships and purchase orders from draft to receipt.

### Personalization and Settings

- **Profile management**: Update user details and upload profile avatars.
- **Business info**: Shop location, contact details, and branding.
- **Theme selection**: Light, Dark, and system-adaptive themes.
- **Language support**: English base, with planned support for IsiZulu, Sesotho, and Setswana.

---

## Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Mobile (Android)** | Kotlin, Jetpack Compose (UI), MVVM, Repository pattern |
| **Networking** | Retrofit 2, OkHttp 4, Gson |
| **Backend (Ktor)** | Ktor 3.x (Netty), Kotlin JVM, kotlinx.serialization |
| **Persistence** | PostgreSQL (Supabase), Exposed ORM, HikariCP |
| **Authentication** | JWT, BCrypt, Firebase Auth, Google Identity |
| **Computer Vision** | Google ML Kit (barcode scanning), CameraX |
| **Infrastructure** | Docker, Render, Gradle 9.x, Java 21 |
| **DevOps** | GitHub Actions (CI), Git |

---

## App Screenshots

Screenshots will be added here. Drop your images into `docs/images/` and uncomment the block below.

<!--
<p align="center">
  <img src="docs/images/screen-onboarding.png" width="250" alt="Onboarding">
  <img src="docs/images/screen-dashboard.png" width="250" alt="Dashboard">
  <img src="docs/images/screen-pos.png" width="250" alt="POS">
</p>
-->

| Placeholder | Suggested file |
| :--- | :--- |
| Onboarding / welcome | `docs/images/screen-onboarding.png` |
| Dashboard | `docs/images/screen-dashboard.png` |
| POS / inventory | `docs/images/screen-pos.png` |

---

## System Architecture

StockFlow uses a client-server architecture designed for scalability and security.

<p align="center">
  <img src="docs/images/Stock-Flow System Architechture.png" width="600" alt="StockFlow Architecture">
</p>

1. **Android app**: Primary interface; communicates with the backend via a REST API.
2. **Ktor backend**: Business logic, JWT authentication middleware, and image processing.
3. **PostgreSQL (Supabase)**: Managed relational database for persistent storage.

---

## Database Schema

The relational schema includes 10 core tables:

- **Users and Roles**: Identity and permissions.
- **Businesses**: Organization-level metadata.
- **Products and Categories**: Inventory data and relationships.
- **Sales and SaleItems**: Revenue and transaction history.
- **Suppliers and PurchaseOrders**: Supply chain management.

---

## Authentication and Security

- **JWT security**: Sensitive API endpoints are protected by JWT authentication.
- **Password hashing**: Passwords are hashed with BCrypt and never stored in plain text.
- **Google OAuth**: Uses Google's authentication infrastructure for identity verification.
- **Environment configuration**: Secrets are managed via environment variables and `.env` files (never committed).

---

## Testing and CI

Continuous Integration runs via GitHub Actions (`.github/workflows/ci.yml`):

- **Backend validation**: Builds the Ktor module and packages the Shadow JAR.
- **Android validation**: Runs unit tests and assembles the debug APK.
- **Code integrity**: Every push to `master` must meet project quality standards.

---

## Project Structure

```text
StockFlow/
|-- app/                  # Android application (Kotlin)
|   |-- src/main/java/    # UI, ViewModel, data layers
|   `-- src/main/res/     # Layouts, drawables, values
|-- backend/              # Ktor REST API (Kotlin)
|   |-- src/main/kotlin/  # Routes, services, repositories
|   `-- Dockerfile        # Production container configuration
|-- docs/                 # Documentation and architecture diagrams
`-- gradle/               # Version catalogs and global configuration
```

---

## Getting Started

### Requirements

- Android Studio (Ladybug or newer)
- JDK 21
- PostgreSQL / Supabase account (for backend)

### 1. Clone the repository

```bash
git clone https://github.com/OdirileMasemola/Stock-Flow.git
cd Stock-Flow
```

### 2. Backend setup

Create a `.env.local` file in the `backend` directory (or root) with:

```env
DB_URL=jdbc:postgresql://your-db-host:5432/postgres
DB_USER=your-user
DB_PASSWORD=your-password
JWT_SECRET=your-secure-secret
```

Run the backend:

```bash
./gradlew :backend:run
```

### 3. Android setup

1. Open the project in Android Studio.
2. Sync Gradle.
3. Run the `app` module on an emulator or physical device.

---

## Production Deployment

StockFlow is deployed using a containerized approach.

- **API base URL**: `https://stock-flow-trbq.onrender.com`
- **Health check**: `https://stock-flow-trbq.onrender.com/api/health`
- **Platform**: Render (web service) and Supabase (managed database).

---

## Future Roadmap

- **Offline first**: Local SQLite caching for operations without internet.
- **Push notifications**: Alerts for low stock and received orders.
- **Cloud storage**: Firebase Storage or AWS S3 for product images.
- **Multilingual**: Native support for IsiZulu, Sesotho, and Setswana.
- **Play Store**: Final optimization and signing for public release.

---

## Known Limitations

- **Connection dependency**: Real-time sales require an active internet connection (offline mode planned).
- **Image storage**: Currently uses local persistent volumes; migration to cloud storage is pending.

---

## Development Practices

- **Git flow**: Descriptive commits and branch management for feature tracking.
- **Clean code**: Repository pattern to decouple UI from data sources.
- **Validation**: Input validation on both client and server.
- **Secure config**: Strict use of `.gitignore` for sensitive credentials.

---

## Author

**Odirile Masemola**

- Diploma in IT — Software Development
- GitHub: [OdirileMasemola](https://github.com/OdirileMasemola)
- LinkedIn: [Odirile Masemola](https://www.linkedin.com/in/odirile-masemola/)

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
