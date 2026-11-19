# MINIMA — Project Development Rules

## Project Objective
Build a personal Android launcher focused on reducing smartphone addiction, unnecessary screen time, habitual app opening and doomscrolling.

This is a personal-use project, not a commercial application.

## Technology
- Kotlin
- Jetpack Compose
- MVVM architecture
- Room Database
- DataStore
- Android UsageStatsManager
- Android PackageManager

## Design Philosophy
- Strict monochrome visual identity.
- Black and white colour palette.
- Typography-focused interface.
- No colourful application icons.
- No unnecessary animations.
- Minimal visual distractions.
- Adaptive interface that reveals information when needed.

## Engineering Rules
1. Follow modern Android development practices.
2. Use modular, maintainable architecture.
3. Never introduce unnecessary dependencies.
4. Keep business logic separate from UI.
5. Handle Android permissions properly.
6. Never assume system permissions have been granted.
7. Provide graceful fallbacks when permissions are unavailable.
8. Test every major feature before implementation is considered complete.
9. Never silently remove existing functionality.
10. Do not implement features that Android does not actually support.

## Development Workflow
1. Understand requirements.
2. Create an implementation plan.
3. Explain architectural decisions.
4. Wait for approval before major implementation.
5. Implement one phase at a time.
6. Build and test after each phase.
7. Report completed functionality and known limitations.

## Product Philosophy
The application should encourage intentional phone usage.

Never use:
- Addictive gamification.
- Streak pressure.
- Engagement notifications.
- Infinite feeds.
- Recommendation algorithms.
- Unnecessary notifications.

The application should reduce attention capture, not create another source of it.