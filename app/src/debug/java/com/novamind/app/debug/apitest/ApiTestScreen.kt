package com.novamind.app.debug.apitest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novamind.app.ui.theme.AppTheme

private val Bg = Color(0xFFF4F4F5)
private val Card = Color(0xFFFFFFFF)
private val TextMain = Color(0xFF1A1A1A)
private val TextSub = Color(0xFF6B6B6B)
private val Accent = Color(0xFF3D7A5A)
private val Danger = Color(0xFFD13C3C)

/** 有状态路由：连接 ViewModel 与无状态 UI。 */
@Composable
fun ApiTestRoute(
    onBack: () -> Unit,
    target: ApiTarget = ApiTarget.ITEMS,
    viewModel: ApiTestViewModel = viewModel(),
) {
    LaunchedEffect(target) { viewModel.setTarget(target) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ApiTestScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
    )
}

/** 无状态 UI：可预览、可测试。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTestScreen(
    uiState: ApiTestUiState,
    onEvent: (ApiTestEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(uiState.target.title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 22.sp, color = TextMain)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.page,
                    onValueChange = { onEvent(ApiTestEvent.UpdatePage(it)) },
                    label = { Text(uiState.target.pageParam) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = uiState.size,
                    onValueChange = { onEvent(ApiTestEvent.UpdateSize(it)) },
                    label = { Text(uiState.target.sizeParam) },
                    singleLine = true,
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                ApiTestViewModel.buildUrl(uiState.target, uiState.page, uiState.size),
                fontSize = 12.sp,
                color = TextSub,
                fontFamily = FontFamily.Monospace,
            )

            PrimaryButton(
                text = if (uiState.isLoading) "Requesting…" else "Request API",
                enabled = !uiState.isLoading,
                onClick = { onEvent(ApiTestEvent.Fetch) },
            )

            uiState.errorMessage?.let { msg ->
                Text(
                    "⚠ $msg",
                    color = Danger,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Danger.copy(alpha = 0.08f))
                        .padding(12.dp),
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading && uiState.items.isEmpty() -> {
                        CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    !uiState.hasLoaded -> {
                        Text(
                            "Tap the button above to make a request",
                            color = TextSub,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            item {
                                Text(
                                    "${uiState.items.size} items total",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Accent,
                                )
                            }
                            items(uiState.items) { item -> ItemCard(item) }
                            if (uiState.rawPreview.isNotEmpty()) {
                                item {
                                    Text("Raw response (first 800 chars)", fontSize = 12.sp, color = TextSub)
                                    Text(
                                        uiState.rawPreview,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextSub,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Card)
                                            .padding(10.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: ApiItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.id.isNotEmpty()) {
                Text(
                    "#${item.id}",
                    fontSize = 11.sp,
                    color = Accent,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextMain)
        }
        if (item.subtitle.isNotEmpty()) {
            Text(item.subtitle, fontSize = 13.sp, color = TextSub)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val alpha = if (enabled) 1f else 0.5f
    Text(
        text = text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Accent.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

// ── Previews ───────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun PreviewEmpty() {
    AppTheme {
        ApiTestScreen(ApiTestUiState(), {}, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewLoaded() {
    AppTheme {
        ApiTestScreen(
            ApiTestUiState(
                hasLoaded = true,
                items = listOf(
                    ApiItem("1", "Item 1", "This is a description", "{}"),
                    ApiItem("2", "Item 2", "Another description", "{}"),
                ),
                rawPreview = "{\"items\":[...]}",
            ),
            {}, {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewError() {
    AppTheme {
        ApiTestScreen(
            ApiTestUiState(hasLoaded = true, errorMessage = "HTTP 500: server error"),
            {}, {},
        )
    }
}
