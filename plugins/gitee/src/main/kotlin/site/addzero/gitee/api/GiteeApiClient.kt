package site.addzero.gitee.api

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import site.addzero.gitee.api.model.CreatePullRequestRequest
import site.addzero.gitee.api.model.PullRequest
import site.addzero.gitee.api.model.Repo
import site.addzero.gitee.api.model.User
import java.util.concurrent.TimeUnit

/**
 * Gitee API client using OkHttp
 */
class GiteeApiClient(private val accessToken: String) {

    companion object {
        const val BASE_URL = "https://gitee.com/api/v5"
        private val gson = Gson()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Get authenticated user info
     */
    @Throws(GiteeApiException::class)
    fun getUser(): User {
        val request = buildGetRequest("/user")
        return executeRequest(request, object : TypeToken<User>() {}.type)
    }

    /**
     * Get list of repositories for authenticated user
     */
    @Throws(GiteeApiException::class)
    fun getRepos(page: Int = 1, perPage: Int = 20): List<Repo> {
        val request = buildGetRequest("/user/repos?page=$page&per_page=$perPage")
        return executeRequest(request, object : TypeToken<List<Repo>>() {}.type)
    }

    /**
     * Create a new repository
     */
    @Throws(GiteeApiException::class)
    fun createRepo(
        name: String,
        description: String? = null,
        isPrivate: Boolean = true,
        autoInit: Boolean = false
    ): Repo {
        val formBuilder = FormBody.Builder()
            .add("name", name)
            .add("private", isPrivate.toString())
            .add("auto_init", autoInit.toString())
        description?.let { formBuilder.add("description", it) }

        val request = Request.Builder()
            .url("$BASE_URL/user/repos?access_token=$accessToken")
            .post(formBuilder.build())
            .build()

        return executeRequest(request, object : TypeToken<Repo>() {}.type)
    }

    /**
     * Get repository details
     */
    @Throws(GiteeApiException::class)
    fun getRepo(owner: String, repo: String): Repo {
        val request = buildGetRequest("/repos/$owner/$repo")
        return executeRequest(request, object : TypeToken<Repo>() {}.type)
    }

    /**
     * Get list of pull requests for a repository
     */
    @Throws(GiteeApiException::class)
    fun getPullRequests(
        owner: String,
        repo: String,
        state: String = "open",
        page: Int = 1,
        perPage: Int = 20
    ): List<PullRequest> {
        val request = buildGetRequest("/repos/$owner/$repo/pulls?state=$state&page=$page&per_page=$perPage")
        return executeRequest(request, object : TypeToken<List<PullRequest>>() {}.type)
    }

    /**
     * Create a pull request
     */
    @Throws(GiteeApiException::class)
    fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        body: String?,
        head: String,
        base: String
    ): PullRequest {
        val prRequest = CreatePullRequestRequest(title, body, head, base)
        val jsonBody = RequestBody.create(JSON_MEDIA_TYPE, gson.toJson(prRequest))

        val request = Request.Builder()
            .url("$BASE_URL/repos/$owner/$repo/pulls?access_token=$accessToken")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(jsonBody)
            .build()

        return executeRequest(request, object : TypeToken<PullRequest>() {}.type)
    }

    /**
     * Delete a repository
     */
    @Throws(GiteeApiException::class)
    fun deleteRepo(owner: String, repo: String) {
        val request = Request.Builder()
            .url("$BASE_URL/repos/$owner/$repo?access_token=$accessToken")
            .header("Accept", "application/json")
            .delete()
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw GiteeApiException("Failed to delete repository: ${response.body?.string()}", response.code)
        }
    }

    /**
     * Build a GET request
     */
    private fun buildGetRequest(endpoint: String): Request {
        return Request.Builder()
            .url("$BASE_URL$endpoint&access_token=$accessToken")
            .header("Accept", "application/json")
            .get()
            .build()
    }

    /**
     * Execute request and parse response
     */
    private fun <T> executeRequest(request: Request, typeOfT: java.lang.reflect.Type): T {
        try {
            val response = httpClient.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errorMap = gson.fromJson(bodyString, Map::class.java)
                    errorMap["message"] as? String ?: bodyString
                } catch (e: Exception) {
                    bodyString
                }
                throw GiteeApiException("API Error: $errorMsg", response.code)
            }

            return gson.fromJson(bodyString, typeOfT)
        } catch (e: java.net.SocketTimeoutException) {
            throw GiteeApiException("Request timeout", cause = e)
        } catch (e: java.io.IOException) {
            throw GiteeApiException("Network error: ${e.message}", cause = e)
        }
    }
}
