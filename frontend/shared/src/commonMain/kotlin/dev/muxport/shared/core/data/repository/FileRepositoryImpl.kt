package dev.muxport.shared.core.data.repository

import dev.muxport.shared.core.data.repository.internal.RepositoryClient
import dev.muxport.shared.core.domain.model.file.FileBrowseResponse
import dev.muxport.shared.core.domain.model.file.FileReadResult
import dev.muxport.shared.core.domain.repository.FileRepository
import dev.muxport.shared.core.domain.repository.RepositoryResult
import io.ktor.http.encodeURLParameter

/**
 * FileRepositoryの実装
 *
 * RepositoryClientを使用してGateway APIと通信します。
 */
class FileRepositoryImpl(
    private val repositoryClient: RepositoryClient,
) : FileRepository,
    BaseRepository {
    override suspend fun browseFiles(
        sessionId: String,
        path: String,
        showHidden: Boolean,
    ): RepositoryResult<FileBrowseResponse> =
        wrapRepositoryOperation {
            repositoryClient.get<FileBrowseResponse>(
                "/api/v1/files/sessions/$sessionId/browse?path=${path.encodeURLParameter()}&show_hidden=$showHidden",
            )
        }

    override suspend fun readFile(
        sessionId: String,
        path: String,
        offset: Int,
        limit: Int,
    ): RepositoryResult<FileReadResult> =
        wrapRepositoryOperation {
            repositoryClient.get<FileReadResult>(
                "/api/v1/files/sessions/$sessionId/read?path=${path.encodeURLParameter()}&offset=$offset&limit=$limit",
            )
        }
}
