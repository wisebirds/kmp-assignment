package com.wisebirds.assignment.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PASSWORD_SALT_ROUNDS = 10

// Configuration
private const val API_BASE_URL = "https://zimobhtejidbcsrujhxd.supabase.co/rest/v1"
private const val API_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InppbW9iaHRlamlkYmNzcnVqaHhkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTgwOTgxNzYsImV4cCI6MjA3MzY3NDE3Nn0.oi_IEKXYWgSUwrCNGVWtZhRf2ys92N028Kp1Km9vOXE"

private const val COMPANIES_ENDPOINT = "/web_assignment_company"
private const val USERS_ENDPOINT = "/web_assignment_users"
private const val CAMPAIGNS_TABLE = "web_assignment_campaigns"
private const val CAMPAIGNS_ENDPOINT = "/$CAMPAIGNS_TABLE"

// Types

/** 캠페인 목적 코드 값. 서버가 새 값을 내려줄 수 있어 모델에서는 String 으로 받습니다. */
object CampaignObjective {
    const val WEBSITE_CONVERSIONS = "WEBSITE_CONVERSIONS"
    const val WEBSITE_TRAFFIC = "WEBSITE_TRAFFIC"
    const val SALES = "SALES"
    const val APP_INSTALLATION = "APP_INSTALLATION"
    const val LEAD = "LEAD"
    const val BRAND = "BRAND"
    const val VIDEO_VIEWS = "VIDEO_VIEWS"

    val values = listOf(WEBSITE_CONVERSIONS, WEBSITE_TRAFFIC, SALES, APP_INSTALLATION, LEAD, BRAND, VIDEO_VIEWS)
}

/** 캠페인 주기 코드 값. 서버가 새 값을 내려줄 수 있어 모델에서는 String 으로 받습니다. */
object CampaignPeriod {
    const val DAILY = "DAILY"
    const val WEEKLY = "WEEKLY"
    const val MONTHLY = "MONTHLY"

    val values = listOf(DAILY, WEEKLY, MONTHLY)
}

