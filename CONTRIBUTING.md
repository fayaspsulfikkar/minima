# Contributing to Minima

Thank you for your interest in contributing to Minima. This project is built with strict architectural and behavioral principles to help users reclaim their attention through intentional Android computing.

---

## Core Engineering Principles

Before contributing, please review our core architectural rules:

1. **Strict Monochrome**: All UI must be monochrome (pure black `#000000` and pure white `#FFFFFF`). Never introduce colorful icons, gradients, or decorative accents.
2. **Zero Internet Requirement**: Minima operates completely offline. Never request `android.permission.INTERNET` or introduce analytics/tracking libraries.
3. **No Addictive Feedback Loops**: Avoid gamification, streak counts, engagement prompts, or infinite feeds. The UI should encourage intentionality, not attention capture.
4. **Clean Architecture & UDF**: Maintain clear boundaries between UI (Jetpack Compose), Domain (UseCases), and Data (Repositories, Room, DataStore).
5. **Defensive Permission Handling**: Never assume system permissions (`PACKAGE_USAGE_STATS`) are granted; provide seamless fallbacks.

---

## Development Setup

### Prerequisites
- JDK 17 (OpenJDK or Temurin recommended)
- Android Studio Iguana (2023.2.1) or newer
- Android SDK 34 (minSdk 26, targetSdk 34)

### Building and Testing from CLI
```bash
# Verify unit tests
./gradlew testDebugUnitTest

# Run Android Lint
./gradlew lintDebug

# Build debug APK
./gradlew assembleDebug
```

---

## Branching & Commit Guidelines

### Git Branching Model
- `main`: Production-stable branch.
- Feature branches: `feature/<feature-name>`
- Bugfix branches: `fix/<bug-description>`
- Refactor branches: `refactor/<scope>`

### Conventional Commit Messages
Commits should adhere to the Conventional Commits specification:
- `feat(scope): add intentional app launch delay countdown`
- `fix(usage): handle midnight rollover in UsageStats query window`
- `test(domain): add unit test for repeated open threshold calculation`
- `docs(readme): expand architecture system diagram`
- `refactor(data): optimize Room query indexing for launch attempts`

---

## Pull Request Checklist

Before submitting a Pull Request:
1. Ensure all unit tests pass: `./gradlew testDebugUnitTest`.
2. Ensure lint checks pass: `./gradlew lintDebug`.
3. Verify that changes follow the monochrome, non-gamified design philosophy.
4. Keep PRs focused on a single logical change.
