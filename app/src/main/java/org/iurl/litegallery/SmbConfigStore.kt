package org.iurl.litegallery

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Manages persistent storage of SMB server configurations using SharedPreferences.
 */
object SmbConfigStore {

    private const val PREFS_NAME = "smb_servers"
    private const val KEY_SERVERS = "servers_json"

    fun getAllServers(context: Context): List<SmbConfig> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                parseServer(array.getJSONObject(i))
            }
        } catch (e: Exception) {
            android.util.Log.e("SmbConfigStore", "Failed to parse servers", e)
            emptyList()
        }
    }

    fun getServerByHost(context: Context, host: String): SmbConfig? {
        return getAllServers(context).find { it.host.equals(host, ignoreCase = true) }
    }

    fun getServerById(context: Context, id: String): SmbConfig? {
        return getAllServers(context).find { it.id == id }
    }

    fun addServer(context: Context, config: SmbConfig): SmbConfig {
        val configWithId = if (config.id.isBlank()) {
            config.copy(id = UUID.randomUUID().toString())
        } else {
            config
        }
        val servers = getAllServers(context).toMutableList()
        servers.add(configWithId)
        saveServers(context, servers)
        return configWithId
    }

    fun updateServer(context: Context, config: SmbConfig) {
        val servers = getAllServers(context).toMutableList()
        val index = servers.indexOfFirst { it.id == config.id }
        if (index >= 0) {
            servers[index] = config
            saveServers(context, servers)
        }
    }

    fun deleteServer(context: Context, id: String) {
        val servers = getAllServers(context).toMutableList()
        servers.removeAll { it.id == id }
        saveServers(context, servers)
    }

    private fun saveServers(context: Context, servers: List<SmbConfig>) {
        val array = JSONArray()
        servers.forEach { config ->
            array.put(serializeServer(config))
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVERS, array.toString())
            .apply()
    }

    private fun serializeServer(config: SmbConfig): JSONObject {
        return JSONObject().apply {
            put("id", config.id)
            put("displayName", config.displayName)
            put("host", config.host)
            put("share", config.share)
            put("path", config.path)
            put("port", config.port)
            put("username", config.username)
            put("password", config.password)
            put("isGuest", config.isGuest)
        }
    }

    private fun parseServer(json: JSONObject): SmbConfig? {
        return try {
            SmbConfig(
                id = json.optString("id", UUID.randomUUID().toString()),
                displayName = json.optString("displayName", ""),
                host = json.optString("host", ""),
                share = json.optString("share", ""),
                path = json.optString("path", ""),
                port = json.optInt("port", 445),
                username = json.optString("username", ""),
                password = json.optString("password", ""),
                isGuest = json.optBoolean("isGuest", false)
            )
        } catch (e: Exception) {
            null
        }
    }
}
