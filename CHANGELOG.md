# Changelog

All notable changes to this project will be documented in this file.

## [1.1.0] - 2025-12-28

### Changed
- **Settings UI**: Refactored Settings from a dialog to a dedicated full-screen experience with smooth transitions.
- **Default Configuration**: All supported OCR languages are now enabled by default for new installations.
- **Navigation**: Improved back navigation logic across Camera, History, and Barcode Scanner screens.

### Fixed
- **History Flow**: Resolved an issue where opening a history item would trigger an automatic re-scan.
- **Build Configuration**: Enabled resource shrinking (`isShrinkResources`) and code minification for optimized release builds.
- **Git Repository**: Fixed push errors by removing accidental large file commits (APKs).
