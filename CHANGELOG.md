# Changelog

## [v2.4.0] - 2026-01-10

### 🚀 New Features & Improvements
- **Startlingly Fast PDF Extraction**: Text extraction from PDFs is now robust and reliable. Added intelligent white-background rendering for optimal ML Kit OCR accuracy.
- **Smart Rate Limiting (2-Request Model)**:
    - **Extract + Translate**: You now get 2 consecutive AI requests (e.g., Extract then immediately Translate) before a 60-second cooldown.
    - **Visual Countdown**: A sleek modal sheet appears during cooldowns with a live progress indicator.
    - **Unlimited Free OCR**: Standard ML Kit text extraction (Camera/Gallery/PDF) is now strictly **unlimited** and offline-capable (no translate button for pure OCR results).
- **Offline Mode Polish**: The "Translate" button now intelligently hides itself when the device is offline to prevent error states.

### 🐛 Bug Fixes
- **PDF Previews**: Fixed an issue where opening a PDF wouldn't show a thumbnail preview in the results screen.
- **Rate Limit Conflict**: Resolved a conflict where the AI service's internal rate limit was blocking the valid second request of the new rate limit model.
- **UI Cleanups**: Removed distracting "rotating tips" from the AI Floating Action Button for a cleaner look.

### 🔧 Under the Hood
- Updated `PdfRendererHelper` with better error handling and per-page exception safety.
- Migrated rate limiting logic fully to `ScanViewModel` for centralized control.
- Bumped `minSdk` compatibility checks and dependencies.
