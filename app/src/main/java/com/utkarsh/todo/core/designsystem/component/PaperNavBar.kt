package com.utkarsh.todo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.utkarsh.todo.core.designsystem.theme.Paper

/** One destination in [PaperNavBar]. */
data class NavBarItem(val key: String, val label: String, val icon: ImageVector)

/**
 * Bottom navigation: two destinations, a centre [AddFab], two destinations.
 * [items] must hold exactly four entries (left pair, right pair).
 */
@Composable
fun PaperNavBar(
    items: List<NavBarItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(items.size == 4) { "PaperNavBar expects exactly 4 items, got ${items.size}" }
    val colors = Paper.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.card)
            .border(1.dp, colors.rule)
            .navigationBarsPadding()
            .height(76.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.take(2).forEach { NavItem(it, it.key == selectedKey, onSelect, Modifier.weight(1f)) }
        AddFab(onAdd)
        items.drop(2).forEach { NavItem(it, it.key == selectedKey, onSelect, Modifier.weight(1f)) }
    }
}

@Composable
private fun NavItem(item: NavBarItem, selected: Boolean, onSelect: (String) -> Unit, modifier: Modifier) {
    val tint = if (selected) Paper.colors.ink else Paper.colors.mutedSoft
    Column(
        modifier = modifier
            .clickable(role = Role.Tab) { onSelect(item.key) }
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(item.label, style = Paper.type.navLabel, color = tint)
    }
}
