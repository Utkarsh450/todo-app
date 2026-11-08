package com.utkarsh.todo.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.utkarsh.todo.core.designsystem.component.NavBarItem
import com.utkarsh.todo.core.designsystem.component.PaperNavBar
import com.utkarsh.todo.core.designsystem.icon.PaperIcons
import com.utkarsh.todo.core.designsystem.theme.Paper
import com.utkarsh.todo.feature.calendar.ui.CalendarRoute
import com.utkarsh.todo.feature.customize.ui.CustomizeRoute
import com.utkarsh.todo.feature.insights.ui.InsightsRoute
import com.utkarsh.todo.feature.quickadd.ui.QuickAddSheet
import com.utkarsh.todo.feature.reminders.ui.RemindersRoute
import com.utkarsh.todo.feature.taskdetail.ui.TaskDetailRoute
import com.utkarsh.todo.feature.today.ui.TodayRoute
import kotlin.reflect.KClass

private data class TopLevel(val key: String, val label: String, val route: Destination, val type: KClass<out Destination>, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel("today", "Today", Destination.Today, Destination.Today::class, PaperIcons.Today),
    TopLevel("calendar", "Calendar", Destination.Calendar, Destination.Calendar::class, PaperIcons.Calendar),
    TopLevel("reminders", "Reminders", Destination.Reminders, Destination.Reminders::class, PaperIcons.Reminders),
    TopLevel("insights", "Insights", Destination.Insights, Destination.Insights::class, PaperIcons.Insights),
)

/** App shell: bottom [PaperNavBar] plus the navigation graph. */
@Composable
fun AppNavHost(modifier: Modifier = Modifier) {
    val nav = rememberNavController()
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    val entry by nav.currentBackStackEntryAsState()
    val selected = topLevel.firstOrNull { t -> entry?.destination?.hasRoute(t.type) == true }?.key

    Column(modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Destination.Today, modifier = Modifier.weight(1f)) {
            composable<Destination.Today> {
                TodayRoute(
                    onOpenTask = { nav.navigate(Destination.TaskDetail(it)) },
                    onCustomize = { nav.navigate(Destination.Customize) },
                )
            }
            composable<Destination.Calendar> { CalendarRoute() }
            composable<Destination.Reminders> { RemindersRoute() }
            composable<Destination.Insights> { InsightsRoute() }
            composable<Destination.Customize> { CustomizeRoute(onBack = { nav.popBackStack() }) }
            composable<Destination.TaskDetail> { TaskDetailRoute(onBack = { nav.popBackStack() }) }
        }
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
                onAdd = { showQuickAdd = true },
            )
        }
    }
    if (showQuickAdd) QuickAddSheet(onDismiss = { showQuickAdd = false })
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(name, style = Paper.type.display, color = Paper.colors.muted)
    }
}
