package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.AppTab
import com.example.ui.KashirakUiState
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldBright
import com.example.ui.theme.GoldRich
import com.example.ui.theme.HeaderSubFg
import com.example.ui.theme.LocalKashirakColors
import com.example.ui.theme.NavyPillBg
import com.example.ui.theme.NavyPillBorder
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.SyncAmber
import com.example.ui.theme.SyncGreen
import com.example.util.Formatters

@Composable
fun KashirakLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 64f
        val sy = h / 64f

        // Blue gradient rounded square
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF1F5FD6), Color(0xFF0A2A78)),
                start = Offset.Zero,
                end = Offset(w, h)
            ),
            size = size,
            cornerRadius = CornerRadius(16f * sx, 16f * sy)
        )

        val goldBrush = Brush.verticalGradient(
            colors = listOf(Color(0xFFF3D779), Color(0xFFC8952A))
        )

        // Ledger book
        val bookPath = Path().apply {
            moveTo(16f * sx, 14f * sy)
            lineTo(42f * sx, 14f * sy)
            quadraticTo(46f * sx, 14f * sy, 46f * sx, 18f * sy)
            lineTo(46f * sx, 40f * sy)
            lineTo(20f * sx, 40f * sy)
            quadraticTo(16f * sx, 40f * sy, 16f * sx, 36f * sy)
            close()
        }
        drawPath(bookPath, brush = goldBrush)

        // Ledger lines
        val lineColor = Color(0xFF8A5F12)
        drawLine(lineColor, Offset(22f * sx, 22f * sy), Offset(40f * sx, 22f * sy), 2.2f * sx, StrokeCap.Round)
        drawLine(lineColor, Offset(22f * sx, 28f * sy), Offset(36f * sx, 28f * sy), 2.2f * sx, StrokeCap.Round)
        drawLine(lineColor, Offset(22f * sx, 34f * sy), Offset(32f * sx, 34f * sy), 2.2f * sx, StrokeCap.Round)

        // Cart basket
        val cartPath = Path().apply {
            moveTo(30f * sx, 40f * sy)
            lineTo(54f * sx, 40f * sy)
            lineTo(51f * sx, 51f * sy)
            lineTo(33f * sx, 51f * sy)
            close()
        }
        drawPath(cartPath, brush = goldBrush)
        drawPath(
            cartPath,
            color = Color(0xFF0A2A78),
            style = Stroke(width = 1.5f * sx, join = StrokeJoin.Round)
        )

        // Wheels
        drawCircle(Color(0xFFF3D779), radius = 2.6f * sx, center = Offset(36f * sx, 55f * sy))
        drawCircle(Color(0xFFF3D779), radius = 2.6f * sx, center = Offset(48f * sx, 55f * sy))

        // White star sparkle
        val star = Path().apply {
            moveTo(50f * sx, 8f * sy)
            lineTo(51.6f * sx, 11.6f * sy)
            lineTo(55f * sx, 13f * sy)
            lineTo(51.6f * sx, 14.4f * sy)
            lineTo(50f * sx, 18f * sy)
            lineTo(48.4f * sx, 14.4f * sy)
            lineTo(45f * sx, 13f * sy)
            lineTo(48.4f * sx, 11.6f * sy)
            close()
        }
        drawPath(star, color = Color.White)
    }
}

