package com.utkarsh.todo.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.component.NavBarItem
import com.utkarsh.todo.core.designsystem.component.OfflineBanner
import com.utkarsh.todo.core.offline.OfflineViewModel
import com.utkarsh.todo.core.offline.ProvideReconnected
import kotlinx.coroutines.delay
import com.utkarsh.todo.core.designsystem.component.PaperNavBar
import com.utkarsh.todo.core.designsystem.icon.PaperIcons
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.feature.calendar.ui.CalendarRoute
import com.utkarsh.todo.feature.customize.ui.CustomizeRoute
import com.utkarsh.todo.feature.insights.ui.InsightsRoute
import com.utkarsh.todo.feature.projects.ui.ProjectsRoute
import com.utkarsh.todo.feature.focus.ui.FocusRoute
import com.utkarsh.todo.feature.create.ui.CreateRoute
import com.utkarsh.todo.feature.search.ui.SearchRoute
import com.utkarsh.todo.feature.reminders.ui.AlertPermissionPrompt
import com.utkarsh.todo.feature.reminders.ui.DueSoonRoute
import com.utkarsh.todo.feature.assistant.ui.AssistantRoute
import com.utkarsh.todo.feature.braindump.ui.BrainDumpRoute
import com.utkarsh.todo.feature.memory.ui.MemoryRoute
import com.utkarsh.todo.feature.taskdetail.ui.TaskDetailRoute
import com.utkarsh.todo.feature.today.ui.TodayRoute
import kotlin.reflect.KClass

private data class TopLevel(val key: String, val label: String, val route: Destination, val type: KClass<out Destination>, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel("today", "Today", Destination.Today, Destination.Today::class, PaperIcons.Today),
    TopLevel("calendar", "Calendar", Destination.Calendar, Destination.Calendar::class, PaperIcons.Calendar),
    TopLevel("timer", "Timer", Destination.Timer, Destination.Timer::class, PaperIcons.Timer),
    TopLevel("projects", "Projects", Destination.Projects, Destination.Projects::class, PaperIcons.Projects),
    TopLevel("insights", "Insights", Destination.Insights, Destination.Insights::class, PaperIcons.Insights),
)

/** App shell: bottom [PaperNavBar] plus the navigation graph. */
@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    val offlineViewModel: OfflineViewModel = hiltViewModel()
    val offline by offlineViewModel.offline.collectAsStateWithLifecycle()
    // While offline, ask the server every so often whether it is back.
    LaunchedEffect(offline) { while (offline) { delay(RECHECK_MS); offlineViewModel.recheck() } }
    val entry by nav.currentBackStackEntryAsState()
    val selected = topLevel.firstOrNull { t -> entry?.destination?.hasRoute(t.type) == true }?.key

    ProvideReconnected(offlineViewModel.reconnected) {
    Column(modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Destination.Today, modifier = Modifier.weight(1f)) {
            composable<Destination.Today> {
                TodayRoute(
                    onOpenTask = { nav.navigate(Destination.TaskDetail(it)) },
                    onCustomize = { nav.navigate(Destination.Customize) },
                    onAssistant = { nav.navigate(Destination.Assistant) },
                    onBrainDump = { nav.navigate(Destination.BrainDump) },
                    onDueSoon = { nav.navigate(Destination.DueSoon) },
                    onSearch = { nav.navigate(Destination.Search) },
                    onAddTask = { nav.navigate(Destination.Create()) },
                )
            }
            composable<Destination.Calendar> {
                CalendarRoute(
                    onOpenTask = { nav.navigate(Destination.TaskDetail(it)) },
                    onCreate = { date, time ->
                        nav.navigate(
                            if (time == null) Destination.Create("EVENT", epochDay = date.toEpochDay())
                            else Destination.Create("EVENT", date.atTime(time).atZone(java.time.ZoneId.systemDefault()).toEpochSecond()),
                        )
                    },
                    onEditEvent = { nav.navigate(Destination.Create("EVENT", eventId = it)) },
                )
            }
            composable<Destination.DueSoon> { DueSoonRoute() }
            composable<Destination.Insights> { InsightsRoute() }
            composable<Destination.Timer> { FocusRoute(onBack = null) }
            composable<Destination.Projects> {
                ProjectsRoute(onOpenTask = { nav.navigate(Destination.TaskDetail(it)) })
            }
            composable<Destination.Search> {
                SearchRoute(onBack = { nav.popBackStack() }, onOpenTask = { nav.navigate(Destination.TaskDetail(it)) })
            }
            composable<Destination.BrainDump> { BrainDumpRoute(onBack = { nav.popBackStack() }) }
            composable<Destination.Assistant> { AssistantRoute(onBack = { nav.popBackStack() }, onOpenMemory = { nav.navigate(Destination.Memory) }) }
            composable<Destination.Memory> { MemoryRoute(onBack = { nav.popBackStack() }) }
            composable<Destination.Customize> { CustomizeRoute(onBack = { nav.popBackStack() }) }
            composable<Destination.TaskDetail> { entry ->
                TaskDetailRoute(onBack = { nav.popBackStack() }, onFocus = { nav.navigate(Destination.Focus(entry.toRoute<Destination.TaskDetail>().taskId)) })
            }
            composable<Destination.Create> { CreateRoute(onClose = { nav.popBackStack() }) }
            composable<Destination.Focus> { FocusRoute(onBack = { nav.popBackStack() }) }
        }
        if (offline) OfflineBanner(onRetry = offlineViewModel::recheck)
        AlertPermissionPrompt()
        if (selected != null) {
            PaperNavBar(
                items = topLevel.map { NavBarItem(it.key, it.label, it.icon) },
                selectedKey = selected,
                onSelect = { key ->
                    val target = topLevel.first { it.key == key }.route
                    nav.navigate(target) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAdd = { nav.navigate(Destination.Create()) },
            )
        }
    }
    }
}

private const val RECHECK_MS = 15_000L

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(name, style = Paper.type.display, color = Paper.colors.muted)
    }
}
