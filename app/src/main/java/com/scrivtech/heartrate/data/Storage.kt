package com.scrivtech.heartrate.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class Storage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("heartrate_data", Context.MODE_PRIVATE)

    fun getRecentDevices(): List<RecentDevice> {
        val json = prefs.getString(KEY_RECENT_DEVICES, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
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

    fun getSessions(): List<HrSession> {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
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
        private const val MAX_RECENT_DEVICES = 7
        private const val MAX_SESSIONS = 30
    }
}
