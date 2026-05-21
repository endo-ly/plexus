package dev.muxport.shared.core.domain.repository

import dev.muxport.shared.core.data.repository.GitRepositoryImpl
import dev.muxport.shared.core.data.repository.internal.RepositoryClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * GitRepositoryImplのテスト
 *
 * Ktor MockEngineを使用してHTTPリクエスト/レスポンスをテストします。
 */
class GitRepositoryImplTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val baseUrl = "http://test.example.com"
    private val apiKey = "test-api-key"

    /**
     * テスト用HttpClientを作成する
     */
    private fun createMockHttpClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

    /**
     * テスト用RepositoryClientを作成する
     */
    private fun createMockRepositoryClient(engine: MockEngine): RepositoryClient {
        val httpClient = createMockHttpClient(engine)
        return RepositoryClient(httpClient, baseUrl, apiKey)
    }

    // ==================== getStatus() テスト ====================

    @Test
    fun `getStatus - success returns branch and changes`() =
        runTest {
            // Arrange: Gitステータスレスポンスのモック設定
            val sessionId = "agent-0001"
            val expectedResponse =
                """
                {
                    "branch": "main",
                    "staged": [
                        {"path": "src/Main.kt", "status": "modified"}
                    ],
                    "unstaged": [
                        {"path": "README.md", "status": "modified"}
                    ],
                    "untracked": [
                        {"path": "new_file.txt", "status": "untracked"}
                    ]
                }
                """.trimIndent()

            val mockEngine =
                MockEngine {
                    assertEquals(HttpMethod.Get, it.method)
                    assertEquals("$baseUrl/api/v1/git/sessions/$sessionId/status", it.url.toString())
                    assertEquals(apiKey, it.headers["X-API-Key"])

                    respond(
                        content = expectedResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = GitRepositoryImpl(repositoryClient)

            // Act
            val result = repository.getStatus(sessionId)

            // Assert
            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertEquals("main", response.branch)
            assertEquals(1, response.staged.size)
            assertEquals("src/Main.kt", response.staged[0].path)
            assertEquals("modified", response.staged[0].status)
            assertEquals(1, response.unstaged.size)
            assertEquals("README.md", response.unstaged[0].path)
            assertEquals(1, response.untracked.size)
            assertEquals("new_file.txt", response.untracked[0].path)
        }

    @Test
    fun `getStatus - not a git repo returns 422`() =
        runTest {
            // Arrange: Gitリポジトリ不在エラーのモック設定
            val sessionId = "agent-0001"

            val mockEngine =
                MockEngine {
                    respond(
                        content = """{"detail": "not_a_git_repo: no .git directory found"}""",
                        status = HttpStatusCode.UnprocessableEntity,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = GitRepositoryImpl(repositoryClient)

            // Act
            val result = repository.getStatus(sessionId)

            // Assert
            assertTrue(result.isFailure)
            val error = assertIs<ApiError.HttpError>(result.exceptionOrNull()!!)
            assertEquals(422, error.code)
        }

    // ==================== getDiff() テスト ====================

    @Test
    fun `getDiff - success returns files`() =
        runTest {
            // Arrange: Git差分レスポンスのモック設定
            val sessionId = "agent-0001"
            val expectedResponse =
                """
                {
                    "files": [
                        {
                            "path": "src/Main.kt",
                            "additions": 10,
                            "deletions": 3,
                            "patch": "@@ -1,5 +1,12 @@\n+fun newFunction() {\n+    return 42\n+}"
                        },
                        {
                            "path": "README.md",
                            "additions": 2,
                            "deletions": 0,
                            "patch": "@@ -1 +1,3 @@\n+# Updated\n+Added new section"
                        }
                    ]
                }
                """.trimIndent()

            val mockEngine =
                MockEngine {
                    assertEquals(HttpMethod.Get, it.method)

                    respond(
                        content = expectedResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = GitRepositoryImpl(repositoryClient)

            // Act
            val result = repository.getDiff(sessionId)

            // Assert
            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertEquals(2, response.files.size)
            assertEquals("src/Main.kt", response.files[0].path)
            assertEquals(10, response.files[0].additions)
            assertEquals(3, response.files[0].deletions)
            assertEquals("@@ -1,5 +1,12 @@\n+fun newFunction() {\n+    return 42\n+}", response.files[0].patch)
        }

    @Test
    fun `getDiff - large stat only with null patch`() =
        runTest {
            // Arrange: 大きな差分（patch null）のモック設定
            val sessionId = "agent-0001"
            val expectedResponse =
                """
                {
                    "files": [
                        {
                            "path": "large_file.data",
                            "additions": 500,
                            "deletions": 200
                        }
                    ]
                }
                """.trimIndent()

            val mockEngine =
                MockEngine {
                    respond(
                        content = expectedResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = GitRepositoryImpl(repositoryClient)

            // Act
            val result = repository.getDiff(sessionId, target = "staged")

            // Assert
            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertEquals(1, response.files.size)
            assertEquals("large_file.data", response.files[0].path)
            assertEquals(500, response.files[0].additions)
            assertEquals(200, response.files[0].deletions)
            assertEquals(null, response.files[0].patch)
        }

    @Test
    fun `getDiff - specific file path`() =
        runTest {
            // Arrange: 特定ファイルパス指定のモック設定
            val sessionId = "agent-0001"
            val expectedResponse =
                """
                {
                    "files": [
                        {
                            "path": "src/Main.kt",
                            "additions": 5,
                            "deletions": 1,
                            "patch": "@@ -1 +1,5 @@"
                        }
                    ]
                }
                """.trimIndent()

            val mockEngine =
                MockEngine {
                    assertEquals(HttpMethod.Get, it.method)
                    val url = it.url.toString()
                    assertTrue(url.contains("path=src%2FMain.kt"))

                    respond(
                        content = expectedResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = GitRepositoryImpl(repositoryClient)

            // Act
            val result = repository.getDiff(sessionId, path = "src/Main.kt")

            // Assert
            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertEquals(1, response.files.size)
            assertEquals("src/Main.kt", response.files[0].path)
        }

    // ==================== getLog() テスト ====================

    @Test
    fun `getLog - success returns commits`() =
        runTest {
            // Arrange: Gitログレスポンスのモック設定
            val sessionId = "agent-0001"
            val expectedResponse =
                """
                {
                    "commits": [
                        {
                            "sha": "abc123def456789012345678901234567890abcd",
                            "short_sha": "abc123d",
                            "message": "feat: add file browser",
                            "author": "Developer",
                            "date": "2025-06-01T12:00:00Z"
                        },
                        {
                            "sha": "def456abc789012345678901234567890abcdef1",
                            "short_sha": "def456a",
                            "message": "fix: resolve path traversal",
                            "author": "Developer",
                            "date": "2025-06-01T11:00:00Z"
                        }
                    ]
                }
                """.trimIndent()

            val mockEngine =
                MockEngine {
                    assertEquals(HttpMethod.Get, it.method)
                    val url = it.url.toString()
                    assertTrue(url.contains("count=5"))

                    respond(
                        content = expectedResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = GitRepositoryImpl(repositoryClient)

            // Act
            val result = repository.getLog(sessionId, count = 5)

            // Assert
            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertEquals(2, response.commits.size)
            assertEquals("abc123def456789012345678901234567890abcd", response.commits[0].sha)
            assertEquals("abc123d", response.commits[0].shortSha)
            assertEquals("feat: add file browser", response.commits[0].message)
            assertEquals("Developer", response.commits[0].author)
            assertEquals("2025-06-01T12:00:00Z", response.commits[0].date)
        }

    // ==================== getCommitDetail() テスト ====================

    @Test
    fun `getCommitDetail - success returns commit and diff`() =
        runTest {
            // Arrange: コミット詳細レスポンスのモック設定
            val sessionId = "agent-0001"
            val sha = "abc123d"
            val expectedResponse =
                """
                {
                    "commit": {
                        "sha": "abc123def456789012345678901234567890abcd",
                        "message": "feat: add file browser",
                        "author": "Developer",
                        "date": "2025-06-01T12:00:00Z"
                    },
                    "diff": {
                        "files": [
                            {
                                "path": "src/Main.kt",
                                "additions": 10,
                                "deletions": 3,
                                "patch": "@@ -1,5 +1,12 @@"
                            }
                        ]
                    }
                }
                """.trimIndent()

            val mockEngine =
                MockEngine {
                    assertEquals(HttpMethod.Get, it.method)
                    assertEquals("$baseUrl/api/v1/git/sessions/$sessionId/commits/$sha", it.url.toString())
                    assertEquals(apiKey, it.headers["X-API-Key"])

                    respond(
                        content = expectedResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = GitRepositoryImpl(repositoryClient)

            // Act
            val result = repository.getCommitDetail(sessionId, sha)

            // Assert
            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertEquals("abc123def456789012345678901234567890abcd", response.commit.sha)
            assertEquals("feat: add file browser", response.commit.message)
            assertEquals("Developer", response.commit.author)
            assertEquals(1, response.diff.files.size)
            assertEquals("src/Main.kt", response.diff.files[0].path)
            assertEquals(10, response.diff.files[0].additions)
        }
}
