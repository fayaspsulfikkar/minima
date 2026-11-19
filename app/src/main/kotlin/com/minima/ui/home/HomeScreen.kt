package com.minima.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.minima.R
import com.minima.domain.model.AppInfo
import com.minima.domain.model.AppOpenDelay
import com.minima.domain.model.AppUsageItem
import com.minima.domain.model.DailyUsage
import com.minima.domain.model.RepeatOpenAlert
import com.minima.ui.theme.GreyDark
import com.minima.ui.theme.GreyLight
import com.minima.ui.theme.GreyMedium
import com.minima.ui.theme.PureBlack
import com.minima.ui.theme.PureWhite

/**
 * Stateful entry point for the MINIMA Home Screen.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    // Launch the clock ticker tied to this Composable's lifecycle scope
    LaunchedEffect(Unit) {
        viewModel.startClockTicker(this)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreenContent(
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onClearSearch = viewModel::clearSearchQuery,
        onAppClick = viewModel::launchApp,
        onDismissError = viewModel::clearErrorMessage,
        onGrantPermission = viewModel::openUsageAccessSettings,
        onSelectDelay = viewModel::setAppOpenDelay,
        onCancelCountdown = viewModel::cancelCountdown,
        onDismissRepeatAlert = viewModel::dismissRepeatAlert,
        modifier = modifier
    )
}

/**
 * Stateless Home Screen content composable.
 */
