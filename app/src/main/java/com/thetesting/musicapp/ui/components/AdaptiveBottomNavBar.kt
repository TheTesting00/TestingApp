package com.thetesting.musicapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thetesting.musicapp.model.NavBarStyleId
import com.thetesting.musicapp.ui.theme.LocalThemeExtras

enum class AppTab(val label: String, val icon: ImageVector) {
    MUSIC("Musik", Icons.Rounded.MusicNote),
    EDIT("Edit", Icons.Rounded.Edit),
    ADD("Tambah", Icons.Rounded.Add),
    EARPHONE("Earphone", Icons.Rounded.Headphones),
    THEME("Tema", Icons.Rounded.Palette)
}

@Composable
fun AdaptiveBottomNavBar(
    selectedTab: AppTab,
    styleId: NavBarStyleId,
    earphoneConnected: Boolean,
    onSelectTab: (AppTab) -> Unit,
    onCenterAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extras = LocalThemeExtras.current
    val tabs = AppTab.entries

    val isGradientBar = styleId == NavBarStyleId.GRADIENT_BOLD || styleId == NavBarStyleId.CURVED_PILL
    val isFloating = styleId != NavBarStyleId.MINIMAL_CLEAN && styleId != NavBarStyleId.TAB_INDICATOR

    val containerBrush = when (styleId) {
        NavBarStyleId.GRADIENT_BOLD,
        NavBarStyleId.CURVED_PILL -> Brush.horizontalGradient(listOf(extras.start, extras.end))
        NavBarStyleId.GLASSMORPHISM -> Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
            )
        )
        else -> Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surface
            )
        )
    }

    val outerHorizontalPadding = when (styleId) {
        NavBarStyleId.MINIMAL_CLEAN, NavBarStyleId.TAB_INDICATOR -> 0.dp
        NavBarStyleId.IOS_DOCK, NavBarStyleId.CURVED_PILL -> 16.dp
        else -> 12.dp
    }
    val outerBottomPadding = if (isFloating) 10.dp else 0.dp
    val cornerShape = when (styleId) {
        NavBarStyleId.MINIMAL_CLEAN, NavBarStyleId.TAB_INDICATOR -> RoundedCornerShape(0.dp)
        NavBarStyleId.IOS_DOCK, NavBarStyleId.CURVED_PILL, NavBarStyleId.FLOATING_DETACHED -> RoundedCornerShape(28.dp)
        else -> RoundedCornerShape(22.dp)
    }

    val borderStroke = when (styleId) {
        NavBarStyleId.GLASSMORPHISM -> BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
        NavBarStyleId.FLOATING_DETACHED,
        NavBarStyleId.IOS_DOCK -> BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
        else -> null
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = outerHorizontalPadding, vertical = outerBottomPadding)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFloating) Modifier.shadow(10.dp, cornerShape) else Modifier
                ),
            shape = cornerShape,
            color = Color.Transparent,
            border = borderStroke
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(containerBrush)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    if (tab == AppTab.ADD) {
                        CenterAddItem(
                            styleId = styleId,
                            isGradientBar = isGradientBar,
                            onClick = onCenterAddClick
                        )
                    } else {
                        val isSelected = selectedTab == tab
                        NavBarTabItem(
                            tab = tab,
                            isSelected = isSelected,
                            styleId = styleId,
                            isGradientBar = isGradientBar,
                            showBadge = tab == AppTab.EARPHONE && earphoneConnected,
                            onClick = { onSelectTab(tab) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterAddItem(
    styleId: NavBarStyleId,
    isGradientBar: Boolean,
    onClick: () -> Unit
) {
    val extras = LocalThemeExtras.current
    val isElevatedFab = styleId == NavBarStyleId.CENTER_FAB || styleId == NavBarStyleId.FLOATING_DETACHED

    Column(
        modifier = Modifier
            .size(width = 60.dp, height = 52.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .then(if (styleId == NavBarStyleId.CENTER_FAB) Modifier.offset(y = (-6).dp) else Modifier)
                .size(if (isElevatedFab) 44.dp else 36.dp)
                .clip(CircleShape)
                .background(
                    if (isGradientBar) {
                        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.18f)))
                    } else {
                        Brush.linearGradient(listOf(extras.start, extras.end))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "Tambah file musik",
                tint = Color.White,
                modifier = Modifier.size(if (isElevatedFab) 24.dp else 20.dp)
            )
        }
        if (!isElevatedFab) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tambah",
                fontSize = 10.sp,
                color = if (isGradientBar) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NavBarTabItem(
    tab: AppTab,
    isSelected: Boolean,
    styleId: NavBarStyleId,
    isGradientBar: Boolean,
    showBadge: Boolean,
    onClick: () -> Unit
) {
    val activeColor = if (isGradientBar) Color.White else MaterialTheme.colorScheme.primary
    val inactiveColor = if (isGradientBar) {
        Color.White.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val iconTint by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        label = "navTint"
    )

    val pillColor = when {
        !isSelected -> Color.Transparent
        styleId == NavBarStyleId.PILL_HIGHLIGHT || styleId == NavBarStyleId.IOS_DOCK -> {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        }
        styleId == NavBarStyleId.GLASSMORPHISM -> Color.White.copy(alpha = 0.18f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .size(width = 64.dp, height = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(pillColor)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (styleId == NavBarStyleId.TAB_INDICATOR) {
            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(width = 24.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) activeColor else Color.Transparent)
            )
        }
        Box {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            if (showBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-2).dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = tab.label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = iconTint,
            maxLines = 1
        )
    }
}
