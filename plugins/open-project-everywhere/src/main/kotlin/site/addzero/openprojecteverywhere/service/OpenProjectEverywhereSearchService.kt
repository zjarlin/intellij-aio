package site.addzero.openprojecteverywhere.service

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import site.addzero.openprojecteverywhere.OpenProjectEverywhereBundle
import site.addzero.openprojecteverywhere.github.OpenProjectEverywhereRemoteHostResolver
import site.addzero.openprojecteverywhere.search.HintAction
import site.addzero.openprojecteverywhere.search.SearchItem
import site.addzero.openprojecteverywhere.search.SearchResultKind
import site.addzero.openprojecteverywhere.search.SearchScope
import site.addzero.openprojecteverywhere.settings.AuthMode
import site.addzero.openprojecteverywhere.settings.CredentialsSource
import site.addzero.openprojecteverywhere.settings.OpenProjectEverywhereSettings
import site.addzero.openprojecteverywhere.settings.ProviderKind
import site.addzero.openprojecteverywhere.settings.RemoteHostConfiguration
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

@Service(Service.Level.APP)
class OpenProjectEverywhereSearchService {

    private val logger = thisLogger()

    private val settings: OpenProjectEverywhereSettings
        get() = OpenProjectEverywhereSettings.getInstance()

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("OpenProjectEverywhereSearch", 4)

    private val localCache = CacheHolder<List<LocalProject>>()
    private val repositoryCache = ConcurrentHashMap<String, CacheEntry<List<RemoteRepository>>>()

    fun search(pattern: String, indicator: ProgressIndicator, scope: SearchScope, project: Project?): List<SearchItem> {
        val query = pattern.trim()

        if (query.isBlank()) {
            return buildInitialHints(scope, project)
        }
        if (query.length < 2) {
            return listOf(typeMoreHint())
        }

        return when (scope) {
            SearchScope.OWN -> searchOwnScope(query, indicator, project)
            SearchScope.GITHUB_PUBLIC,
            SearchScope.GITLAB_PUBLIC,
            SearchScope.GITEE_PUBLIC,
            SearchScope.CUSTOM_PUBLIC -> searchPublicScope(query, indicator, scope, project)
        }
    }

    fun invalidateCaches() {
        localCache.clear()
        repositoryCache.clear()
    }

    private fun searchOwnScope(query: String, indicator: ProgressIndicator, project: Project?): List<SearchItem> {
        val localContext = buildLocalProjectContext(indicator)
        val localItems = buildLocalItems(query, localContext.projects)
        val missingProviders = mutableListOf<String>()
        val providerOutcomes = mutableListOf<CompletableFuture<ProviderOutcome>>()

        configuredRemoteHosts(project).forEach { config ->
            if (config.isConfiguredForOwnSearch()) {
                providerOutcomes += CompletableFuture.supplyAsync({
                    searchRemote(query, config, indicator, localContext, RemoteSearchMode.OWN)
                }, executor)
            } else {
                missingProviders += config.displayNameForMessages()
            }
        }

        val outcomes = collectOutcomes(providerOutcomes, indicator)
        val remoteItems = outcomes
            .flatMap { it.items }
            .sortedWith(projectComparator(query))
            .take(50)
        val hints = outcomes.mapNotNull { it.hint }.toMutableList()

        if (remoteItems.isEmpty() && localItems.isEmpty() && missingProviders.isNotEmpty()) {
            hints += SearchItem.Hint(
                title = OpenProjectEverywhereBundle.message("search.hint.configure"),
                description = missingProviders.joinToString(),
                action = HintAction.OPEN_SETTINGS
            )
        }

        val roots = localProjectsRootPaths()
        if (remoteItems.isEmpty() && localItems.isEmpty() && settings.localProjectsEnabled && localContext.projects.isEmpty() &&
            roots.isNotEmpty() && roots.none { it.exists() && it.isDirectory() }
        ) {
            hints += SearchItem.Hint(
                title = OpenProjectEverywhereBundle.message("search.hint.localRootMissing"),
                description = settings.localProjectsRoots.joinToString(),
                action = HintAction.OPEN_SETTINGS,
                isError = true
            )
        }

        if (remoteItems.isEmpty() && localItems.isEmpty() && hints.isEmpty()) {
            hints += SearchItem.Hint(
                title = OpenProjectEverywhereBundle.message("search.hint.noResults", query),
                description = null,
                action = HintAction.NONE
            )
        }

        return buildList {
            addAll(disambiguateProjectTitles(localItems + remoteItems))
            addAll(hints)
        }
    }

