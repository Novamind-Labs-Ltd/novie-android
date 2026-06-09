package com.novamind.app.feature.home

import androidx.lifecycle.ViewModel
import com.novamind.app.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            upcomingItems = listOf(
                UpcomingItem(
                    id = "1",
                    title = "Monthly report sharing",
                    subtitle = "Team project progress tracking",
                    iconResId = R.drawable.ic_upcoming_report,
                ),
                UpcomingItem(
                    id = "2",
                    title = "Board meeting",
                    subtitle = "Internal stakeholder alignment",
                    iconResId = R.drawable.ic_upcoming_meeting,
                ),
            ),
            notes = listOf(
                NoteItem(
                    id = "1",
                    title = "Market research",
                    description = "Here is an overview of your competitors in 2026.\n\n3 new competitor in the market, they all boutique studios in...",
                    isSelected = false,
                ),
                NoteItem(
                    id = "2",
                    title = "Market research",
                    description = "Here is an overview of your competitors in 2026.\n\n3 new competitor in the market, they all boutique studios in...",
                    isSelected = true,
                ),
                NoteItem(
                    id = "3",
                    title = "Market research",
                    description = "Here is an overview of your competitors in 2026.\n\n3 new competitor in the market, they all boutique studios in...",
                    isSelected = false,
                ),
            ),
        )
    )
    val uiState = _uiState.asStateFlow()

    fun onSearchQueryChange(query: String) {
        // TODO: filter items by query
    }
}
