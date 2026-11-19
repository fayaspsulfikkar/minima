# Architectural Blueprint & Technical Specifications

This document outlines the architectural patterns, state pipelines, data persistence models, and system-level integrations in Minima.

---

## 1. Architectural Pattern: Clean Architecture + MVVM

Minima adopts Clean Architecture with strict boundary enforcement and Unidirectional Data Flow (UDF).

```
┌────────────────────────────────────────────────────────┐
│                   Presentation Layer                   │
│   Jetpack Compose (UI)  ◄───►  HomeViewModel (MVI/MVVM) │
└───────────────────────────┬────────────────────────────┘
                            │ (invokes)
┌───────────────────────────▼────────────────────────────┐
│                      Domain Layer                      │
│   Use Cases (Single Responsibility, Pure Kotlin)       │
│   - GetInstalledAppsUseCase                            │
│   - SyncUsageDataUseCase                               │
│   - DetectRepeatedOpeningUseCase                       │
│   - LaunchAppUseCase                                   │
└───────────────────────────┬────────────────────────────┘
                            │ (interfaces implemented by)
┌───────────────────────────▼────────────────────────────┐
│                       Data Layer                       │
│   Repository Implementations (Offline-First Cache)      │
│   ├── Room SQLite Database                             │
│   ├── DataStore Preferences (Key-Value)                │
│   └── Android System Data Sources (PackageManager/Stats)│
└────────────────────────────────────────────────────────┘
```

### Layer Rules:
1. **Presentation Layer**: Pure UI rendering and UI state emission. It holds no business logic, direct database references, or system manager references. State is modeled via immutable Kotlin data classes and exposed as `StateFlow`.
2. **Domain Layer**: Contains pure Kotlin models, repository interfaces, and use cases. It has no Android framework dependencies (e.g., no `Context`, `Intent`, or `Bundle`).
3. **Data Layer**: Implements repository contracts. Coordinates between system services (`UsageStatsManager`, `PackageManager`), local SQLite storage (`Room`), and reactive preferences (`DataStore`).

---

## 2. Unidirectional Data Flow & Lifecycle Management

State flows in a single direction from the data layer up to the Compose UI:

```
[System Events / DB Updates] ──► [Repository Flow] ──► [UseCase] ──► [HomeViewModel] ──► [StateFlow<HomeUiState>] ──► [Compose Recomposition]
```

### Flow Subscriptions & Memory Safety
To avoid unnecessary CPU wakeups or database observation when the user is not actively on the launcher screen, state flows are exposed with:
```kotlin
stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000L),
    initialValue = HomeUiState.Loading
)
```
The 5-second buffer prevents subscription churn during configuration changes (e.g., orientation switches or short transitions).

---

## 3. Data Persistence & Schema Design

Minima uses **Room 2.6.1** with SQLite, compiled via **KSP (Kotlin Symbol Processing)**.

### Entities & Indices
1. **`DailyUsageRecordEntity`**
   - Key: `packageName` + `date`
   - Fields: `packageName`, `appName`, `totalForegroundTimeMs`, `date`, `lastUpdatedTimestamp`
   - Index: `Index(value = ["date"])` for instant daily usage aggregations without full table scans.

2. **`LaunchAttemptEntity`**
   - Key: Auto-generated primary key ID
   - Fields: `packageName`, `appName`, `attemptTimestamp`, `wasCompleted`, `delaySeconds`
   - Index: `Index(value = ["packageName", "attemptTimestamp"])` enabling efficient lookups for repeated open calculations within rolling time windows.

### Reactive Settings: DataStore Preferences
Replaces legacy `SharedPreferences` with transactional, asynchronous coroutine streams:
- `KEY_DEFAULT_DELAY_SECONDS`: Integer setting for pre-launch cognitive delay.
- `KEY_REPEAT_WINDOW_MINUTES`: Rolling window duration for detecting rapid re-opens.
- `KEY_REPEAT_THRESHOLD_COUNT`: Number of launches required to trigger the reflection alert.

---

## 4. Android System Subsystem Integrations

### UsageStatsManager Integration
- Queries `UsageStatsManager.queryUsageStats()` across daily calendar boundaries (`Calendar.getInstance()` truncated to `00:00:00.000` until current timestamp).
- **Graceful Fallback**: If `PACKAGE_USAGE_STATS` permission has not been granted by the user via Special App Access, the app logs a graceful warning and renders a non-intrusive permission prompt rather than throwing a `SecurityException` or crashing.

### PackageManager & Dynamic App Discovery
- Queries intent handlers for `Intent.ACTION_MAIN` and `Intent.CATEGORY_LAUNCHER`.
- Excludes Minima's own package name (`BuildConfig.APPLICATION_ID`) to prevent recursive home launcher listings.
- Observes real-time system broadcasts via `PackageChangedReceiver` (`ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED`, `ACTION_PACKAGE_REPLACED`) to refresh the app drawer without requiring a full launcher restart.

### WorkManager Periodic Synchronization
- `UsageSyncWorker` scheduled periodically to recalculate daily screen-time metrics in the background.
- Employs exponential backoff policy and runs with battery-friendly constraints.

---

## 5. Security & Threat Modeling

Minima is explicitly engineered to eliminate network vulnerability surfaces:
- **No Internet Permission**: The manifest contains zero network permissions. Network calls are physically impossible at the OS permission level.
- **Component Exposure**: The launcher activity `MainActivity` is the sole exported activity, strictly configured with `android.intent.category.HOME`. All receivers and providers are either unexported or protected by system-level intent filters.
