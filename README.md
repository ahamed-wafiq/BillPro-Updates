# BillPro — Android billing starter

BillPro is an original Android Studio starter project for an offline-first billing app. It is not affiliated with Billculator or any other billing app.

## Included
- Kotlin + Jetpack Compose dark UI
- Room entities and DAO scaffolding for products, customers, invoices, invoice lines, and cashbook entries
- GST calculation helper for intra-state CGST/SGST and inter-state IGST
- Basic dashboard with sample cards
- Bluetooth ESC/POS printer helper scaffold
- Cloud-sync interface placeholder

## Open and build
1. Install Android Studio (a recent stable version).
2. Open this `BillPro` folder.
3. Allow Gradle sync and install the Android SDK requested by Android Studio.
4. Run the `app` configuration on an Android device or emulator.

This starter has not been compiled or tested in this environment. Resolve any Gradle/SDK issues Android Studio reports before use.

## Important production work still needed
- Complete create/edit screens and connect them to Room repositories.
- Add database migrations and robust backup/restore.
- Implement printer discovery, runtime permissions, and test against your exact printer model.
- Choose and implement a cloud backend (for example Firebase or your own API), authentication, conflict resolution, and encryption.
- Verify GST invoice requirements with a qualified tax professional.
- Add automated tests, accessibility checks, and release signing.

## GST note
The sample calculator assumes prices are tax-exclusive and takes a caller-provided GST percentage. Tax treatment can differ by product, transaction, and legal circumstances. Do not rely on it as tax advice.
