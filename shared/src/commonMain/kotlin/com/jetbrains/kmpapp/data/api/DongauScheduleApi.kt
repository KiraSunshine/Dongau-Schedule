package com.jetbrains.kmpapp.data.api

import com.jetbrains.kmpapp.data.DebugConfig
import com.jetbrains.kmpapp.data.model.DongauApiResponse
import com.jetbrains.kmpapp.data.model.DongauGroupItem
import com.jetbrains.kmpapp.data.model.DongauScheduleResponse
import com.jetbrains.kmpapp.data.model.ScheduleTarget
import com.jetbrains.kmpapp.data.model.ScheduleTargetType
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.errors.IOException

class DongauScheduleApi(private val client: HttpClient) {

    private val baseUrl = "http://edu.dongau.ru"

    suspend fun searchGroups(query: String): List<ScheduleTarget> {
        if (DebugConfig.isOfflineSimulated.value) {
            throw IOException("Simulated network offline")
        }
        val response: DongauApiResponse<List<DongauGroupItem>> =
            client.get("$baseUrl/api/raspGrouplist") {
                header(HttpHeaders.UserAgent, "dongau-schedule-app/1.0")
            }.body()

        val trimmed = query.trim().lowercase()
        val normalized = normalizeSearch(trimmed)
        return response.data
            .filter { group ->
                trimmed.isEmpty() ||
                    normalizeSearch(group.name.lowercase()).contains(normalized) ||
                    normalizeSearch(group.faculty.lowercase()).contains(normalized)
            }
            .map { group ->
                ScheduleTarget(
                    id = group.id,
                    targetTitle = group.name,
                    fullTitle = "${group.name} (${group.faculty}, ${group.kurs} курс)",
                    scheduleTarget = ScheduleTargetType.GROUP.id
                )
            }
    }

    private fun normalizeSearch(value: String): String {
        return value.replace("ё", "е").replace("-", "_").replace(" ", "_")
    }

    suspend fun getSchedule(targetType: ScheduleTargetType, id: Int): DongauScheduleResponse {
        if (DebugConfig.isOfflineSimulated.value) {
            throw IOException("Simulated network offline")
        }
        val response: DongauApiResponse<DongauScheduleResponse> =
            client.get("$baseUrl/api/Rasp") {
                header(HttpHeaders.UserAgent, "dongau-schedule-app/1.0")
                header(HttpHeaders.Accept, "application/json")
                parameter(targetType.apiParam, id)
            }.body()
        return response.data
    }
}