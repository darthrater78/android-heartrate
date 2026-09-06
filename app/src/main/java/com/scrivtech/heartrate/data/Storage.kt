package com.scrivtech.heartrate.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Storage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("heartrate_data", Context.MODE_PRIVATE)

    /**
     * Age in years, or null when none has been set.
     *
     * Zones are percentages of a maximum heart rate derived from age, so without this
     * there is nothing to compute them from. Callers must treat null as "zones
     * unavailable" and hide them rather than substituting a default age, which would
     * quietly show every user someone else's zones.
     */
    fun getAge(): Int? = prefs.getInt(KEY_AGE, 0).takeIf { it in MIN_AGE..MAX_AGE }

    fun setAge(age: Int) {
        prefs.edit().putInt(KEY_AGE, age.coerceIn(MIN_AGE, MAX_AGE)).apply()
    }

    fun getRecentDevices(): List<RecentDevice> = readList(KEY_RECENT_DEVICES) { array ->
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            RecentDevice(
                name = obj.getString("name"),
                address = obj.getString("address"),
                lastConnectedAt = obj.getLong("lastConnectedAt")
            )
        }.sortedByDescending { it.lastConnectedAt }
    }

    fun addRecentDevice(name: String, address: String) {
        val devices = getRecentDevices().toMutableList()
        devices.removeAll { it.address == address }
        devices.add(0, RecentDevice(name, address, System.currentTimeMillis()))
        val trimmed = devices.take(MAX_RECENT_DEVICES)

        val array = JSONArray()
        trimmed.forEach { d ->
            array.put(JSONObject().apply {
                put("name", d.name)
                put("address", d.address)
                put("lastConnectedAt", d.lastConnectedAt)
            })
        }
        prefs.edit().putString(KEY_RECENT_DEVICES, array.toString()).apply()
    }

    fun getSessions(): List<HrSession> = readList(KEY_SESSIONS) { array ->
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            val readingsArray = obj.getJSONArray("readings")
            val readings = (0 until readingsArray.length()).map { r ->
                val pair = readingsArray.getJSONArray(r)
                HrReading(pair.getLong(0), pair.getInt(1))
            }
            HrSession(
                id = obj.getString("id"),
                sessionName = obj.optString("sessionName", ""),
                deviceName = obj.getString("deviceName"),
                deviceAddress = obj.getString("deviceAddress"),
                startTime = obj.getLong("startTime"),
                endTime = obj.getLong("endTime"),
                readings = readings,
                avgBpm = obj.getInt("avgBpm"),
                maxBpm = obj.getInt("maxBpm"),
                minBpm = obj.getInt("minBpm")
            )
        }.sortedByDescending { it.startTime }
    }

    fun saveSession(session: HrSession) {
        val sessions = getSessions().toMutableList()
        sessions.add(0, session)
        writeSessions(sessions.take(MAX_SESSIONS))
    }

    fun deleteSession(sessionId: String) {
        writeSessions(getSessions().filter { it.id != sessionId })
    }

    fun renameSession(sessionId: String, newName: String) {
        writeSessions(getSessions().map { s ->
            if (s.id == sessionId) s.copy(sessionName = newName) else s
        })
    }

    /**
     * Reads and parses one stored JSON array, yielding an empty list if it cannot be read.
     *
     * A truncated or hand-edited blob previously threw JSONException straight out of the
     * composable that calls this, crashing the app on launch with no way back in. There is
     * nothing to salvage from malformed JSON, but starting empty beats not starting.
     */
    private fun <T> readList(key: String, parse: (JSONArray) -> List<T>): List<T> {
        val json = prefs.getString(key, null) ?: return emptyList()
        return runCatching { parse(JSONArray(json)) }.getOrElse {
            android.util.Log.w("HeartRateMirror", "Discarding unreadable '$key' data", it)
            emptyList()
        }
    }

    private fun writeSessions(sessions: List<HrSession>) {
        val array = JSONArray()
        sessions.forEach { s ->
            val readingsArray = JSONArray()
            s.readings.forEach { r ->
                readingsArray.put(JSONArray().apply {
                    put(r.timestampMs)
                    put(r.bpm)
                })
            }
            array.put(JSONObject().apply {
                put("id", s.id)
                put("sessionName", s.sessionName)
                put("deviceName", s.deviceName)
                put("deviceAddress", s.deviceAddress)
                put("startTime", s.startTime)
                put("endTime", s.endTime)
                put("readings", readingsArray)
                put("avgBpm", s.avgBpm)
                put("maxBpm", s.maxBpm)
                put("minBpm", s.minBpm)
            })
        }
        prefs.edit().putString(KEY_SESSIONS, array.toString()).apply()
    }

    companion object {
        private const val KEY_RECENT_DEVICES = "recent_devices"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_AGE = "age_years"
        private const val MAX_RECENT_DEVICES = 7
        private const val MAX_SESSIONS = 30

        // Bounds the age entry field shares, so validation and storage agree. Wide enough
        // to be nobody's business, narrow enough to reject a mistyped year of birth.
        const val MIN_AGE = 10
        const val MAX_AGE = 120
    }
}
