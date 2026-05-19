package dev.muxport.shared.core.domain.repository

import dev.muxport.shared.core.domain.model.ChatRequest
import dev.muxport.shared.core.domain.model.ChatResponse
import dev.muxport.shared.core.domain.model.ModelsResponse
import dev.muxport.shared.core.domain.model.StreamChunk
import kotlinx.coroutines.flow.Flow

/**
 * チャットRepository
 *
 * メッセージ送信、ストリーミングチャット、モデル一覧取得を担当します。
 */
interface ChatRepository {
    /**
     * チャットメッセージを送信する（ストリーミング）
     *
     * @param request チャットリクエスト
     * @return ストリーミングレスポンスのFlow
     */
    fun sendMessage(request: ChatRequest): Flow<RepositoryResult<StreamChunk>>

    /**
     * チャットメッセージを送信する（非ストリーミング）
     *
     * @param request チャットリクエスト
     * @return チャットレスポンス
     */
    suspend fun sendMessageSync(request: ChatRequest): RepositoryResult<ChatResponse>

    /**
     * 利用可能なモデル一覧を取得する
     *
     * @return モデル一覧とデフォルトモデルIDを含むレスポンス
     */
    suspend fun getModels(): RepositoryResult<ModelsResponse>
}
