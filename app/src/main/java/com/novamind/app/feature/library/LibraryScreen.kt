package com.novamind.app.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novamind.app.ui.theme.AppTheme

private val BgPage = Color(0xFFF0EFEA)
private val ColorTextTitle = Color(0xFF1A1A1A)
private val ColorTextSub = Color(0xFF6B6B6B)
private val ColorBorder = Color(0xFFE0E0E0)

private data class LibraryNote(val title: String, val preview: String, val tag: String)

private val sampleNotes = listOf(
    LibraryNote("Market research", "Overview of competitors in 2026.", "Research"),
    LibraryNote("Q2 Strategy", "Key initiatives for Q2 growth plan.", "Strategy"),
    LibraryNote("Design system", "Component tokens and guidelines.", "Design"),
    LibraryNote("Team retro", "Sprint retrospective notes.", "Meeting"),
    LibraryNote("Product roadmap", "Feature priorities for H2.", "Product"),
    LibraryNote("User interviews", "Insights from 10 user sessions.", "Research"),
)

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgPage)
            .statusBarsPadding()
            .padding(bottom = 100.dp),
    ) {
        Text(
            text = "Library",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ColorTextTitle,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(sampleNotes) { note ->
                LibraryNoteCard(note = note)
            }
        }
    }
}

@Composable
private fun LibraryNoteCard(note: LibraryNote) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // tag chip
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF3D7A5A).copy(alpha = 0.1f),
            ) {
                Text(
                    text = note.tag,
                    fontSize = 10.sp,
                    color = Color(0xFF3D7A5A),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text(note.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ColorTextTitle)
            Text(note.preview, fontSize = 12.sp, color = ColorTextSub, lineHeight = 17.sp, maxLines = 3)
        }
    }
}

@Composable
fun LibraryRoute(modifier: Modifier = Modifier) {
    LibraryScreen(modifier = modifier)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun LibraryScreenPreview() {
    AppTheme { LibraryScreen() }
}
