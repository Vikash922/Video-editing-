package com.vikash.vidopro.ui.home

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ProjectManager {
    private const val PREFS_NAME = "editpro_projects_prefs"
    private const val KEY_PROJECTS = "saved_projects_list"
    private val gson = Gson()

    private val initialProjects = listOf(
        ProjectItem(
            id = "proj_1",
            title = "My Edit 12",
            meta = "00:32 · 1080P",
            timeAgo = "2h ago",
            thumbUrl = "https://images.pexels.com/photos/3863218/pexels-photo-3863218.jpeg?auto=compress&cs=tinysrgb&dpr=1&fit=crop&h=200&w=280",
            durationMs = 32000L
        ),
        ProjectItem(
            id = "proj_2",
            title = "Travel Vlog",
            meta = "01:45 · 1080P",
            timeAgo = "5h ago",
            thumbUrl = "https://images.pexels.com/photos/20344919/pexels-photo-20344919.jpeg?auto=compress&cs=tinysrgb&dpr=1&fit=crop&h=200&w=280",
            durationMs = 105000L
        ),
        ProjectItem(
            id = "proj_3",
            title = "Cinematic",
            meta = "00:58 · 1080P",
            timeAgo = "1d ago",
            thumbUrl = "https://images.pexels.com/photos/14169534/pexels-photo-14169534.jpeg?auto=compress&cs=tinysrgb&dpr=1&fit=crop&h=200&w=280",
            durationMs = 58000L
        ),
        ProjectItem(
            id = "proj_4",
            title = "Reels",
            meta = "00:21 · 1080P",
            timeAgo = "2d ago",
            thumbUrl = "https://images.pexels.com/photos/19779565/pexels-photo-19779565.jpeg?auto=compress&cs=tinysrgb&dpr=1&fit=crop&h=200&w=280",
            durationMs = 21000L
        )
    )

    val initialTemplates = listOf(
        TemplateItem(
            id = "tmpl_1",
            name = "Cinematic",
            duration = "00:15",
            imgUrl = "https://images.pexels.com/photos/14169534/pexels-photo-14169534.jpeg?auto=compress&cs=tinysrgb&dpr=1&fit=crop&h=320&w=240",
            description = "Moody cinematic colors with letterbox aspect ratio and slow-motion cuts."
        ),
        TemplateItem(
            id = "tmpl_2",
            name = "Travel",
            duration = "00:30",
            imgUrl = "https://images.pexels.com/photos/3863218/pexels-photo-3863218.jpeg?auto=compress&cs=tinysrgb&dpr=1&fit=crop&h=320&w=240",
            description = "High energy travel transitions with vibrant color grading."
        ),
        TemplateItem(
            id = "tmpl_3",
            name = "Sunset",
            duration = "00:20",
            imgUrl = "https://images.pexels.com/photos/29857601/pexels-photo-29857601.jpeg?auto=compress&cs=tinysrgb&dpr=1&fit=crop&h=320&w=240",
            description = "Golden hour warm tones, soft glow, and relaxed pacing."
        ),
        TemplateItem(
            id = "tmpl_4",
            name = "Vlog",
            duration = "01:00",
            imgUrl = "https://images.pexels.com/photos/28492053/pexels-photo-28492053.jpeg?auto=compress&cs=tinysrgb&dpr=1&fit=crop&h=320&w=240",
            description = "Dynamic jump cuts, subtitle captions, and modern typography."
        )
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getProjects(context: Context): List<ProjectItem> {
        val json = getPrefs(context).getString(KEY_PROJECTS, null)
        if (json.isNullOrEmpty()) {
            saveProjects(context, initialProjects)
            return initialProjects
        }
        return try {
            val type = object : TypeToken<List<ProjectItem>>() {}.type
            gson.fromJson<List<ProjectItem>>(json, type) ?: initialProjects
        } catch (e: Exception) {
            initialProjects
        }
    }

    fun saveProjects(context: Context, list: List<ProjectItem>) {
        val json = gson.toJson(list)
        getPrefs(context).edit().putString(KEY_PROJECTS, json).apply()
    }

    fun addProject(context: Context, project: ProjectItem) {
        val list = getProjects(context).toMutableList()
        list.add(0, project)
        saveProjects(context, list)
    }

    fun deleteProject(context: Context, projectId: String): List<ProjectItem> {
        val list = getProjects(context).filterNot { it.id == projectId }
        saveProjects(context, list)
        return list
    }

    fun renameProject(context: Context, projectId: String, newTitle: String): List<ProjectItem> {
        val list = getProjects(context).map {
            if (it.id == projectId) it.copy(title = newTitle, timeAgo = "Just now") else it
        }
        saveProjects(context, list)
        return list
    }

    fun duplicateProject(context: Context, projectId: String): List<ProjectItem> {
        val currentList = getProjects(context).toMutableList()
        val index = currentList.indexOfFirst { it.id == projectId }
        if (index != -1) {
            val original = currentList[index]
            val duplicate = original.copy(
                id = "proj_${System.currentTimeMillis()}",
                title = "${original.title} (Copy)",
                timeAgo = "Just now",
                lastModified = System.currentTimeMillis()
            )
            currentList.add(0, duplicate)
            saveProjects(context, currentList)
        }
        return currentList
    }
}