@Serializable
data class Company(
    val id: Int,
    val name: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class User(
    val id: Int,
    val email: String,
    val name: String,
    val password: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class Campaign(
    val id: Int,
    val name: String,
    val enabled: Boolean,
    /** [CampaignObjective] 의 값 */
    @SerialName("campaign_objective") val campaignObjective: String,
    /** [CampaignPeriod] 의 값 */
    val period: String,
    val impressions: Long,
    val clicks: Long,
    val ctr: Double,
    @SerialName("video_views") val videoViews: Long,
    val vtr: Double,
    @SerialName("created_at") val createdAt: String,
)

/** create / update 요청 body. null 인 필드는 전송하지 않습니다. (web 의 `Partial<Company>`) */
@Serializable
data class CompanyInput(val name: String? = null)

/** create / update 요청 body. null 인 필드는 전송하지 않습니다. (web 의 `Partial<User>`) */
@Serializable
data class UserInput(
    val email: String? = null,
    val name: String? = null,
    val password: String? = null,
)

/** create / update 요청 body. null 인 필드는 전송하지 않습니다. (web 의 `Partial<Campaign>`) */
@Serializable
data class CampaignInput(
    val name: String? = null,
    val enabled: Boolean? = null,
    @SerialName("campaign_objective") val campaignObjective: String? = null,
    val period: String? = null,
    val impressions: Long? = null,
    val clicks: Long? = null,
    val ctr: Double? = null,
    @SerialName("video_views") val videoViews: Long? = null,
    val vtr: Double? = null,
)

data class PaginatedResponse<T>(
    val data: List<T>,
    val total: Int,
    val totalPages: Int,
    val currentPage: Int,
    val hasNext: Boolean,
    val hasPrev: Boolean,
)

enum class OrderDirection(val value: String) {
    ASC("asc"),
    DESC("desc"),
}

data class QueryOptions(
    val page: Int = 0,
    val limit: Int = 20,
    val orderBy: String = "id",
    val orderDir: OrderDirection = OrderDirection.DESC,
    val select: String = "*",
    /** PostgREST 필터. 예: mapOf("enabled" to "eq.true") */
    val filters: Map<String, Any?> = emptyMap(),
)

@Serializable
data class SupabaseError(
    val code: String,
    val details: String? = null,
    val hint: String? = null,
    val message: String,
)

/** API 에러. message 는 "code: message" 형식입니다. */
class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class CountRow(val count: Int)

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val RequestLogger = createClientPlugin("RequestLogger") {
    onRequest { request, _ ->
        println("🚀 ${request.method.value} ${request.url.buildString()}")
    }
    onResponse { response ->
        if (response.status.isSuccess()) {
            println("✅ ${response.status.value} ${response.call.request.url}")
        }
    }
}

private fun Map<String, Any?>.toQueryParameters(): List<Pair<String, String>> =
    mapNotNull { (key, value) -> value?.toString()?.takeIf { it.isNotEmpty() }?.let { key to it } }

// Base Ktor Client
class SupabaseHttpClient {
    private val client = HttpClient {
        expectSuccess = true
        install(RequestLogger)
        defaultRequest {
            header("apikey", API_KEY)
            header(HttpHeaders.Authorization, "Bearer $API_KEY")
        }
    }

    // Generic GET method with pagination
    suspend fun <T> get(
        endpoint: String,
        serializer: KSerializer<T>,
        options: QueryOptions = QueryOptions(),
    ): PaginatedResponse<T> = request {
        val filterParameters = options.filters.toQueryParameters()
        val start = options.page * options.limit

        val response = client.get("$API_BASE_URL$endpoint") {
            // Pagination
            parameter("offset", start)
            parameter("limit", options.limit)
            // Ordering
            parameter("order", "${options.orderBy}.${options.orderDir.value}")
            // Column selection
            parameter("select", options.select)
            // Filters
            filterParameters.forEach { (key, value) -> parameter(key, value) }
            header(HttpHeaders.Range, "$start-${start + options.limit - 1}")
        }
        val data = response.decodeList(serializer)

        // Always fetch total count from API using Prefer: count=exact header,
        // with the same filters as the data request
        val total = try {
            val countResponse = client.get("$API_BASE_URL$endpoint") {
                filterParameters.forEach { (key, value) -> parameter(key, value) }
                header("Prefer", "count=exact")
                header(HttpHeaders.Range, "0-0")
            }
            countResponse.headers[HttpHeaders.ContentRange]
                ?.let { contentRange -> Regex("""/(\d+)$""").find(contentRange)?.groupValues?.get(1)?.toInt() }
                ?: data.size
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println("❌ Response Error: ${error.message}")
            data.size
        }
        val totalPages = (total + options.limit - 1) / options.limit

        PaginatedResponse(
            data = data,
            total = total,
            totalPages = totalPages,
            currentPage = options.page,
            hasNext = options.page < totalPages - 1,
            hasPrev = options.page > 0,
        )
    }

    // Generic POST method
    suspend fun <B, T> post(
        endpoint: String,
        body: B,
        bodySerializer: KSerializer<B>,
        serializer: KSerializer<T>,
    ): List<T> = request {
        client.post("$API_BASE_URL$endpoint") {
            header("Prefer", "return=representation")
            setBody(TextContent(json.encodeToString(bodySerializer, body), ContentType.Application.Json))
        }.decodeList(serializer)
    }

    // Generic PUT method (sent as PATCH)
    suspend fun <B, T> put(
        endpoint: String,
        body: B,
        bodySerializer: KSerializer<B>,
        serializer: KSerializer<T>,
        filters: Map<String, Any> = emptyMap(),
    ): List<T> = request {
        client.patch("$API_BASE_URL$endpoint") {
            filters.forEach { (key, value) -> parameter(key, "eq.$value") }
            header("Prefer", "return=representation")
            setBody(TextContent(json.encodeToString(bodySerializer, body), ContentType.Application.Json))
        }.decodeList(serializer)
    }

    // Generic DELETE method (blocked by RLS but included for completeness)
    suspend fun delete(endpoint: String, filters: Map<String, Any>) {
        request {
            client.delete("$API_BASE_URL$endpoint") {
                filters.forEach { (key, value) -> parameter(key, "eq.$value") }
            }
        }
    }

    // Get single record by ID
    suspend fun <T> getById(endpoint: String, id: Int, serializer: KSerializer<T>, select: String = "*"): T? =
        request {
            client.get("$API_BASE_URL$endpoint") {
                parameter("id", "eq.$id")
                parameter("select", select)
            }.decodeList(serializer).firstOrNull()
        }

    // Get count only
    suspend fun count(endpoint: String, filters: Map<String, Any?> = emptyMap()): Int = request {
        client.get("$API_BASE_URL$endpoint") {
            parameter("select", "count")
            filters.toQueryParameters().forEach { (key, value) -> parameter(key, value) }
        }.decodeList(CountRow.serializer()).firstOrNull()?.count ?: 0
    }

    // Get distinct values for a column via RPC
    suspend fun getDistinct(tableName: String, column: String): List<String> = request {
        val body = buildJsonObject {
            put("table_name", tableName)
            put("column_name", column)
        }
        client.post("$API_BASE_URL/rpc/get_distinct_column_values") {
            setBody(TextContent(body.toString(), ContentType.Application.Json))
        }.decodeList(String.serializer())
    }

    private suspend fun <T> HttpResponse.decodeList(serializer: KSerializer<T>): List<T> =
        json.decodeFromString(ListSerializer(serializer), bodyAsText())

    // Error handling
    private suspend fun <T> request(block: suspend () -> T): T {
        try {
            return block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: ResponseException) {
            val body = error.response.bodyAsText()
            println("❌ Response Error: $body")
            val supabaseError = runCatching { json.decodeFromString(SupabaseError.serializer(), body) }.getOrNull()
            throw if (supabaseError != null) ApiException("${supabaseError.code}: ${supabaseError.message}", error) else error
        } catch (error: Exception) {
            println("❌ Response Error: ${error.message}")
            throw error
        }
    }
}

// Companies API
class CompaniesApi(private val client: SupabaseHttpClient) {
    suspend fun getAll(options: QueryOptions = QueryOptions()): PaginatedResponse<Company> =
        client.get(COMPANIES_ENDPOINT, Company.serializer(), options)

    suspend fun getById(id: Int): Company? = client.getById(COMPANIES_ENDPOINT, id, Company.serializer())

    suspend fun create(company: CompanyInput): List<Company> =
        client.post(COMPANIES_ENDPOINT, company, CompanyInput.serializer(), Company.serializer())

    suspend fun update(id: Int, company: CompanyInput): List<Company> =
        client.put(COMPANIES_ENDPOINT, company, CompanyInput.serializer(), Company.serializer(), mapOf("id" to id))

    suspend fun search(name: String, options: QueryOptions = QueryOptions()): PaginatedResponse<Company> =
        client.get(COMPANIES_ENDPOINT, Company.serializer(), options.copy(filters = mapOf("name" to "ilike.*$name*")))

    suspend fun count(): Int = client.count(COMPANIES_ENDPOINT)
}

// Users API
class UsersApi(private val client: SupabaseHttpClient) {
    suspend fun getAll(options: QueryOptions = QueryOptions()): PaginatedResponse<User> =
        client.get(USERS_ENDPOINT, User.serializer(), options)

    suspend fun getById(id: Int): User? = client.getById(USERS_ENDPOINT, id, User.serializer())

    suspend fun create(user: UserInput): List<User> =
        client.post(USERS_ENDPOINT, user.withHashedPassword(), UserInput.serializer(), User.serializer())

    suspend fun update(id: Int, user: UserInput): List<User> =
        client.put(USERS_ENDPOINT, user.withHashedPassword(), UserInput.serializer(), User.serializer(), mapOf("id" to id))

    suspend fun searchByEmail(email: String, options: QueryOptions = QueryOptions()): PaginatedResponse<User> =
        client.get(USERS_ENDPOINT, User.serializer(), options.copy(filters = mapOf("email" to "ilike.*$email*")))

    suspend fun searchByName(name: String, options: QueryOptions = QueryOptions()): PaginatedResponse<User> =
        client.get(USERS_ENDPOINT, User.serializer(), options.copy(filters = mapOf("name" to "ilike.*$name*")))

    suspend fun count(): Int = client.count(USERS_ENDPOINT)
}

// bcrypt 는 CPU 를 오래 쓰므로 메인 스레드를 막지 않도록 Dispatchers.Default 에서 실행
private suspend fun UserInput.withHashedPassword(): UserInput {
    val plainPassword = password?.takeIf { it.isNotEmpty() } ?: return this
    val hashedPassword = withContext(Dispatchers.Default) {
        BCrypt.hashPassword(plainPassword, BCrypt.generateSalt(PASSWORD_SALT_ROUNDS))
    }
    return copy(password = hashedPassword)
}

/**
 * 캠페인 목록 필터.
 * - null 이면 해당 조건 없음
 * - 빈 리스트면 결과 없음 (web 과 동일하게 `in.()` 로 전송)
 */
data class CampaignFilterParams(
    val period: List<String>? = null,
    val campaignObjective: List<String>? = null,
    val enabled: Boolean? = null,
    val name: String? = null,
)

data class CampaignFilter(val key: String, val values: List<String>)

// Campaigns API
class CampaignsApi(private val client: SupabaseHttpClient) {
    suspend fun getAll(
        options: QueryOptions = QueryOptions(),
        campaignFilters: CampaignFilterParams? = null,
    ): PaginatedResponse<Campaign> {
        val filters = options.filters.toMutableMap()
        campaignFilters?.period?.let { period -> filters["period"] = "in.(${period.joinToString(",")})" }
        campaignFilters?.campaignObjective?.let { objective ->
            filters["campaign_objective"] = "in.(${objective.joinToString(",")})"
        }
        campaignFilters?.enabled?.let { enabled -> filters["enabled"] = "eq.$enabled" }
        campaignFilters?.name?.takeIf { it.isNotEmpty() }?.let { name -> filters["name"] = "ilike.*$name*" }
        return client.get(CAMPAIGNS_ENDPOINT, Campaign.serializer(), options.copy(filters = filters))
    }

    suspend fun getFilters(): List<CampaignFilter> = coroutineScope {
        val periods = async { client.getDistinct(CAMPAIGNS_TABLE, "period") }
        val objectives = async { client.getDistinct(CAMPAIGNS_TABLE, "campaign_objective") }
        listOf(
            CampaignFilter(key = "period", values = periods.await()),
            CampaignFilter(key = "campaign_objective", values = objectives.await()),
        )
    }

    suspend fun getById(id: Int): Campaign? = client.getById(CAMPAIGNS_ENDPOINT, id, Campaign.serializer())

    suspend fun create(campaign: CampaignInput): List<Campaign> =
        client.post(CAMPAIGNS_ENDPOINT, campaign, CampaignInput.serializer(), Campaign.serializer())

    suspend fun update(id: Int, campaign: CampaignInput): List<Campaign> =
        client.put(CAMPAIGNS_ENDPOINT, campaign, CampaignInput.serializer(), Campaign.serializer(), mapOf("id" to id))

    suspend fun getByObjective(objective: String, options: QueryOptions = QueryOptions()): PaginatedResponse<Campaign> =
        client.get(CAMPAIGNS_ENDPOINT, Campaign.serializer(), options.copy(filters = mapOf("campaign_objective" to "eq.$objective")))

    suspend fun getByPeriod(period: String, options: QueryOptions = QueryOptions()): PaginatedResponse<Campaign> =
        client.get(CAMPAIGNS_ENDPOINT, Campaign.serializer(), options.copy(filters = mapOf("period" to "eq.$period")))

    suspend fun getEnabled(options: QueryOptions = QueryOptions()): PaginatedResponse<Campaign> =
        client.get(CAMPAIGNS_ENDPOINT, Campaign.serializer(), options.copy(filters = mapOf("enabled" to "eq.true")))

    suspend fun getDisabled(options: QueryOptions = QueryOptions()): PaginatedResponse<Campaign> =
        client.get(CAMPAIGNS_ENDPOINT, Campaign.serializer(), options.copy(filters = mapOf("enabled" to "eq.false")))

    suspend fun search(name: String, options: QueryOptions = QueryOptions()): PaginatedResponse<Campaign> =
        client.get(CAMPAIGNS_ENDPOINT, Campaign.serializer(), options.copy(filters = mapOf("name" to "ilike.*$name*")))

    suspend fun getTopPerforming(limit: Int = 10): PaginatedResponse<Campaign> =
        client.get(
            CAMPAIGNS_ENDPOINT,
            Campaign.serializer(),
            QueryOptions(limit = limit, orderBy = "clicks", orderDir = OrderDirection.DESC, filters = mapOf("enabled" to "eq.true")),
        )

    suspend fun getHighCtr(minCtr: Double = 1.0, options: QueryOptions = QueryOptions()): PaginatedResponse<Campaign> =
        client.get(CAMPAIGNS_ENDPOINT, Campaign.serializer(), options.copy(filters = mapOf("ctr" to "gte.$minCtr")))

    suspend fun count(): Int = client.count(CAMPAIGNS_ENDPOINT)

    suspend fun countByObjective(): Map<String, Int> = coroutineScope {
        CampaignObjective.values
            .map { objective ->
                async { objective to client.count(CAMPAIGNS_ENDPOINT, mapOf("campaign_objective" to "eq.$objective")) }
            }
            .awaitAll()
            .toMap()
    }
}

// Main API Client
class SupabaseApi {
    private val client = SupabaseHttpClient()
    val companies = CompaniesApi(client)
    val users = UsersApi(client)
    val campaigns = CampaignsApi(client)

    // Health check
    suspend fun healthCheck(): Boolean =
        try {
            companies.count()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            false
        }
}

// Export singleton instance
val supabaseApi = SupabaseApi()

// Usage Examples:
/*

// Get campaigns with pagination
val campaigns = supabaseApi.campaigns.getAll(QueryOptions(page = 0, limit = 25, orderBy = "impressions"))

// Filter campaigns (multi select)
val filtered = supabaseApi.campaigns.getAll(
    campaignFilters = CampaignFilterParams(period = listOf(CampaignPeriod.DAILY, CampaignPeriod.WEEKLY)),
)

// Filter keys & values
val filters = supabaseApi.campaigns.getFilters()

// Create user (password is hashed with bcrypt before sending)
val created = supabaseApi.users.create(UserInput(email = "user@wisebirds.ai", name = "홍길동", password = "Abcd1234!"))

// Update campaign
val updated = supabaseApi.campaigns.update(1, CampaignInput(enabled = false))

*/
