@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("SpellCheckingInspection")

package io.github.ladogag.recentwikipediadeaths.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.plus
import androidx.core.net.toUri
import io.github.ladogag.recentwikipediadeaths.R
import io.github.ladogag.recentwikipediadeaths.data.DeathEvent
import io.github.ladogag.recentwikipediadeaths.viewmodel.TimeMode
import io.github.ladogag.recentwikipediadeaths.viewmodel.TrackerViewModel
import java.util.Date

@Composable
private fun screenBackground() = if (isSystemInDarkTheme())
    MaterialTheme.colorScheme.surface
else
    MaterialTheme.colorScheme.surfaceContainer

@Composable
private fun cardBackground() = if (isSystemInDarkTheme())
    MaterialTheme.colorScheme.surfaceContainerHigh
else
    MaterialTheme.colorScheme.surface

@Composable
private fun groupShape(isFirst: Boolean, isLast: Boolean) = MaterialTheme.shapes.extraLarge.copy(
    topStart = if (isFirst) CornerSize(28.dp) else CornerSize(4.dp),
    topEnd = if (isFirst) CornerSize(28.dp) else CornerSize(4.dp),
    bottomStart = if (isLast) CornerSize(28.dp) else CornerSize(4.dp),
    bottomEnd = if (isLast) CornerSize(28.dp) else CornerSize(4.dp)
)

@Composable
fun FeedScreen(viewModel: TrackerViewModel, onOpenSettings: () -> Unit) {
    val events by viewModel.visibleEvents.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val failedLangs by viewModel.failedLangs.collectAsState()
    val successCount by viewModel.successCount.collectAsState()
    val targets by viewModel.targets.collectAsState()

    val showDetails = remember { mutableStateOf(false) }
    if (showDetails.value) ErrorDetailsDialog(failedLangs) { showDetails.value = false }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = screenBackground(),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = screenBackground(),
                    scrolledContainerColor = screenBackground()
                ),
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 8.dp
            ) + WindowInsets.navigationBars.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (loading) item {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }

            if (!loading) {
                val totalCount = targets.size
                when {
                    successCount == 0 -> item {
                        StatusCard(
                            stringResource(R.string.error_total),
                            failedLangs = failedLangs,
                            onShowDetails = { showDetails.value = true },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    failedLangs.isNotEmpty() -> item {
                        StatusCard(
                            stringResource(R.string.error_partial) + "\n" +
                                    stringResource(R.string.error_summary, successCount, totalCount),
                            failedLangs = failedLangs,
                            onShowDetails = { showDetails.value = true },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            itemsIndexed(events, key = { _, e -> e.id }) { index, event ->
                EventCard(
                    event = event,
                    shape = groupShape(
                        isFirst = index == 0,
                        isLast = index == events.lastIndex
                    )
                )
            }

            if (!loading && events.isEmpty()) item {
                Text(
                    stringResource(R.string.empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: TrackerViewModel, onBack: () -> Unit) {
    val selectedLangs by viewModel.selectedLangs.collectAsState()
    val targets by viewModel.targets.collectAsState()
    val timeMode by viewModel.timeMode.collectAsState()
    val hours by viewModel.hours.collectAsState()
    val isLastHours = timeMode is TimeMode.LastHours

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
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
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = screenBackground(),
                    scrolledContainerColor = screenBackground()
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 8.dp
            ) + WindowInsets.navigationBars.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.timeframe),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = groupShape(isFirst = true, isLast = true),
                    colors = CardDefaults.cardColors(containerColor = cardBackground())
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = !isLastHours,
                                onClick = { viewModel.setLiveMode() },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) { Text(stringResource(R.string.live_only)) }

                            SegmentedButton(
                                selected = isLastHours,
                                onClick = { viewModel.setLastHoursMode() },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) { Text(stringResource(R.string.last_hours)) }
                        }

                        AnimatedVisibility(
                            visible = isLastHours,
                            enter = fadeIn(tween(220, easing = LinearOutSlowInEasing)) +
                                    expandVertically(tween(220, easing = LinearOutSlowInEasing)),
                            exit = fadeOut(tween(180, easing = FastOutLinearInEasing)) +
                                    shrinkVertically(tween(180, easing = FastOutLinearInEasing))
                        ) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Slider(
                                    value = hours.toFloat(),
                                    onValueChange = { viewModel.setHours(it.toInt()) },
                                    valueRange = 1f..168f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    stringResource(R.string.hours_value, hours),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.align(Alignment.End)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.wiki_sections),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }

            if (targets.isEmpty()) {
                item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupShape(isFirst = true, isLast = targets.isEmpty()),
                        colors = CardDefaults.cardColors(containerColor = cardBackground())
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(stringResource(R.string.select_all)) },
                            supportingContent = {
                                Text(
                                    stringResource(R.string.langs_selected, selectedLangs.size, targets.size),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = selectedLangs.size == targets.size,
                                    onCheckedChange = { viewModel.setAllLangs(it) }
                                )
                            },
                            modifier = Modifier.clickable {
                                viewModel.setAllLangs(selectedLangs.size != targets.size)
                            }
                        )
                    }
                }

                items(targets.size, key = { i -> targets[i].lang }) { index ->
                    val target = targets[index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = groupShape(isFirst = false, isLast = index == targets.lastIndex),
                        colors = CardDefaults.cardColors(containerColor = cardBackground())
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            headlineContent = { Text(target.displayName) },
                            supportingContent = {
                                Text(target.lang.uppercase(), style = MaterialTheme.typography.labelSmall)
                            },
                            leadingContent = {
                                Checkbox(
                                    checked = target.lang in selectedLangs,
                                    onCheckedChange = { viewModel.toggleLang(target.lang) }
                                )
                            },
                            modifier = Modifier.clickable { viewModel.toggleLang(target.lang) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    text: String,
    failedLangs: List<io.github.ladogag.recentwikipediadeaths.data.ErrorDetail>,
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().then(modifier),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (failedLangs.isNotEmpty()) {
                TextButton(
                    onClick = onShowDetails,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.details))
                }
            }
        }
    }
}

@Composable
private fun ErrorDetailsDialog(
    failedLangs: List<io.github.ladogag.recentwikipediadeaths.data.ErrorDetail>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.error_details_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                failedLangs.forEach { err ->
                    ListItem(
                        headlineContent = { Text(err.lang.uppercase()) },
                        supportingContent = {
                            Text(err.message, style = MaterialTheme.typography.bodySmall)
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        }
    )
}

@Composable
private fun EventCard(event: DeathEvent, shape: Shape) {
    val context = LocalContext.current
    val timeText = remember(event.timestamp) {
        java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT
        ).format(Date(event.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val url = "https://${event.wiki}.wikipedia.org/wiki/" +
                        android.net.Uri.encode(event.title)
                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardBackground())
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        event.wiki.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(timeText, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            val description = event.description ?: event.extract
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3
                )
            }

            if (!event.comment.isNullOrBlank()) {
                Text(
                    text = event.comment,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}