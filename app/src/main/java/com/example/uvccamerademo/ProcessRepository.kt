package com.example.uvccamerademo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ProcessCache(
    val items: List<ProcessItem>,
    val fetchedAt: Long
)

class ProcessRepository(private val context: Context) {
    private val dataStore = context.appDataStore

    suspend fun loadApiUrl(): String {
        val prefs = dataStore.data.first()
        return prefs[KEY_API_URL] ?: DEFAULT_API_URL
    }

    suspend fun saveApiUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[KEY_API_URL] = url
        }
    }

    suspend fun loadSelectedProcess(): ProcessItem? {
        val prefs = dataStore.data.first()
        val id = prefs[KEY_SELECTED_PROCESS_ID] ?: return null
        val name = prefs[KEY_SELECTED_PROCESS_NAME] ?: return null
        return ProcessItem(id = id, name = name)
    }

    suspend fun saveSelectedProcess(item: ProcessItem?) {
        dataStore.edit { prefs ->
            if (item == null) {
                prefs.remove(KEY_SELECTED_PROCESS_ID)
                prefs.remove(KEY_SELECTED_PROCESS_NAME)
            } else {
                prefs[KEY_SELECTED_PROCESS_ID] = item.id
                prefs[KEY_SELECTED_PROCESS_NAME] = item.name
            }
        }
    }

    suspend fun loadCachedProcesses(): ProcessCache? {
        val prefs = dataStore.data.first()
        val json = prefs[KEY_PROCESS_CACHE] ?: return null
        val fetchedAt = prefs[KEY_PROCESS_FETCHED_AT] ?: return null
        val items = decodeProcessList(json)
        return ProcessCache(items = items, fetchedAt = fetchedAt)
    }

    suspend fun saveCachedProcesses(items: List<ProcessItem>, fetchedAt: Long = now()) {
        dataStore.edit { prefs ->
            prefs[KEY_PROCESS_CACHE] = encodeProcessList(items)
            prefs[KEY_PROCESS_FETCHED_AT] = fetchedAt
        }
    }

    fun isCacheValid(fetchedAt: Long): Boolean {
        return now() - fetchedAt <= PROCESS_CACHE_TTL_MS
    }

    suspend fun fetchProcesses(apiUrl: String): List<ProcessItem> = withContext(Dispatchers.IO) {
        val endpoint = apiUrl.trim().trimEnd('/') + "/api/processes"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        connection.connect()
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            error("HTTP $code")
        }
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        parseProcessResponse(body)
    }

    private fun parseProcessResponse(body: String): List<ProcessItem> {
        val json = JSONObject(body)
        val itemsJson = json.optJSONArray("items") ?: JSONArray()
        val items = mutableListOf<ProcessItem>()
        for (index in 0 until itemsJson.length()) {
            val item = itemsJson.optJSONObject(index) ?: continue
            val id = item.optString("id")
            val name = item.optString("name")
            if (id.isNotBlank() && name.isNotBlank()) {
                items.add(ProcessItem(id = id, name = name))
            }
        }
        return items
    }

    private fun encodeProcessList(items: List<ProcessItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("name", item.name)
            array.put(obj)
        }
        return array.toString()
    }

    private fun decodeProcessList(json: String): List<ProcessItem> {
        val array = JSONArray(json)
        val items = mutableListOf<ProcessItem>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isNotBlank() && name.isNotBlank()) {
                items.add(ProcessItem(id = id, name = name))
            }
        }
        return items
    }

    private fun now(): Long = System.currentTimeMillis()

    companion object {
        private const val DEFAULT_API_URL = "http://10.0.2.2:8080"
        private const val PROCESS_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

        private val KEY_API_URL = stringPreferencesKey("process_api_url")
        private val KEY_PROCESS_CACHE = stringPreferencesKey("process_cache_json")
        private val KEY_PROCESS_FETCHED_AT = longPreferencesKey("process_fetched_at")
        private val KEY_SELECTED_PROCESS_ID = stringPreferencesKey("selected_process_id")
        private val KEY_SELECTED_PROCESS_NAME = stringPreferencesKey("selected_process_name")
    }
}