    private fun searchPublicScope(
        query: String,
        indicator: ProgressIndicator,
        scope: SearchScope,
        project: Project?
    ): List<SearchItem> {
        val config = configurationForScope(scope, project)
        if (config == null || !isScopeEnabled(scope) || !config.isConfiguredForPublicSearch()) {
            return listOf(scopeUnavailableHint(scope))
        }

        val localContext = buildLocalProjectContext(indicator)
        val outcome = awaitFuture(
            CompletableFuture.supplyAsync({
                searchRemote(query, config, indicator, localContext, RemoteSearchMode.PUBLIC)
            }, executor),
            indicator
        )
        val remoteItems = outcome.items.sortedWith(projectComparator(query))
        val hints = outcome.hint?.let(::listOf).orEmpty().toMutableList()

        if (remoteItems.isEmpty() && hints.isEmpty()) {
            hints += SearchItem.Hint(
                title = OpenProjectEverywhereBundle.message("search.hint.noResults", query),
                description = null,
                action = HintAction.NONE
            )
        }

        return buildList {
            addAll(disambiguateProjectTitles(remoteItems))
            addAll(hints)
        }
    }

    private fun collectOutcomes(
        providerOutcomes: List<CompletableFuture<ProviderOutcome>>,
        indicator: ProgressIndicator
    ): List<ProviderOutcome> {
        return providerOutcomes.map { future ->
            runCatching { awaitFuture(future, indicator) }.getOrElse { error ->
                if (error is ProcessCanceledException) {
                    throw error
                }

                ProviderOutcome(
                    hint = SearchItem.Hint(
                        title = OpenProjectEverywhereBundle.message("search.hint.providerError", "Search", error.message ?: "timeout"),
                        description = null,
                        action = HintAction.OPEN_SETTINGS,
                        isError = true
                    )
                )
            }
        }
    }

    private fun buildInitialHints(scope: SearchScope, project: Project?): List<SearchItem> {
        val scopeConfig = configurationForScope(scope, project)
        return when {
            scope == SearchScope.OWN && !hasAnyConfiguredOwnSource(project) -> {
                listOf(
                    SearchItem.Hint(
                        title = OpenProjectEverywhereBundle.message("search.hint.configure"),
                        description = null,
                        action = HintAction.OPEN_SETTINGS
                    )
                )
            }

            scope != SearchScope.OWN && (!isScopeEnabled(scope) || scopeConfig == null || !scopeConfig.isConfiguredForPublicSearch()) -> {
                listOf(scopeUnavailableHint(scope))
            }

            else -> listOf(typeMoreHint())
        }
    }

    private fun typeMoreHint(): SearchItem.Hint {
        return SearchItem.Hint(
            title = OpenProjectEverywhereBundle.message("search.hint.typeMore"),
            description = null,
            action = HintAction.NONE
        )
    }

    private fun scopeUnavailableHint(scope: SearchScope): SearchItem.Hint {
        return SearchItem.Hint(
            title = OpenProjectEverywhereBundle.message("search.hint.sourceTabUnavailable", scope.displayName(settings)),
            description = null,
            action = HintAction.OPEN_SETTINGS
        )
    }

    private fun hasAnyConfiguredOwnSource(project: Project?): Boolean {
        val localReady = settings.localProjectsEnabled && localProjectsRootPaths().any { it.exists() && it.isDirectory() }
        val remoteReady = configuredRemoteHosts(project).any { it.isConfiguredForOwnSearch() }
        return localReady || remoteReady
    }

    private fun isScopeEnabled(scope: SearchScope): Boolean {
        return when (scope) {
            SearchScope.OWN -> true
            SearchScope.GITHUB_PUBLIC -> settings.githubEnabled
            SearchScope.GITLAB_PUBLIC -> settings.gitlabEnabled
            SearchScope.GITEE_PUBLIC -> settings.giteeEnabled
            SearchScope.CUSTOM_PUBLIC -> settings.customEnabled
        }
    }

    private fun configurationForScope(scope: SearchScope, project: Project?): RemoteHostConfiguration? {
        return OpenProjectEverywhereRemoteHostResolver.configurationForScope(settings, project, scope)
    }

    private fun configuredRemoteHosts(project: Project?): List<RemoteHostConfiguration> {
        return OpenProjectEverywhereRemoteHostResolver.enabledRemoteHosts(settings, project)
    }

    private fun buildLocalProjectContext(indicator: ProgressIndicator): LocalProjectContext {
        val projects = loadLocalProjects(indicator)
        return LocalProjectContext(
            projects = projects,
            primaryRoot = localProjectsRootPaths().firstOrNull(),
            byRelativePath = projects.groupBy { it.relativePath.lowercase() },
            byName = projects.groupBy { it.name.lowercase() }
        )
    }

    private fun buildLocalItems(query: String, projects: List<LocalProject>): List<SearchItem.Project> {
        return projects
            .asSequence()
            .filter { matches(query, it.name, it.path.toString(), it.relativePath) }
            .sortedWith(compareBy<LocalProject> { matchScore(query, it.name, it.relativePath, it.path.toString()) }.thenBy { it.name.lowercase() })
            .take(30)
            .map {
                SearchItem.Project(
                    title = it.name,
                    baseTitle = it.name,
                    subtitle = it.path.toString(),
                    description = null,
                    categoryLabel = OpenProjectEverywhereBundle.message("search.category.local"),
                    kind = SearchResultKind.LOCAL,
                    localPath = it.path.toString(),
                    cloneUrl = null,
                    webUrl = null,
                    directoryName = it.name,
                    cloneParentRelativePath = null,
                    titleQualifier = null
                )
            }
            .toList()
    }

