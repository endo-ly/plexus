"""ファイルブラウザ API ルート。

ディレクトリ一覧とファイル読み取りを提供します。
"""

import logging
import os
import stat

import anyio
from starlette.exceptions import HTTPException
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Route

from gateway.dependencies import verify_gateway_token
from gateway.infrastructure.path_guard import PathValidationError, validate_path
from gateway.infrastructure.tmux import get_active_pane_metadata, session_exists

logger = logging.getLogger(__name__)

MAX_FILE_SIZE = 1 * 1024 * 1024  # 1MB

# 言語マッピング（拡張子 → 言語名）
_LANGUAGE_MAP: dict[str, str] = {
    ".md": "markdown",
    ".py": "python",
    ".kt": "kotlin",
    ".js": "javascript",
    ".ts": "typescript",
    ".json": "json",
    ".yaml": "yaml",
    ".yml": "yaml",
    ".toml": "toml",
    ".rs": "rust",
    ".go": "go",
    ".sh": "shell",
    ".bash": "shell",
    ".zsh": "shell",
    ".css": "css",
    ".html": "html",
    ".xml": "xml",
    ".sql": "sql",
    ".java": "java",
    ".c": "c",
    ".cpp": "cpp",
    ".h": "c",
    ".hpp": "cpp",
    ".rb": "ruby",
    ".php": "php",
    ".swift": "swift",
    ".dart": "dart",
    ".lua": "lua",
    ".r": "r",
    ".dockerfile": "dockerfile",
    ".gradle": "groovy",
    ".kts": "kotlin",
}


def _validate_session_id(session_id: str) -> bool:
    """セッションIDの形式を検証する。

    Args:
        session_id: 検証するセッションID

    Returns:
        形式が正しい場合はTrue、そうでない場合はFalse
    """
    if not session_id:
        return False
    invalid_chars = set("\t\n\r;: ")
    return not any(c in invalid_chars for c in session_id)


def _detect_language(filepath: str) -> str:
    """ファイル拡張子から言語名を判定する。

    Args:
        filepath: ファイルパス

    Returns:
        言語名（不明な場合は拡張子なしのドット付き文字列）
    """
    _, ext = os.path.splitext(filepath)
    ext_lower = ext.lower()
    return _LANGUAGE_MAP.get(ext_lower, ext_lower.lstrip("."))


def _is_binary(data: bytes) -> bool:
    """データがバイナリかどうかを判定する。

    最初の1024バイトにNULバイトが含まれるかで判定する。

    Args:
        data: 判定対象のバイトデータ

    Returns:
        バイナリの場合True
    """
    chunk = data[:1024]
    return b"\x00" in chunk


def _browse_dir(abs_path: str, show_hidden: bool) -> dict:
    """ディレクトリ内のエントリ一覧を取得する。

    Args:
        abs_path: 絶対パス
        show_hidden: ドットファイルを含めるか

    Returns:
        エントリ一覧を含む辞書
    """
    entries: list[dict] = []
    try:
        items = sorted(os.listdir(abs_path))
    except PermissionError:
        raise HTTPException(status_code=403, detail="path_not_readable") from None

    for name in items:
        # .git ディレクトリは常に除外
        if name == ".git":
            continue
        # ドットファイル制御
        if not show_hidden and name.startswith("."):
            continue

        full_path = os.path.join(abs_path, name)
        try:
            st = os.lstat(full_path)
        except OSError:
            continue

        if stat.S_ISDIR(st.st_mode):
            entry_type = "directory"
        elif stat.S_ISLNK(st.st_mode):
            entry_type = "symlink"
        else:
            entry_type = "file"

        entries.append(
            {
                "name": name,
                "type": entry_type,
                "size": st.st_size,
                "modified": st.st_mtime,
            }
        )

    return {"path": abs_path, "entries": entries}


def _read_file_sync(
    abs_path: str, offset: int, limit: int
) -> dict:
    """ファイルを読み取る（ブロッキング）。

    Args:
        abs_path: ファイルの絶対パス
        offset: 読み取り開始バイト位置
        limit: 読み取り最大バイト数

    Returns:
        ファイル内容を含む辞書

    Raises:
        HTTPException: バイナリファイルや読み取りエラーの場合
    """
    file_size = os.path.getsize(abs_path)

    with open(abs_path, "rb") as f:
        f.seek(offset)
        data = f.read(limit)

    if _is_binary(data):
        raise HTTPException(status_code=422, detail="binary_file")

    content = data.decode("utf-8", errors="replace")
    truncated = (offset + limit) < file_size

    return {
        "content": content,
        "language": _detect_language(abs_path),
        "size": file_size,
        "truncated": truncated,
    }


