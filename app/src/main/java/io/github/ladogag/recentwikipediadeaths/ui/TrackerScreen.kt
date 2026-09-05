@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@file:Suppress("SpellCheckingInspection")

package io.github.ladogag.recentwikipediadeaths.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.ladogag.recentwikipediadeaths.R
import io.github.ladogag.recentwikipediadeaths.data.DeathEvent
import io.github.ladogag.recentwikipediadeaths.data.ErrorDetail
import io.github.ladogag.recentwikipediadeaths.viewmodel.TimeMode
import io.github.ladogag.recentwikipediadeaths.viewmodel.TrackerViewModel
import java.util.Date

@Composable
private fun screenBackground(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface
    else MaterialTheme.colorScheme.surfaceContainer

@Composable
private fun cardBackground(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surface

@Composable
private fun groupShape(isFirst: Boolean, isLast: Boolean): Shape =
    MaterialTheme.shapes.extraLarge.copy(
        topStart = if (isFirst) CornerSize(28.dp) else CornerSize(4.dp),
        topEnd = if (isFirst) CornerSize(28.dp) else CornerSize(4.dp),
        bottomStart = if (isLast) CornerSize(28.dp) else CornerSize(4.dp),
        bottomEnd = if (isLast) CornerSize(28.dp) else CornerSize(4.dp),
    )

@Composable
fun FeedScreen(
    viewModel: TrackerViewModel,
    onOpenSettings: () -> Unit,
) {
    val events by viewModel.visibleEvents.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val failedLangs by viewModel.failedLangs.collectAsState()
    val successCount by viewModel.successCount.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val showIndicator = isRefreshing || loading

    val showDetails = remember { mutableStateOf(false) }
    if (showDetails.value) {
        ErrorDetailsDialog(
            failedLangs = failedLangs,
            onDismiss = { showDetails.value = false },
        )
    }

    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        containerColor = screenBackground(),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = screenBackground(),
                    scrolledContainerColor = screenBackground(),
                ),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_settings_24),
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = showIndicator,
            onRefresh = {
                if (topAppBarState.collapsedFraction == 0f) {
                    viewModel.refresh()
                }
            },
            enabled = !loading,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            indicator = {
                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = showIndicator,
                    )
                }
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ) + WindowInsets.navigationBars.asPaddingValues(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!loading) {
                    when {
                        successCount == 0 -> item {
                            StatusCard(
                                text = stringResource(R.string.error_total),
                                failedLangs = failedLangs,
                                onShowDetails = { showDetails.value = true },
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }

                        failedLangs.isNotEmpty() -> item {
                            val attempted = successCount + failedLangs.size
                            StatusCard(
                                text = stringResource(R.string.error_partial) + "\n" +
                                        stringResource(R.string.error_summary, successCount, attempted),
                                failedLangs = failedLangs,
                                onShowDetails = { showDetails.value = true },
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = events,
                    key = { _, event -> event.id },
                ) { index, event ->
                    EventCard(
                        event = event,
                        shape = groupShape(
                            isFirst = index == 0,
                            isLast = index == events.lastIndex,
                        ),
                    )
                }

                if (!loading && events.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: TrackerViewModel,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    val selectedLangs by viewModel.selectedLangs.collectAsState()
    val targets by viewModel.targets.collectAsState()
    val timeMode by viewModel.timeMode.collectAsState()
    val hours by viewModel.hours.collectAsState()
    val isLastHours = timeMode is TimeMode.LastHours

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState(),
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = screenBackground(),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_arrow_back_24),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = screenBackground(),
                    scrolledContainerColor = screenBackground(),
                ),
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_search_24),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 8.dp,
            ) + WindowInsets.navigationBars.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.timeframe),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = groupShape(isFirst = true, isLast = true),
                    colors = CardDefaults.cardColors(containerColor = cardBackground()),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .animateContentSize(),
                    ) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !isLastHours,
                                onClick = { viewModel.setLiveMode() },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            ) {
                                Text(stringResource(R.string.live_only))
                            }

                            SegmentedButton(
                                selected = isLastHours,
                                onClick = { viewModel.setLastHoursMode() },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            ) {
                                Text(stringResource(R.string.last_hours))
                            }
                        }

                        AnimatedVisibility(
                            visible = isLastHours,
                            enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)) +
                                    expandVertically(tween(220, easing = LinearOutSlowInEasing)),
                            exit = fadeOut(tween(180, easing = FastOutLinearInEasing)) +
                                    shrinkVertically(tween(180, easing = FastOutLinearInEasing)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .clipToBounds(),
                            ) {
                                Slider(
                                    value = hours.toFloat(),
                                    onValueChange = { viewModel.setHours(it.toInt()) },
                                    valueRange = 1f..168f,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    text = stringResource(R.string.hours_value, hours),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.align(Alignment.End),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.wiki_sections),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }

            if (targets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupShape(isFirst = true, isLast = false),
                        colors = CardDefaults.cardColors(containerColor = cardBackground()),
                    ) {
                        ListItem(
                            supportingContent = {
                                Text(
                                    text = stringResource(
                                        R.string.langs_selected,
                                        selectedLangs.size,
                                        targets.size,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = {
                                TriStateCheckbox(
                                    state = when {
                                        selectedLangs.isEmpty() -> ToggleableState.Off
                                        selectedLangs.size == targets.size -> ToggleableState.On
                                        else -> ToggleableState.Indeterminate
                                    },
                                    onClick = {
                                        viewModel.setAllLangs(selectedLangs.size != targets.size)
                                    },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                viewModel.setAllLangs(selectedLangs.size != targets.size)
                            },
                        ) {
                            Text(stringResource(R.string.select_all))
                        }
                    }
                }

                itemsIndexed(
                    items = targets,
                    key = { _, target -> target.lang },
                ) { index, target ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupShape(
                            isFirst = false,
                            isLast = index == targets.lastIndex,
                        ),
                        colors = CardDefaults.cardColors(containerColor = cardBackground()),
                    ) {
                        ListItem(
                            supportingContent = {
                                Text(
                                    text = target.lang.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = target.lang in selectedLangs,
                                    onCheckedChange = { viewModel.toggleLang(target.lang) },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { viewModel.toggleLang(target.lang) },
                        ) {
                            Text(target.displayName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSearchScreen(
    viewModel: TrackerViewModel,
    onBack: () -> Unit,
    isActive: Boolean = true,
) {
    val targets by viewModel.targets.collectAsState()
    val selectedLangs by viewModel.selectedLangs.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(isActive) {
        if (isActive) {
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
            searchQuery = ""
        }
    }

    val filteredTargets = remember(targets, searchQuery) {
        if (searchQuery.isBlank()) {
            targets
        } else {
            val q = searchQuery.trim()
            targets.filter { target ->
                target.displayName.contains(q, ignoreCase = true) ||
                        target.englishName.contains(q, ignoreCase = true) ||
                        target.nativeName.contains(q, ignoreCase = true) ||
                        target.lang.equals(q, ignoreCase = true)
            }
        }
    }

    Scaffold(
        containerColor = screenBackground(),
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_arrow_back_24),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.rounded_close_24),
                                contentDescription = null,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = screenBackground(),
                    scrolledContainerColor = screenBackground(),
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 8.dp,
            ) + WindowInsets.navigationBars.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (targets.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredTargets,
                    key = { _, target -> target.lang },
                ) { index, target ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupShape(
                            isFirst = index == 0,
                            isLast = index == filteredTargets.lastIndex,
                        ),
                        colors = CardDefaults.cardColors(containerColor = cardBackground()),
                    ) {
                        ListItem(
                            supportingContent = {
                                Text(
                                    text = target.lang.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = target.lang in selectedLangs,
                                    onCheckedChange = { viewModel.toggleLang(target.lang) },
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { viewModel.toggleLang(target.lang) },
                        ) {
                            Text(target.displayName)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    text: String,
    failedLangs: List<ErrorDetail>,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (failedLangs.isNotEmpty()) {
                TextButton(
                    onClick = onShowDetails,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(R.string.details))
                }
            }
        }
    }
}

@Composable
private fun ErrorDetailsDialog(
    failedLangs: List<ErrorDetail>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = screenBackground(),
        title = { Text(stringResource(R.string.error_details_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                failedLangs.forEachIndexed { index, err ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupShape(
                            isFirst = index == 0,
                            isLast = index == failedLangs.lastIndex,
                        ),
                        color = cardBackground(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = err.lang.uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = err.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.done))
            }
        },
    )
}

@Composable
private fun EventCard(
    event: DeathEvent,
    shape: Shape,
) {
    val context = LocalContext.current
    val timeText = remember(event.timestamp) {
        java.text.DateFormat
            .getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
            .format(Date(event.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable {
                val url = "https://${event.wiki}.wikipedia.org/wiki/" +
                        android.net.Uri.encode(event.title)
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardBackground()),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = event.wiki.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val description = event.description ?: event.extract
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }

            if (!event.comment.isNullOrBlank()) {
                Text(
                    text = event.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}