    private fun loadLocalProjects(indicator: ProgressIndicator): List<LocalProject> {
        val roots = localProjectsRootPaths()
        if (!settings.localProjectsEnabled || roots.isEmpty()) {
            return emptyList()
        }

        val cacheKey = roots.joinToString("|") { it.toString() }
        localCache.getIfFresh(30_000L)?.takeIf { it.key == cacheKey }?.value?.let {
            return it
        }

        indicator.checkCanceled()
        val scanned = roots
            .filter { it.exists() && it.isDirectory() }
            .flatMap { root -> scanLocalProjects(root, maxDepth = 4) }
            .distinctBy { it.path.toString() }
            .sortedBy { it.path.toString().lowercase() }
        localCache.store(cacheKey, scanned)
        return scanned
    }

    private fun localProjectsRootPaths(): List<Path> {
        return settings.localProjectsRoots.mapNotNull { root ->
            runCatching { Paths.get(root) }.getOrNull()
        }
    }

    private fun scanLocalProjects(root: Path, maxDepth: Int): List<LocalProject> {
        if (!root.exists() || !root.isDirectory()) {
            return emptyList()
        }

        val result = mutableListOf<LocalProject>()
        val queue = ArrayDeque<Pair<Path, Int>>()
        val skipDirectories = setOf("node_modules", "build", "out", "target", ".gradle")
        queue.add(root to 0)

        while (queue.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val (current, depth) = queue.removeFirst()
            if (depth > maxDepth) {
                continue
            }

            if (current != root && looksLikeProjectRoot(current)) {
                result += LocalProject(
                    name = current.name,
                    root = root,
                    path = current,
                    relativePath = normalizeRelativePath(root.relativize(current).toString())
                )
                continue
            }

            if (depth == maxDepth) {
                continue
            }

            runCatching {
                Files.newDirectoryStream(current).use { stream ->
                    stream.forEach { child ->
                        if (!Files.isDirectory(child)) {
                            return@forEach
                        }
                        val childName = child.fileName.toString()
                        if (childName in skipDirectories) {
                            return@forEach
                        }
                        if (childName.startsWith(".") && childName !in setOf(".idea", ".git")) {
                            return@forEach
                        }
                        queue.add(child to depth + 1)
                    }
                }
            }
        }

        return result.distinctBy { it.path.toString() }.sortedBy { it.relativePath.lowercase() }
    }

    private fun looksLikeProjectRoot(path: Path): Boolean {
        return listOf(".git", ".idea", "settings.gradle.kts", "settings.gradle", "pom.xml", "package.json")
            .any { path.resolve(it).exists() }
    }

    private fun searchRemote(
        query: String,
        config: RemoteHostConfiguration,
        indicator: ProgressIndicator,
        localContext: LocalProjectContext,
        mode: RemoteSearchMode
    ): ProviderOutcome {
        return runCatching {
            indicator.checkCanceled()
            val repositories = when (mode) {
                RemoteSearchMode.OWN -> searchOwnRepositories(query, config)
                RemoteSearchMode.PUBLIC -> searchPublicRepositories(query, config)
            }

            ProviderOutcome(
                items = repositories.mapNotNull { repo ->
                    toSearchItem(repo, localContext)
                }
            )
        }.getOrElse {
            if (it is ProcessCanceledException) {
                throw it
            }

            logProviderFailure(config, it)

            ProviderOutcome(
                hint = buildProviderHint(config, it)
            )
        }
    }

