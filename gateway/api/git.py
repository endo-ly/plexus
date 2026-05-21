"""Git API ルート。

Git リポジトリの status / diff / log / commit 情報を提供します。
"""

import logging

import anyio
from starlette.exceptions import HTTPException
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route

from gateway.dependencies import verify_gateway_token
from gateway.infrastructure.git import (
    GitCommandRunner,
    NotAGitRepoError,
)
from gateway.infrastructure.tmux import get_active_pane_metadata, session_exists

logger = logging.getLogger(__name__)

_runner = GitCommandRunner()


def _validate_session_id(session_id: str | None) -> str:
    """セッション ID を検証して返します。

    Args:
        session_id: パスパラメータから取得したセッション ID

    Returns:
        検証済みのセッション ID

    Raises:
        HTTPException: セッション ID が不正な場合（400）
    """
    if not session_id or not isinstance(session_id, str):
        raise HTTPException(status_code=400, detail="invalid_session_id: required")
    invalid_chars = set("\t\n\r;: ")
    if any(c in invalid_chars for c in session_id):
        raise HTTPException(
            status_code=400, detail=f"invalid_session_id: {session_id}"
        )
    return session_id


async def _resolve_repo_path(session_id: str) -> str:
    """セッションのアクティブペインからリポジトリパスを解決します。

    Args:
        session_id: tmux セッション名

    Returns:
        カレントディレクトリパス

    Raises:
        HTTPException: セッションが存在しない場合（404）、
                       パスが取得できない場合（500）
    """
    if not await anyio.to_thread.run_sync(session_exists, session_id):
        raise HTTPException(status_code=404, detail="session_not_found")

    _, current_path = await anyio.to_thread.run_sync(
        get_active_pane_metadata, session_id
    )
    if current_path is None:
        raise HTTPException(
            status_code=500, detail="Failed to resolve session working directory"
        )
    return current_path


async def git_status(request: Request) -> JSONResponse:
    """リポジトリのステータスを取得します。

    Args:
        request: Starlette リクエストオブジェクト

    Returns:
        ブランチ名とファイル変更情報を含む JSONResponse
    """
    await verify_gateway_token(request)

    session_id = _validate_session_id(request.path_params.get("session_id"))
    repo_path = await _resolve_repo_path(session_id)

    try:
        result = await anyio.to_thread.run_sync(_runner.status, repo_path)
    except NotAGitRepoError as e:
        raise HTTPException(status_code=422, detail="not_a_git_repo") from e

    return JSONResponse(
        {
            "branch": result.branch,
            "staged": [
                {"path": fc.path, "status": fc.status} for fc in result.staged
            ],
            "unstaged": [
                {"path": fc.path, "status": fc.status} for fc in result.unstaged
            ],
            "untracked": [{"path": p} for p in result.untracked],
        }
    )


async def git_diff(request: Request) -> JSONResponse:
    """リポジトリの差分を取得します。

    Args:
        request: Starlette リクエストオブジェクト

    Returns:
        差分ファイルのリストを含む JSONResponse
    """
    await verify_gateway_token(request)

    session_id = _validate_session_id(request.path_params.get("session_id"))
    repo_path = await _resolve_repo_path(session_id)

    target = request.query_params.get("target", "unstaged")
    path = request.query_params.get("path")

    try:
        result = await anyio.to_thread.run_sync(
            lambda: _runner.diff(repo_path, target=target, path=path)
        )
    except NotAGitRepoError as e:
        raise HTTPException(status_code=422, detail="not_a_git_repo") from e

    return JSONResponse(
        {
            "files": [
                {
                    "path": f.path,
                    "additions": f.additions,
                    "deletions": f.deletions,
                    "patch": f.patch,
                }
                for f in result.files
            ],
        }
    )


async def git_log(request: Request) -> JSONResponse:
    """コミットログを取得します。

    Args:
        request: Starlette リクエストオブジェクト

    Returns:
        コミット一覧を含む JSONResponse
    """
    await verify_gateway_token(request)

    session_id = _validate_session_id(request.path_params.get("session_id"))
    repo_path = await _resolve_repo_path(session_id)

    count_str = request.query_params.get("count", "10")
    try:
        count = int(count_str)
    except ValueError:
        raise HTTPException(status_code=400, detail="invalid_count: must be an integer") from None
    if count <= 0:
        raise HTTPException(status_code=400, detail="invalid_count: must be > 0")

    try:
        entries = await anyio.to_thread.run_sync(
            lambda: _runner.log(repo_path, count=count)
        )
    except NotAGitRepoError as e:
        raise HTTPException(status_code=422, detail="not_a_git_repo") from e

    return JSONResponse(
        {
            "commits": [
                {
                    "sha": entry.sha,
                    "short_sha": entry.short_sha,
                    "message": entry.message,
                    "author": entry.author,
                    "date": entry.date,
                }
                for entry in entries
            ],
        }
    )


async def git_commit_detail(request: Request) -> JSONResponse:
    """指定コミットの詳細情報を取得します。

    Args:
        request: Starlette リクエストオブジェクト

    Returns:
        コミット詳細と差分を含む JSONResponse
    """
    await verify_gateway_token(request)

    session_id = _validate_session_id(request.path_params.get("session_id"))
    repo_path = await _resolve_repo_path(session_id)

    sha = request.path_params.get("sha", "")
    if not sha:
        raise HTTPException(status_code=400, detail="invalid_sha: required")

    try:
        detail = await anyio.to_thread.run_sync(
            lambda: _runner.commit_detail(repo_path, sha)
        )
    except NotAGitRepoError as e:
        raise HTTPException(status_code=422, detail="not_a_git_repo") from e
    except ValueError as e:
        raise HTTPException(status_code=404, detail="commit_not_found") from e

    return JSONResponse(
        {
            "commit": {
                "sha": detail.sha,
                "message": detail.message,
                "author": detail.author,
                "date": detail.date,
            },
            "diff": {
                "files": [
                    {
                        "path": f.path,
                        "additions": f.additions,
                        "deletions": f.deletions,
                        "patch": f.patch,
                    }
                    for f in detail.diff.files
                ],
            },
        }
    )


def get_git_routes() -> list[Route]:
    """Git API ルートを取得します。

    Returns:
        ルート定義のリスト
    """
    return [
        Route(
            "/v1/git/sessions/{session_id}/status",
            git_status,
            methods=["GET"],
        ),
        Route(
            "/v1/git/sessions/{session_id}/diff",
            git_diff,
            methods=["GET"],
        ),
        Route(
            "/v1/git/sessions/{session_id}/log",
            git_log,
            methods=["GET"],
        ),
        Route(
            "/v1/git/sessions/{session_id}/commits/{sha}",
            git_commit_detail,
            methods=["GET"],
        ),
    ]