@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onAppClick: (String) -> Unit,
    onDismissError: () -> Unit,
    onGrantPermission: () -> Unit,
    onSelectDelay: (AppOpenDelay) -> Unit = {},
    onCancelCountdown: () -> Unit = {},
    onDismissRepeatAlert: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (uiState.activeCountdown != null) {
        CountdownScreen(
            appLabel = uiState.activeCountdown.appLabel,
            remainingSeconds = uiState.activeCountdown.remainingSeconds,
            onCancel = onCancelCountdown,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(PureBlack)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                // Top Section: Minimal Brand Identifier + Delay Setting
                MinimalHeader(
                    appName = uiState.appName,
                    selectedDelay = uiState.appOpenDelay,
                    onSelectDelay = onSelectDelay
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Dashboard: Digital Clock, Date & Live Screen Time Indicator
                MainDashboard(
                    currentTime = uiState.currentTime,
                    currentDate = uiState.currentDate,
                    usageState = uiState.usageState,
                    screenTime = uiState.screenTimeDisplay,
                    onGrantPermission = onGrantPermission
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Repeat-open awareness alert (Phase 7)
                if (uiState.repeatOpenAlert != null) {
                    RepeatOpenAlertBanner(
                        alert = uiState.repeatOpenAlert,
                        onDismiss = onDismissRepeatAlert
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Minimalist Search Bar
                MinimalSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = onSearchQueryChanged,
                    onClear = onClearSearch
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Applications List
                ApplicationsList(
                    apps = uiState.filteredApps,
                    searchQuery = uiState.searchQuery,
                    onAppClick = onAppClick,
                    modifier = Modifier.weight(1f)
                )

                // Transient error message if an app failed to launch
                if (uiState.errorMessage != null) {
                    ErrorMessageBanner(
                        message = uiState.errorMessage,
                        onDismiss = onDismissError
                    )
                }
            }
        }
    }
}

/**
 * Displays the minimal launcher identity and quick intentional delay selector at the top.
 */
@Composable
private fun MinimalHeader(
    appName: String,
    selectedDelay: AppOpenDelay,
    onSelectDelay: (AppOpenDelay) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = appName,
            style = MaterialTheme.typography.labelSmall,
            color = GreyMedium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.delay_setting_label),
                style = MaterialTheme.typography.labelSmall,
                color = GreyDark
            )
            AppOpenDelay.entries.forEach { delayOption ->
                val isSelected = delayOption == selectedDelay
                Text(
                    text = delayOption.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) PureWhite else GreyDark,
                    modifier = Modifier
                        .clickable { onSelectDelay(delayOption) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/**
 * Primary focus of the home screen: clock, date, and truthful screen-time indicator.
 */
@Composable
private fun MainDashboard(
    currentTime: String,
    currentDate: String,
    usageState: DailyUsage,
    screenTime: String,
    onGrantPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // Large digital clock
        Text(
            text = currentTime,
            style = MaterialTheme.typography.displayLarge,
            color = PureWhite
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Current date and day
        Text(
            text = currentDate,
            style = MaterialTheme.typography.bodyLarge,
            color = GreyLight
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Screen-time section header
        Text(
            text = stringResource(R.string.screen_time_label),
            style = MaterialTheme.typography.labelSmall,
            color = GreyMedium
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Screen-time value or permission prompt
        when (usageState) {
            is DailyUsage.Loaded -> {
                Text(
                    text = screenTime,
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureWhite
                )
            }
            is DailyUsage.PermissionDenied -> {
                Column(
                    modifier = Modifier.clickable(onClick = onGrantPermission)
                ) {
                    Text(
                        text = stringResource(R.string.usage_permission_required),
                        style = MaterialTheme.typography.headlineMedium,
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.usage_permission_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GreyLight
                    )
                }
            }
            is DailyUsage.Unavailable -> {
                Column {
                    Text(
                        text = stringResource(R.string.usage_data_unavailable),
                        style = MaterialTheme.typography.headlineMedium,
                        color = GreyMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.usage_data_unavailable_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GreyDark
                    )
                }
            }
        }
    }
}

/**
 * Minimalist monochrome search input.
 */
@Composable
private fun MinimalSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = PureWhite),
            cursorBrush = SolidColor(PureWhite),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = GreyMedium
                        )
                    }
                    innerTextField()
                }
            }
        )

        if (query.isNotEmpty()) {
            Text(
                text = stringResource(R.string.clear_search),
                style = MaterialTheme.typography.labelSmall,
                color = GreyLight,
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * Alphabetical, text-based list of installed applications paired with usage insights.
 */
@Composable
private fun ApplicationsList(
    apps: List<AppUsageItem>,
    searchQuery: String,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (apps.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopStart
        ) {
            val emptyMessage = if (searchQuery.isNotEmpty()) {
                stringResource(R.string.no_apps_found)
            } else {
                stringResource(R.string.pinned_apps_hint)
            }

            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = GreyMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth()
        ) {
            items(
                items = apps,
                key = { it.packageName }
            ) { app ->
                AppListItem(
                    app = app,
                    onClick = { onAppClick(app.packageName) }
                )
            }
        }
    }
}

/**
 * Single text-only application item in the launcher list, displaying
 * the app name on the left and daily usage insight (duration + opens) on the right.
 */
@Composable
private fun AppListItem(
    app: AppUsageItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            color = PureWhite,
            modifier = Modifier.weight(1f, fill = false)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = app.usageDisplay,
            style = MaterialTheme.typography.bodyMedium,
            color = GreyMedium
        )
    }
}

/**
 * Minimal error notification banner displayed when an app cannot be opened.
 */
@Composable
private fun ErrorMessageBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onDismiss)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = GreyLight
        )
        Text(
            text = stringResource(R.string.clear_search),
            style = MaterialTheme.typography.labelSmall,
            color = GreyDark,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

/**
 * Minimal monochrome awareness banner informing the user of repeated app openings.
 */
@Composable
private fun RepeatOpenAlertBanner(
    alert: RepeatOpenAlert,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                R.string.repeat_open_alert_format,
                alert.appLabel,
                alert.attemptCount,
                alert.windowMinutes
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = GreyLight,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.dismiss_alert),
            style = MaterialTheme.typography.labelSmall,
            color = GreyDark,
            modifier = Modifier
                .clickable(onClick = onDismiss)
                .padding(vertical = 4.dp)
        )
    }
}
