package com.vikash.vidopro.ui.home

import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector

data class ProjectItem(
    val id: String,
    val title: String,
    val meta: String, // e.g., "00:32 · 1080P"
    val timeAgo: String, // e.g., "2h ago"
    val thumbUrl: String? = null,
    val videoUriString: String? = null,
    val durationMs: Long = 32000L,
    val lastModified: Long = System.currentTimeMillis()
)

data class TemplateItem(
    val id: String,
    val name: String,
    val duration: String,
    val imgUrl: String,
    val description: String = "",
    val category: String = "Trending"
)

data class QuickAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val description: String = ""
)

enum class HomeTab {
    PROJECTS,
    TEMPLATES
}

enum class NavigationTab(val label: String) {
    HOME("Home"),
    EDIT("Edit"),
    TEMPLATE("Template"),
    INBOX("Inbox"),
    ME("Me")
}
