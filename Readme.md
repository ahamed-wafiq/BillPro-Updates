# BillPro Android Application

Official repository for **BillPro** — Modern offline-first Android billing, inventory management, and GitHub in-app release update system.

## Key Features
- **Modern Jetpack Compose UI**: Signature yellow (`#FFC928`), black, and white design system with floating bottom navigation.
- **Inventory Ledger**: Complete stock movement tracking (restocks, sales, returns, wastage, adjustments) with per-item ledger history.
- **GST Invoicing**: Instant invoice generation with CGST/SGST/IGST breakdown, PDF export, and ESC/POS thermal printing.
- **Customer Ledger**: Credit accounts, purchase history, and balance tracking.
- **Cashbook & Payments**: Daily income and expense management with 3-column financial analytics (Revenue, Expenses, Net Profit).
- **Isolated Multi-Tenant Architecture**: Business ID isolation for all local Room database records.
- **GitHub In-App Updater**: Client devices automatically check for and install releases from GitHub Releases with SHA-256 verification.

## Technology Stack
- **Language**: Kotlin 2.0+
- **UI Toolkit**: Jetpack Compose + Material 3
- **Local Database**: Room 2.6+ with schema migrations
- **Architecture**: MVVM with Kotlin Coroutines & Flow
- **Build System**: Gradle 8.9 + Android Gradle Plugin 8.7.3
- **Target SDK**: Android 15 (API 35), Min SDK: Android 8.0 (API 26)
