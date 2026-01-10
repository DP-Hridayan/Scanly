package com.skeler.scanely.history.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class HistoryItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val imageUri: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class HistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val historyFile = File(context.filesDir, "scan_history.json")

    fun saveItem(text: String, imageUri: String) {
        val currentList = getHistory().toMutableList()
        currentList.add(0, HistoryItem(text = text, imageUri = imageUri))
        // Keep only last 50 items
        val trimmedList = currentList.take(50)
        saveHistory(trimmedList)
    }

    fun getHistory(): List<HistoryItem> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val jsonString = historyFile.readText()
            val jsonArray = JSONArray(jsonString)
            val items = mutableListOf<HistoryItem>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                items.add(
                    HistoryItem(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        text = obj.optString("text"),
                        imageUri = obj.optString("imageUri"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            items
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    fun clearHistory() {
        if (historyFile.exists()) {
            historyFile.delete()
        }
    }
    
    private fun saveHistory(items: List<HistoryItem>) {
        val jsonArray = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("text", item.text)
                put("imageUri", item.imageUri)
                put("timestamp", item.timestamp)
            }
            jsonArray.put(obj)
        }
        historyFile.writeText(jsonArray.toString())
    }
}