async def browse_files(request: Request) -> JSONResponse:
    """ディレクトリ内のファイル/ディレクトリ一覧を返す。

    Args:
        request: Starlette リクエストオブジェクト

    Returns:
        エントリ一覧を含む JSONResponse
    """
    await verify_gateway_token(request)

    session_id = request.path_params.get("session_id")
    if not session_id or not _validate_session_id(session_id):
        raise HTTPException(status_code=400, detail="invalid_path: invalid session_id")

    # セッション存在確認
    if not await anyio.to_thread.run_sync(session_exists, session_id):
        raise HTTPException(status_code=404, detail="session_not_found")

    # pane_current_path を取得して workdir とする
    _, workdir = await anyio.to_thread.run_sync(get_active_pane_metadata, session_id)
    if not workdir:
        raise HTTPException(status_code=500, detail="session_path_unavailable")

    requested_path = request.query_params.get("path", ".")
    show_hidden = request.query_params.get("show_hidden", "false").lower() == "true"

    # パス検証
    try:
        abs_path = validate_path(workdir, requested_path)
    except PathValidationError:
        raise HTTPException(status_code=403, detail="path_outside_workdir") from None

    # パスが存在するか確認
    if not await anyio.to_thread.run_sync(os.path.exists, abs_path):
        raise HTTPException(status_code=404, detail="path_not_found")

    # ディレクトリか確認
    if not await anyio.to_thread.run_sync(os.path.isdir, abs_path):
        raise HTTPException(status_code=400, detail="invalid_path: not a directory")

    result = await anyio.to_thread.run_sync(_browse_dir, abs_path, show_hidden)
    return JSONResponse(result)


async def read_file(request: Request) -> JSONResponse:
    """ファイルの内容を読み取って返す。

    Args:
        request: Starlette リクエストオブジェクト

    Returns:
        ファイル内容を含む JSONResponse
    """
    await verify_gateway_token(request)

    session_id = request.path_params.get("session_id")
    if not session_id or not _validate_session_id(session_id):
        raise HTTPException(status_code=400, detail="invalid_path: invalid session_id")

    # セッション存在確認
    if not await anyio.to_thread.run_sync(session_exists, session_id):
        raise HTTPException(status_code=404, detail="session_not_found")

    # pane_current_path を取得して workdir とする
    _, workdir = await anyio.to_thread.run_sync(get_active_pane_metadata, session_id)
    if not workdir:
        raise HTTPException(status_code=500, detail="session_path_unavailable")

    requested_path = request.query_params.get("path")
    if not requested_path:
        raise HTTPException(status_code=400, detail="invalid_path: path is required")

    offset = int(request.query_params.get("offset", "0"))
    limit = int(request.query_params.get("limit", str(MAX_FILE_SIZE)))

    # パス検証
    try:
        abs_path = validate_path(workdir, requested_path)
    except PathValidationError:
        raise HTTPException(status_code=403, detail="path_outside_workdir") from None

    # ファイルが存在するか確認
    if not await anyio.to_thread.run_sync(os.path.exists, abs_path):
        raise HTTPException(status_code=404, detail="file_not_found")

    # ファイルか確認（ディレクトリ等は不可）
    if not await anyio.to_thread.run_sync(os.path.isfile, abs_path):
        raise HTTPException(status_code=400, detail="invalid_path: not a file")

    result = await anyio.to_thread.run_sync(_read_file_sync, abs_path, offset, limit)
    return JSONResponse(result)


def get_file_routes() -> list[Route]:
    """ファイルブラウザ API ルートを取得する。

    Returns:
        ルート定義のリスト
    """
    return [
        Route(
            "/v1/files/sessions/{session_id}/browse",
            browse_files,
            methods=["GET"],
        ),
        Route(
            "/v1/files/sessions/{session_id}/read",
            read_file,
            methods=["GET"],
        ),
    ]
