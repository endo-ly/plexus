package dev.muxport.shared.core.domain.repository

import dev.muxport.shared.core.data.repository.FileRepositoryImpl
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
 * FileRepositoryImplのテスト
 *
 * Ktor MockEngineを使用してHTTPリクエスト/レスポンスをテストします。
 */
class FileRepositoryImplTest {
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

    // ==================== browseFiles() テスト ====================

    @Test
    fun `browseFiles - success returns entries`() =
        runTest {
            // Arrange: ファイル一覧レスポンスのモック設定
            val sessionId = "agent-0001"
            val expectedResponse =
                """
                {
                    "path": "/home/user/project",
                    "entries": [
                        {
                            "name": "src",
                            "type": "directory",
                            "size": 4096,
                            "modified": "2025-06-01T12:00:00Z"
                        },
                        {
                            "name": "README.md",
                            "type": "file",
                            "size": 1024,
                            "modified": "2025-06-01T11:00:00Z"
                        },
                        {
                            "name": "link",
                            "type": "symlink",
                            "size": 0,
                            "modified": "2025-06-01T10:00:00Z"
                        }
                    ]
                }
                """.trimIndent()

            val mockEngine =
                MockEngine {
                    assertEquals(HttpMethod.Get, it.method)
                    assertEquals(apiKey, it.headers["X-API-Key"])

                    respond(
                        content = expectedResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = FileRepositoryImpl(repositoryClient)

            // Act: ファイル一覧を取得
            val result = repository.browseFiles(sessionId)

            // Assert: 結果の検証
            assertTrue(result.isSuccess)
            val response = result.getOrNull()!!
            assertEquals("/home/user/project", response.path)
            assertEquals(3, response.entries.size)
            assertEquals("src", response.entries[0].name)
            assertEquals("directory", response.entries[0].type)
            assertEquals(4096, response.entries[0].size)
            assertEquals("README.md", response.entries[1].name)
            assertEquals("file", response.entries[1].type)
            assertEquals("link", response.entries[2].name)
            assertEquals("symlink", response.entries[2].type)
        }

    @Test
    fun `browseFiles - path traversal error returns 403`() =
        runTest {
            // Arrange: パストラバーサルエラーのモック設定
            val sessionId = "agent-0001"

            val mockEngine =
                MockEngine {
                    assertEquals(HttpMethod.Get, it.method)

                    respond(
                        content = """{"detail": "forbidden_path: path traversal detected"}""",
                        status = HttpStatusCode.Forbidden,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = FileRepositoryImpl(repositoryClient)

            // Act: エラー結果を取得
            val result = repository.browseFiles(sessionId, path = "../../etc")

            // Assert
            assertTrue(result.isFailure)
            val error = assertIs<ApiError.HttpError>(result.exceptionOrNull()!!)
            assertEquals(403, error.code)
        }

    @Test
    fun `browseFiles - invalid path error returns 400`() =
        runTest {
            // Arrange: 無効パスエラーのモック設定
            val sessionId = "agent-0001"

            val mockEngine =
                MockEngine {
                    respond(
                        content = """{"detail": "invalid_path: path contains null bytes"}""",
                        status = HttpStatusCode.BadRequest,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = FileRepositoryImpl(repositoryClient)

            // Act
            val result = repository.browseFiles(sessionId, path = "\u0000")

            // Assert
            assertTrue(result.isFailure)
            val error = assertIs<ApiError.HttpError>(result.exceptionOrNull()!!)
            assertEquals(400, error.code)
        }

    // ==================== readFile() テスト ====================

    @Test
    fun `readFile - success returns content`() =
        runTest {
            // Arrange: ファイル読み取りレスポンスのモック設定
            val sessionId = "agent-0001"
            val expectedResponse =
                """
                {
                    "content": "fun main() {\n    println(\"Hello\")\n}",
                    "language": "kotlin",
                    "size": 38,
                    "truncated": false
                }
                """.trimIndent()

            val mockEngine =
                MockEngine {
                    assertEquals(HttpMethod.Get, it.method)
                    assertEquals(apiKey, it.headers["X-API-Key"])

                    respond(
                        content = expectedResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = FileRepositoryImpl(repositoryClient)

            // Act
            val result = repository.readFile(sessionId, "Main.kt")

            // Assert
            assertTrue(result.isSuccess)
            val actual = result.getOrNull()!!
            assertEquals("fun main() {\n    println(\"Hello\")\n}", actual.content)
            assertEquals("kotlin", actual.language)
            assertEquals(38, actual.size)
            assertEquals(false, actual.truncated)
        }

    @Test
    fun `readFile - binary file error returns 422`() =
        runTest {
            // Arrange: バイナリファイルエラーのモック設定
            val sessionId = "agent-0001"

            val mockEngine =
                MockEngine {
                    respond(
                        content = """{"detail": "binary_file: cannot read binary content"}""",
                        status = HttpStatusCode.UnprocessableEntity,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = FileRepositoryImpl(repositoryClient)

            // Act
            val result = repository.readFile(sessionId, "image.png")

            // Assert
            assertTrue(result.isFailure)
            val error = assertIs<ApiError.HttpError>(result.exceptionOrNull()!!)
            assertEquals(422, error.code)
        }

    @Test
    fun `readFile - with offset and limit parameters`() =
        runTest {
            // Arrange: offset/limit指定のモック設定
            val sessionId = "agent-0001"
            val expectedResponse =
                """
                {
                    "content": "partial content",
                    "language": "text",
                    "size": 1024,
                    "truncated": true
                }
                """.trimIndent()

            val mockEngine =
                MockEngine {
                    assertEquals(HttpMethod.Get, it.method)
                    val url = it.url.toString()
                    assertTrue(url.contains("offset=100"))
                    assertTrue(url.contains("limit=512"))

                    respond(
                        content = expectedResponse,
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val repositoryClient = createMockRepositoryClient(mockEngine)
            val repository = FileRepositoryImpl(repositoryClient)

            // Act
            val result = repository.readFile(sessionId, "large.log", offset = 100, limit = 512)

            // Assert
            assertTrue(result.isSuccess)
            val actual = result.getOrNull()!!
            assertEquals("partial content", actual.content)
            assertEquals(1024, actual.size)
            assertEquals(true, actual.truncated)
        }
}