@Composable
fun KashirakHeader(
    state: KashirakUiState,
    onSyncClick: () -> Unit,
    onAccountPillClick: () -> Unit,
    onRoleClick: () -> Unit
) {
    val ext = LocalKashirakColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ext.headerBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.widthIn(max = 540.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KashirakLogo(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White)) { append("كاشيرك ") }
                            withStyle(SpanStyle(color = GoldAccent)) { append("الذكي") }
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${state.settings.shopName.ifBlank { "المحل" }} · ${Formatters.headerDate()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HeaderSubFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    val pillText = when {
                        state.isSyncing -> "جاري المزامنة..."
                        !state.isOnline && state.pendingSyncCount > 0 -> "بدون إنترنت · ${state.pendingSyncCount} بانتظار المزامنة"
                        !state.isOnline -> "وضع عدم الاتصال · محفوظ محلياً"
                        state.pendingSyncCount > 0 -> "بانتظار المزامنة · ${state.pendingSyncCount}"
                        else -> "متزامن مع حساب جوجل"
                    }
                    Surface(
                        onClick = onAccountPillClick,
                        shape = RoundedCornerShape(999.dp),
                        color = NavyPillBg,
                        border = BorderStroke(1.dp, NavyPillBorder),
                        modifier = Modifier.testTag("header_sync_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    !state.isOnline -> Icons.Default.CloudOff
                                    state.isSyncing -> Icons.Default.Sync
                                    else -> Icons.Default.CloudDone
                                },
                                contentDescription = null,
                                tint = GoldAccent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = pillText,
                                color = Color(0xFFE7ECF8),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Cloud Sync button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NavyPillBg)
                        .border(1.dp, Color(0xFF38507F), RoundedCornerShape(14.dp))
                        .clickable(onClick = onSyncClick)
                        .testTag("sync_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = stringResource(R.string.cd_sync_button),
                        tint = GoldAccent,
                        modifier = Modifier.size(23.dp)
                    )
                    val dotColor = when {
                        !state.isOnline -> Color(0xFF9AA6BF)
                        state.pendingSyncCount > 0 || state.isSyncing -> SyncAmber
                        else -> SyncGreen
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 6.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                            .border(2.dp, NavyPillBg, CircleShape)
                    )
                }

                // Role pill button (المالك / الكاشير)
                val isCashier = !state.isOwner
                Surface(
                    onClick = onRoleClick,
                    shape = RoundedCornerShape(999.dp),
                    color = if (isCashier) GoldAccent else Color.Transparent,
                    border = BorderStroke(1.dp, if (isCashier) GoldAccent else Color(0xFF6F6A52)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("role_button")
                ) {
                    Text(
                        text = if (isCashier) "الكاشير" else "المالك",
                        color = if (isCashier) NavyPrimary else Color(0xFFE7ECF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(GoldAccent)
        )
    }
}

@Composable
fun KashirakBottomNav(
    state: KashirakUiState,
    onSelectTab: (AppTab) -> Unit,
    onOpenSell: () -> Unit
) {
    val ext = LocalKashirakColors.current
    val startTabs = listOf(
        Triple(AppTab.HOME, "الرئيسية", Icons.Default.Home),
        Triple(AppTab.DEBTS, "الديون", Icons.AutoMirrored.Filled.ReceiptLong),
        Triple(AppTab.EXPENSES, "المصروف", Icons.Default.AccountBalanceWallet)
    ).filter { state.allowed(it.first) }

    val endTabs = listOf(
        Triple(AppTab.REPORTS, "التقارير", Icons.Default.BarChart),
        Triple(AppTab.INVENTORY, "الجرد", Icons.Default.Inventory2),
        Triple(AppTab.MORE, "المزيد", Icons.Default.Menu)
    ).filter { state.allowed(it.first) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ext.navBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ext.dividerLine)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 540.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        startTabs.forEach { (tab, label, icon) ->
                            NavTabItem(
                                label = label,
                                icon = icon,
                                selected = state.activeTab == tab,
                                testTag = "tab_${tab.name.lowercase()}",
                                onClick = { onSelectTab(tab) }
                            )
                        }
                    }

                    // Space reserved for centered raised gold FAB
                    Spacer(Modifier.width(86.dp))

                    val invAlerts = state.lowStockProducts().size
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        endTabs.forEach { (tab, label, icon) ->
                            NavTabItem(
                                label = label,
                                icon = icon,
                                selected = state.activeTab == tab || (tab == AppTab.MORE && state.activeTab == AppTab.WORKERS),
                                badgeCount = if (tab == AppTab.INVENTORY) invAlerts else 0,
                                testTag = "tab_${tab.name.lowercase()}",
                                onClick = { onSelectTab(tab) }
                            )
                        }
                    }
                }
            }
        }

        // Raised circular gold FAB ("كاشير") centered above the nav bar
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-26).dp)
                .size(88.dp),
            contentAlignment = Alignment.Center
        ) {
            // Soft outer halo
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(GoldAccent.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .shadow(10.dp, CircleShape, ambientColor = GoldRich, spotColor = GoldRich)
                    .clip(CircleShape)
                    .background(ext.navBg)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(GoldBright, GoldRich)
                        )
                    )
                    .clickable(onClick = onOpenSell)
                    .testTag("fab_cashier"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = stringResource(R.string.cd_cashier_fab),
                        tint = NavyPrimary,
                        modifier = Modifier.size(25.dp)
                    )
                    Text(
                        text = "كاشير",
                        color = NavyPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    testTag: String,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    val ext = LocalKashirakColors.current
    val tint = if (selected) ext.goldDarkText else ext.mutedText
    Column(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 6.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(ext.badFg)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                        .testTag("${testTag}_badge"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun KashirakCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val ext = LocalKashirakColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ext.cardBg,
        border = BorderStroke(1.dp, ext.dividerLine),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val tagModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = NavyPrimary,
            disabledContainerColor = GoldAccent.copy(alpha = 0.45f),
            disabledContentColor = NavyPrimary.copy(alpha = 0.55f)
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = tagModifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (enabled) Brush.verticalGradient(listOf(GoldBright, GoldRich))
                else Brush.verticalGradient(listOf(GoldBright.copy(alpha = 0.45f), GoldRich.copy(alpha = 0.45f)))
            )
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            color = if (enabled) NavyPrimary else NavyPrimary.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    testTag: String? = null
) {
    val ext = LocalKashirakColors.current
    val tagMod = if (testTag != null) modifier.testTag(testTag) else modifier
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = ext.mutedText,
                fontSize = 13.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GoldAccent,
            unfocusedBorderColor = ext.dividerLine,
            focusedContainerColor = ext.cardBg,
            unfocusedContainerColor = ext.cardBg
        ),
        modifier = tagMod.fillMaxWidth()
    )
}