    private fun <T> awaitFuture(future: Future<T>, indicator: ProgressIndicator): T {
        while (true) {
            indicator.checkCanceled()

            try {
                return future.get(100, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                continue
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ProcessCanceledException(error)
            } catch (error: CancellationException) {
                throw ProcessCanceledException(error)
            } catch (error: ExecutionException) {
                throw unwrapFutureFailure(error)
            }
        }
    }

    private fun unwrapFutureFailure(error: ExecutionException): Throwable {
        val cause = error.cause ?: return error
        return when (cause) {
            is ProcessCanceledException -> cause
            is Error -> cause
            is RuntimeException -> cause
            else -> RuntimeException(cause)
        }
    }

    private fun toSearchItem(repo: RemoteRepository, localContext: LocalProjectContext): SearchItem.Project? {
        val localPath = localContext.findLocalPath(repo)
        if (repo.cloneUrl == null && localPath == null) {
            return null
        }

        return SearchItem.Project(
            title = repo.name,
            baseTitle = repo.name,
            subtitle = repo.subtitle(),
            description = repo.description,
            categoryLabel = repo.categoryLabel,
            kind = repo.kind,
            localPath = localPath,
            cloneUrl = repo.cloneUrl,
            webUrl = repo.webUrl,
            directoryName = repo.directoryName,
            cloneParentRelativePath = repo.cloneParentRelativePath,
            titleQualifier = repo.ownerDisplayName
        )
    }

    private fun searchOwnRepositories(query: String, config: RemoteHostConfiguration): List<RemoteRepository> {
        val cacheKey = "own|${config.id}"
        val repositories = repositoryCache[cacheKey]?.takeIf { it.isFresh(5 * 60_000L) }?.value
            ?: loadOwnRepositories(config).also { repositoryCache[cacheKey] = CacheEntry(cacheKey, it) }

        return repositories.filter {
            matches(query, it.name, it.fullName, it.description.orEmpty())
        }
    }

    private fun searchPublicRepositories(query: String, config: RemoteHostConfiguration): List<RemoteRepository> {
        val cacheKey = "public|${config.id}|${query.lowercase()}"
        repositoryCache[cacheKey]?.takeIf { it.isFresh(60_000L) }?.let { return it.value }

        val repositories = when (config.kind) {
            ProviderKind.GITHUB -> searchGithubPublic(query, config)
            ProviderKind.GITLAB -> searchGitlabPublic(query, config)
            ProviderKind.GITEE -> searchGiteePublic(query, config)
        }
        repositoryCache[cacheKey] = CacheEntry(cacheKey, repositories)
        return repositories
    }

    private fun loadOwnRepositories(config: RemoteHostConfiguration): List<RemoteRepository> {
        return when (config.kind) {
            ProviderKind.GITHUB -> loadGithubOwnRepositories(config)
            ProviderKind.GITLAB -> loadGitlabOwnRepositories(config)
            ProviderKind.GITEE -> loadGiteeOwnRepositories(config)
        }
    }

    private fun loadGithubOwnRepositories(config: RemoteHostConfiguration): List<RemoteRepository> {
        val apiBase = githubApiBase(config)
        return loadPagedResults { page ->
            val url = apiBase.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addPathSegment("user")
                ?.addPathSegment("repos")
                ?.addQueryParameter("affiliation", "owner,collaborator,organization_member")
                ?.addQueryParameter("sort", "updated")
                ?.addQueryParameter("per_page", "100")
                ?.addQueryParameter("page", page.toString())
                ?.build()
                ?.toString()
                ?: throw IllegalArgumentException("Invalid GitHub URL")

            val requestBuilder = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
            applyAuth(requestBuilder, config)
            executeRequest(requestBuilder.build(), Array<GithubRepoPayload>::class.java).toList()
        }.mapNotNull { it.toGithubRepository(config) }
    }

    private fun loadGitlabOwnRepositories(config: RemoteHostConfiguration): List<RemoteRepository> {
        return loadPagedResults { page ->
            val url = "${config.normalizedBaseUrl}/api/v4/projects".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("membership", "true")
                ?.addQueryParameter("simple", "true")
                ?.addQueryParameter("order_by", "last_activity_at")
                ?.addQueryParameter("sort", "desc")
                ?.addQueryParameter("per_page", "100")
                ?.addQueryParameter("page", page.toString())
                ?.build()
                ?.toString()
                ?: throw IllegalArgumentException("Invalid GitLab URL")

            val requestBuilder = Request.Builder().url(url)
            applyAuth(requestBuilder, config)
            executeRequest(requestBuilder.build(), Array<GitlabProjectPayload>::class.java).toList()
        }.mapNotNull { it.toGitlabRepository(config) }
    }

    private fun loadGiteeOwnRepositories(config: RemoteHostConfiguration): List<RemoteRepository> {
        return loadPagedResults { page ->
            val builder = "${config.normalizedBaseUrl}/api/v5".toHttpUrlOrNull()
                ?.newBuilder()
                ?: throw IllegalArgumentException("Invalid Gitee URL")

            val url = if (config.authMode == AuthMode.TOKEN) {
                builder
                    .addPathSegment("user")
                    .addPathSegment("repos")
                    .addQueryParameter("access_token", config.secret)
                    .addQueryParameter("sort", "updated")
                    .addQueryParameter("per_page", "100")
                    .addQueryParameter("page", page.toString())
                    .build()
                    .toString()
            } else {
                builder
                    .addPathSegment("users")
                    .addPathSegment(config.username)
                    .addPathSegment("repos")
                    .addQueryParameter("sort", "updated")
                    .addQueryParameter("per_page", "100")
                    .addQueryParameter("page", page.toString())
                    .build()
                    .toString()
            }

            executeRequest(Request.Builder().url(url).build(), Array<GiteeRepoPayload>::class.java).toList()
        }.mapNotNull { it.toGiteeRepository(config) }
    }

    private fun searchGithubPublic(query: String, config: RemoteHostConfiguration): List<RemoteRepository> {
        val apiBase = githubApiBase(config)
        val url = apiBase.toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment("search")
            ?.addPathSegment("repositories")
            ?.addQueryParameter("q", "$query in:name")
            ?.addQueryParameter("per_page", "20")
            ?.addQueryParameter("sort", "updated")
            ?.addQueryParameter("order", "desc")
            ?.build()
            ?.toString()
            ?: throw IllegalArgumentException("Invalid GitHub URL")

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
        applyAuth(requestBuilder, config)

        val payload = executeRequest(requestBuilder.build(), GithubSearchResponse::class.java)
        return payload.items.mapNotNull { it.toGithubRepository(config) }
    }

    private fun searchGitlabPublic(query: String, config: RemoteHostConfiguration): List<RemoteRepository> {
        val url = "${config.normalizedBaseUrl}/api/v4/projects".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("search", query)
            ?.addQueryParameter("simple", "true")
            ?.addQueryParameter("per_page", "20")
            ?.addQueryParameter("order_by", "last_activity_at")
            ?.addQueryParameter("sort", "desc")
            ?.build()
            ?.toString()
            ?: throw IllegalArgumentException("Invalid GitLab URL")

        val requestBuilder = Request.Builder().url(url)
        applyAuth(requestBuilder, config)

        return executeRequest(requestBuilder.build(), Array<GitlabProjectPayload>::class.java)
            .toList()
            .mapNotNull { it.toGitlabRepository(config) }
    }

    private fun searchGiteePublic(query: String, config: RemoteHostConfiguration): List<RemoteRepository> {
        val urlBuilder = "${config.normalizedBaseUrl}/api/v5".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegment("search")
            ?.addPathSegment("repositories")
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("sort", "updated_at")
            ?.addQueryParameter("order", "desc")
            ?.addQueryParameter("per_page", "20")
            ?: throw IllegalArgumentException("Invalid Gitee URL")

        if (config.authMode == AuthMode.TOKEN && config.secret.isNotBlank()) {
            urlBuilder.addQueryParameter("access_token", config.secret)
        }

        val payload = executeRequest(
            Request.Builder().url(urlBuilder.build()).build(),
            GiteeSearchResponse::class.java
        )
        return payload.items.mapNotNull { it.toGiteeRepository(config) }
    }

    private fun githubApiBase(config: RemoteHostConfiguration): String {
        return if (config.normalizedBaseUrl.equals("https://github.com", ignoreCase = true)) {
            "https://api.github.com"
        } else {
            "${config.normalizedBaseUrl}/api/v3"
        }
    }

    private fun categoryLabel(config: RemoteHostConfiguration, defaultKind: SearchResultKind): String {
        return if (config.id == "custom") {
            OpenProjectEverywhereBundle.message("search.category.custom.named", config.displayNameForMessages())
        } else {
            when (defaultKind) {
                SearchResultKind.LOCAL -> OpenProjectEverywhereBundle.message("search.category.local")
                SearchResultKind.GITHUB -> OpenProjectEverywhereBundle.message("search.category.github")
                SearchResultKind.GITLAB -> OpenProjectEverywhereBundle.message("search.category.gitlab")
                SearchResultKind.GITEE -> OpenProjectEverywhereBundle.message("search.category.gitee")
                SearchResultKind.CUSTOM -> OpenProjectEverywhereBundle.message("search.category.custom")
            }
        }
    }

    private fun applyAuth(requestBuilder: Request.Builder, config: RemoteHostConfiguration) {
        when (config.authMode) {
            AuthMode.TOKEN -> {
                if (config.secret.isBlank()) return
                when (config.kind) {
                    ProviderKind.GITHUB -> requestBuilder.header("Authorization", "Bearer ${config.secret}")
                    ProviderKind.GITLAB -> requestBuilder.header("PRIVATE-TOKEN", config.secret)
                    ProviderKind.GITEE -> Unit
                }
            }

            AuthMode.USERNAME_PASSWORD -> {
                if (config.username.isBlank() || config.secret.isBlank()) return
                val credentials = okhttp3.Credentials.basic(config.username, config.secret)
                requestBuilder.header("Authorization", credentials)
            }
        }
    }

    private fun buildProviderHint(config: RemoteHostConfiguration, error: Throwable): SearchItem.Hint {
        val providerName = config.displayNameForMessages()
        val githubAuthFailure = config.kind == ProviderKind.GITHUB &&
            error is RemoteApiException &&
            (error.kind == RemoteApiFailureKind.AUTH || error.kind == RemoteApiFailureKind.FORBIDDEN)
        val title = when (error) {
            is RemoteApiException -> when (error.kind) {
                RemoteApiFailureKind.AUTH ->
                    if (githubAuthFailure) {
                        OpenProjectEverywhereBundle.message("search.hint.githubAuth")
                    } else {
                        OpenProjectEverywhereBundle.message("search.hint.providerAuth", providerName)
                    }

                RemoteApiFailureKind.FORBIDDEN ->
                    if (githubAuthFailure) {
                        OpenProjectEverywhereBundle.message("search.hint.githubForbidden")
                    } else {
                        OpenProjectEverywhereBundle.message("search.hint.providerForbidden", providerName)
                    }

                RemoteApiFailureKind.NOT_FOUND ->
                    OpenProjectEverywhereBundle.message("search.hint.providerNotFound", providerName)

                RemoteApiFailureKind.RATE_LIMIT ->
                    OpenProjectEverywhereBundle.message("search.hint.providerRateLimit", providerName)

                RemoteApiFailureKind.UNAVAILABLE ->
                    OpenProjectEverywhereBundle.message("search.hint.providerUnavailable", providerName)

                RemoteApiFailureKind.HTTP ->
                    OpenProjectEverywhereBundle.message("search.hint.providerHttpError", providerName, error.statusCode.toString())
            }

            is IllegalArgumentException ->
                OpenProjectEverywhereBundle.message("search.hint.providerInvalidUrl", providerName)

            is IOException ->
                OpenProjectEverywhereBundle.message("search.hint.providerUnavailable", providerName)

            else ->
                OpenProjectEverywhereBundle.message("search.hint.providerUnexpected", providerName)
        }

        return SearchItem.Hint(
            title = title,
            description = if (githubAuthFailure) {
                OpenProjectEverywhereBundle.message("search.hint.githubAuth.description")
            } else {
                null
            },
            action = providerHintAction(config, error),
            isError = true
        )
    }

    private fun providerHintAction(config: RemoteHostConfiguration, error: Throwable): HintAction {
        val isGithubAuthIssue = config.kind == ProviderKind.GITHUB &&
            config.credentialsSource == CredentialsSource.IDE_GITHUB &&
            error is RemoteApiException &&
            (error.kind == RemoteApiFailureKind.AUTH || error.kind == RemoteApiFailureKind.FORBIDDEN)

        return if (isGithubAuthIssue || config.kind == ProviderKind.GITHUB && error is RemoteApiException &&
            (error.kind == RemoteApiFailureKind.AUTH || error.kind == RemoteApiFailureKind.FORBIDDEN)
        ) {
            HintAction.OPEN_GITHUB_SETTINGS
        } else {
            HintAction.OPEN_SETTINGS
        }
    }

    private fun logProviderFailure(config: RemoteHostConfiguration, error: Throwable) {
        val providerName = config.displayNameForMessages()
        val message = "Failed to load repositories from $providerName"
        when (error) {
            is RemoteApiException,
            is IOException,
            is IllegalArgumentException -> logger.info("$message: ${error.message}")
            else -> logger.warn(message, error)
        }
    }

    private fun <T> executeRequest(request: Request, responseType: Class<T>): T {
        val response = httpClient.newCall(request).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                throw RemoteApiException(
                    statusCode = it.code,
                    kind = classifyApiFailure(it.code, body)
                )
            }
            val parsed = gson.fromJson(body, responseType)
            return parsed ?: throw IOException("Empty response body")
        }
    }

    private fun classifyApiFailure(statusCode: Int, responseBody: String): RemoteApiFailureKind {
        val normalizedBody = responseBody.lowercase()
        return when {
            statusCode == 401 -> RemoteApiFailureKind.AUTH
            statusCode == 403 && normalizedBody.contains("rate limit") -> RemoteApiFailureKind.RATE_LIMIT
            statusCode == 403 -> RemoteApiFailureKind.FORBIDDEN
            statusCode == 404 -> RemoteApiFailureKind.NOT_FOUND
            statusCode == 429 -> RemoteApiFailureKind.RATE_LIMIT
            statusCode == 408 || statusCode in 500..599 -> RemoteApiFailureKind.UNAVAILABLE
            else -> RemoteApiFailureKind.HTTP
        }
    }

    private fun <T> loadPagedResults(
        pageSize: Int = 100,
        maxPages: Int = 10,
        fetchPage: (Int) -> List<T>
    ): List<T> {
        val results = mutableListOf<T>()
        for (page in 1..maxPages) {
            val pageItems = fetchPage(page)
            if (pageItems.isEmpty()) break
            results += pageItems
            if (pageItems.size < pageSize) break
        }
        return results
    }

    private fun disambiguateProjectTitles(items: List<SearchItem.Project>): List<SearchItem.Project> {
        val duplicateTitles = items
            .groupingBy { it.baseTitle.lowercase() }
            .eachCount()
            .filterValues { it > 1 }

        return items.map { item ->
            if (duplicateTitles.containsKey(item.baseTitle.lowercase()) && !item.titleQualifier.isNullOrBlank()) {
                item.copy(title = "${item.baseTitle} (${item.titleQualifier})")
            } else {
                item.copy(title = item.baseTitle)
            }
        }
    }

    private fun projectComparator(query: String): Comparator<SearchItem.Project> {
        return compareBy<SearchItem.Project> {
            matchScore(query, it.baseTitle, it.subtitle, it.description.orEmpty())
        }.thenBy {
            kindRank(it.kind)
        }.thenBy {
            it.baseTitle.lowercase()
        }.thenBy {
            it.subtitle.lowercase()
        }
    }

    private fun matches(query: String, vararg values: String): Boolean {
        val normalizedQuery = query.lowercase()
        return values.any { value ->
            val normalizedValue = value.lowercase()
            normalizedValue.contains(normalizedQuery) || isSubsequence(normalizedQuery, normalizedValue)
        }
    }

    private fun isSubsequence(query: String, target: String): Boolean {
        if (query.isBlank()) return true
        var index = 0
        for (char in target) {
            if (char == query[index]) {
                index++
                if (index == query.length) return true
            }
        }
        return false
    }

    private fun matchScore(query: String, primary: String, vararg extra: String): Int {
        val normalizedQuery = query.lowercase()
        val normalizedPrimary = primary.lowercase()
        val joined = (listOf(primary) + extra.toList()).joinToString(" ").lowercase()
        return when {
            normalizedPrimary == normalizedQuery -> 0
            normalizedPrimary.startsWith(normalizedQuery) -> 1
            joined.contains(normalizedQuery) -> 2
            isSubsequence(normalizedQuery, normalizedPrimary) -> 3
            else -> 4
        }
    }

    private fun kindRank(kind: SearchResultKind): Int {
        return when (kind) {
            SearchResultKind.LOCAL -> 0
            SearchResultKind.GITHUB -> 1
            SearchResultKind.GITLAB -> 2
            SearchResultKind.GITEE -> 3
            SearchResultKind.CUSTOM -> 4
        }
    }

    private fun normalizeRelativePath(path: String): String {
        return path.replace('\\', '/').trim('/')
    }

    private fun RemoteHostConfiguration.displayNameForMessages(): String {
        return displayName.ifBlank {
            OpenProjectEverywhereBundle.message("settings.custom.display.default")
        }
    }

    private fun RemoteHostConfiguration.isConfiguredForOwnSearch(): Boolean {
        return when (authMode) {
            AuthMode.TOKEN -> secret.isNotBlank()
            AuthMode.USERNAME_PASSWORD -> username.isNotBlank() && secret.isNotBlank()
        }
    }

    private fun RemoteHostConfiguration.isConfiguredForPublicSearch(): Boolean {
        if (id == "custom") {
            return displayName.isNotBlank() && normalizedBaseUrl.isNotBlank()
        }
        return normalizedBaseUrl.isNotBlank()
    }

    private fun buildRemoteRepository(
        config: RemoteHostConfiguration,
        defaultKind: SearchResultKind,
        name: String,
        fullName: String,
        description: String?,
        cloneUrl: String?,
        webUrl: String?,
        visibility: String
    ): RemoteRepository {
        return RemoteRepository(
            kind = if (config.id == "custom") SearchResultKind.CUSTOM else defaultKind,
            categoryLabel = categoryLabel(config, defaultKind),
            name = name,
            fullName = fullName,
            description = description?.trim()?.takeIf { it.isNotBlank() },
            cloneUrl = cloneUrl?.takeIf { it.isNotBlank() },
            webUrl = webUrl?.takeIf { it.isNotBlank() },
            visibility = visibility
        )
    }

    private fun GithubRepoPayload.toGithubRepository(config: RemoteHostConfiguration): RemoteRepository? {
        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: fullName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: return null
        val resolvedFullName = fullName?.takeIf { it.isNotBlank() } ?: resolvedName
        return buildRemoteRepository(
            config = config,
            defaultKind = SearchResultKind.GITHUB,
            name = resolvedName,
            fullName = resolvedFullName,
            description = description,
            cloneUrl = cloneUrl ?: inferCloneUrl(config.normalizedBaseUrl, resolvedFullName),
            webUrl = htmlUrl ?: inferWebUrl(config.normalizedBaseUrl, resolvedFullName),
            visibility = if (isPrivate == true) {
                OpenProjectEverywhereBundle.message("search.visibility.private")
            } else {
                OpenProjectEverywhereBundle.message("search.visibility.public")
            }
        )
    }

    private fun GitlabProjectPayload.toGitlabRepository(config: RemoteHostConfiguration): RemoteRepository? {
        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: pathWithNamespace?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: path?.takeIf { it.isNotBlank() }
            ?: return null
        val resolvedFullName = pathWithNamespace?.takeIf { it.isNotBlank() } ?: resolvedName
        return buildRemoteRepository(
            config = config,
            defaultKind = SearchResultKind.GITLAB,
            name = resolvedName,
            fullName = resolvedFullName,
            description = description,
            cloneUrl = httpUrlToRepo ?: inferCloneUrl(config.normalizedBaseUrl, resolvedFullName),
            webUrl = webUrl ?: inferWebUrl(config.normalizedBaseUrl, resolvedFullName),
            visibility = when (visibility?.lowercase()) {
                "private" -> OpenProjectEverywhereBundle.message("search.visibility.private")
                "internal" -> OpenProjectEverywhereBundle.message("search.visibility.internal")
                "public" -> OpenProjectEverywhereBundle.message("search.visibility.public")
                else -> OpenProjectEverywhereBundle.message("search.visibility.unknown")
            }
        )
    }

    private fun GiteeRepoPayload.toGiteeRepository(config: RemoteHostConfiguration): RemoteRepository? {
        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: fullName?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: return null
        val resolvedFullName = fullName?.takeIf { it.isNotBlank() } ?: resolvedName
        return buildRemoteRepository(
            config = config,
            defaultKind = SearchResultKind.GITEE,
            name = resolvedName,
            fullName = resolvedFullName,
            description = description,
            cloneUrl = cloneUrl ?: inferCloneUrl(config.normalizedBaseUrl, resolvedFullName),
            webUrl = htmlUrl ?: inferWebUrl(config.normalizedBaseUrl, resolvedFullName),
            visibility = if (isPrivate == true) {
                OpenProjectEverywhereBundle.message("search.visibility.private")
            } else {
                OpenProjectEverywhereBundle.message("search.visibility.public")
            }
        )
    }

    private fun inferCloneUrl(baseUrl: String, fullName: String): String {
        return "${baseUrl.trimEnd('/')}/${fullName.trim('/')}.git"
    }

    private fun inferWebUrl(baseUrl: String, fullName: String): String {
        return "${baseUrl.trimEnd('/')}/${fullName.trim('/')}"
    }

    private data class LocalProject(
        val name: String,
        val root: Path,
        val path: Path,
        val relativePath: String
    )

    private data class LocalProjectContext(
        val projects: List<LocalProject>,
        val primaryRoot: Path?,
        val byRelativePath: Map<String, List<LocalProject>>,
        val byName: Map<String, List<LocalProject>>
    ) {
        fun findLocalPath(repo: RemoteRepository): String? {
            selectPreferredMatch(byRelativePath[repo.cloneRelativePath.lowercase()])?.let { return it.path.toString() }
            return selectPreferredMatch(byName[repo.name.lowercase()])?.path?.toString()
        }

        private fun selectPreferredMatch(candidates: List<LocalProject>?): LocalProject? {
            if (candidates.isNullOrEmpty()) {
                return null
            }
            if (candidates.size == 1) {
                return candidates.first()
            }
            return candidates.firstOrNull { it.root == primaryRoot } ?: candidates.first()
        }
    }

    private data class RemoteRepository(
        val kind: SearchResultKind,
        val categoryLabel: String,
        val name: String,
        val fullName: String,
        val description: String?,
        val cloneUrl: String?,
        val webUrl: String?,
        val visibility: String
    ) {
        val ownerDisplayName: String?
            get() = fullName.substringBeforeLast('/', "").ifBlank { null }

        val cloneRelativePath: String
            get() {
                val segments = fullName.split('/')
                    .mapNotNull { segment ->
                        val sanitized = sanitizePathSegment(segment)
                        sanitized.takeIf { it.isNotBlank() }
                    }
                return if (segments.isNotEmpty()) {
                    segments.joinToString("/")
                } else {
                    sanitizePathSegment(name)
                }
            }

        val cloneParentRelativePath: String?
            get() = cloneRelativePath.substringBeforeLast("/", "").ifBlank { null }

        val directoryName: String
            get() = cloneRelativePath.substringAfterLast("/")

        fun subtitle(): String {
            return OpenProjectEverywhereBundle.message("search.project.subtitle", fullName, visibility)
        }

        companion object {
            private fun sanitizePathSegment(segment: String): String {
                return segment
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    .trim()
                    .ifBlank { "repo" }
            }
        }
    }

    private data class ProviderOutcome(
        val items: List<SearchItem.Project> = emptyList(),
        val hint: SearchItem.Hint? = null
    )

    private class RemoteApiException(
        val statusCode: Int,
        val kind: RemoteApiFailureKind
    ) : IOException("HTTP $statusCode")

    private enum class RemoteApiFailureKind {
        AUTH,
        FORBIDDEN,
        NOT_FOUND,
        RATE_LIMIT,
        UNAVAILABLE,
        HTTP
    }

    private data class CacheEntry<T>(
        val key: String,
        val value: T,
        val loadedAt: Long = System.currentTimeMillis()
    ) {
        fun isFresh(ttlMs: Long): Boolean = System.currentTimeMillis() - loadedAt <= ttlMs
    }

    private class CacheHolder<T> {
        @Volatile
        private var entry: CacheEntry<T>? = null

        fun getIfFresh(ttlMs: Long): CacheEntry<T>? = entry?.takeIf { it.isFresh(ttlMs) }

        fun store(key: String, value: T) {
            entry = CacheEntry(key, value)
        }

        fun clear() {
            entry = null
        }
    }

    private data class GithubSearchResponse(
        val items: List<GithubRepoPayload> = emptyList()
    )

    private data class GiteeSearchResponse(
        val items: List<GiteeRepoPayload> = emptyList()
    )

    private data class GithubRepoPayload(
        val name: String?,
        @SerializedName("full_name")
        val fullName: String?,
        val description: String?,
        @SerializedName("clone_url")
        val cloneUrl: String?,
        @SerializedName("html_url")
        val htmlUrl: String?,
        @SerializedName("private")
        val isPrivate: Boolean?
    )

    private data class GitlabProjectPayload(
        val name: String?,
        val path: String?,
        @SerializedName("path_with_namespace")
        val pathWithNamespace: String?,
        val description: String?,
        @SerializedName("http_url_to_repo")
        val httpUrlToRepo: String?,
        @SerializedName("web_url")
        val webUrl: String?,
        val visibility: String?
    )

    private data class GiteeRepoPayload(
        val name: String?,
        @SerializedName("full_name")
        val fullName: String?,
        val description: String?,
        @SerializedName("clone_url")
        val cloneUrl: String?,
        @SerializedName("html_url")
        val htmlUrl: String?,
        @SerializedName("private")
        val isPrivate: Boolean?
    )

    private enum class RemoteSearchMode {
        OWN,
        PUBLIC
    }

    companion object {
        fun getInstance(): OpenProjectEverywhereSearchService {
            return ApplicationManager.getApplication().getService(OpenProjectEverywhereSearchService::class.java)
        }
    }
}
