"""ファイルブラウザ API の単体テスト。

browse_files / read_file エンドポイントのテストを行います。
"""

import os
import tempfile
from unittest.mock import MagicMock, patch

import pytest
from starlette.exceptions import HTTPException

from gateway.api.files import browse_files, read_file


@pytest.fixture
def mock_request():
    """テスト用リクエスト（認証済み）。"""
    request = MagicMock()
    request.headers = {"X-API-Key": "valid_api_key_32_bytes_or_more"}
    request.path_params = {"session_id": "agent-0001"}
    request.query_params = {}
    return request


@pytest.fixture
def tmp_workdir():
    """テスト用一時ディレクトリ。"""
    with tempfile.TemporaryDirectory() as d:
        yield d


def _setup_session_mocks(workdir: str):
    """セッション関連のモックを設定するヘルパー。

    session_exists → True, get_active_pane_metadata → (title, workdir) を返す。
    """

    def mock_run_sync(func, *args):
        name = getattr(func, "__name__", "")
        if name == "session_exists":
            return True
        if name == "get_active_pane_metadata":
            return ("test-title", workdir)
        return func(*args)

    return mock_run_sync


# ============================================================================
# browse_files テスト
# ============================================================================


class TestBrowseFiles:
    """browse_files エンドポイントのテスト。"""

    @pytest.mark.asyncio
    async def test_browse_returns_entries(self, mock_request, tmp_workdir):
        """ディレクトリ内のファイル/ディレクトリ一覧を返す。"""
        # Arrange
        os.makedirs(os.path.join(tmp_workdir, "subdir"))
        with open(os.path.join(tmp_workdir, "hello.txt"), "w") as f:
            f.write("hello")

        mock_request.query_params = {"path": "."}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            response = await browse_files(mock_request)

        # Assert
        assert response.status_code == 200
        body = response.body.decode() if isinstance(response.body, bytes) else response.body
        import json

        data = json.loads(body)
        assert "entries" in data
        assert len(data["entries"]) == 2
        names = {e["name"] for e in data["entries"]}
        assert names == {"subdir", "hello.txt"}

    @pytest.mark.asyncio
    async def test_browse_shows_name_type_size_modified(self, mock_request, tmp_workdir):
        """各エントリのフィールドを確認する。"""
        # Arrange
        with open(os.path.join(tmp_workdir, "test.md"), "w") as f:
            f.write("content")

        mock_request.query_params = {"path": "."}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            response = await browse_files(mock_request)

        # Assert
        import json

        data = json.loads(response.body)
        entry = data["entries"][0]
        assert entry["name"] == "test.md"
        assert entry["type"] == "file"
        assert entry["size"] == 7
        assert isinstance(entry["modified"], float)

    @pytest.mark.asyncio
    async def test_browse_hides_dotfiles_by_default(self, mock_request, tmp_workdir):
        """ドットファイルがデフォルトで非表示になる。"""
        # Arrange
        with open(os.path.join(tmp_workdir, ".envrc"), "w") as f:
            f.write("secret")
        with open(os.path.join(tmp_workdir, "visible.txt"), "w") as f:
            f.write("ok")

        mock_request.query_params = {"path": "."}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            response = await browse_files(mock_request)

        # Assert
        import json

        data = json.loads(response.body)
        names = {e["name"] for e in data["entries"]}
        assert ".envrc" not in names
        assert "visible.txt" in names

    @pytest.mark.asyncio
    async def test_browse_show_hidden_true(self, mock_request, tmp_workdir):
        """show_hidden=true でドットファイルが表示される。"""
        # Arrange
        with open(os.path.join(tmp_workdir, ".envrc"), "w") as f:
            f.write("secret")

        mock_request.query_params = {"path": ".", "show_hidden": "true"}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            response = await browse_files(mock_request)

        # Assert
        import json

        data = json.loads(response.body)
        names = {e["name"] for e in data["entries"]}
        assert ".envrc" in names

    @pytest.mark.asyncio
    async def test_browse_excludes_git_directory(self, mock_request, tmp_workdir):
        """.git ディレクトリは一覧に含めない。"""
        # Arrange
        os.makedirs(os.path.join(tmp_workdir, ".git"))
        os.makedirs(os.path.join(tmp_workdir, ".hidden"))
        with open(os.path.join(tmp_workdir, ".gitignore"), "w") as f:
            f.write("*.pyc")

        mock_request.query_params = {"path": ".", "show_hidden": "true"}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            response = await browse_files(mock_request)

        # Assert
        import json

        data = json.loads(response.body)
        names = {e["name"] for e in data["entries"]}
        assert ".git" not in names
        assert ".hidden" in names
        assert ".gitignore" in names

    @pytest.mark.asyncio
    async def test_browse_path_traversal_403(self, mock_request, tmp_workdir):
        """../etc/passwd → 403 path_outside_workdir。"""
        # Arrange
        mock_request.query_params = {"path": "../etc/passwd"}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act & Assert
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            with pytest.raises(HTTPException) as exc_info:
                await browse_files(mock_request)

        assert exc_info.value.status_code == 403
        assert "path_outside_workdir" in exc_info.value.detail

    @pytest.mark.asyncio
    async def test_browse_invalid_path_400(self, mock_request, tmp_workdir):
        """不正パス → 400 invalid_path。"""
        # Arrange — 不正なセッションID
        mock_request.path_params = {"session_id": "bad;id"}
        mock_request.query_params = {"path": "."}

        # Act & Assert
        with patch("gateway.api.files.verify_gateway_token"):
            with pytest.raises(HTTPException) as exc_info:
                await browse_files(mock_request)

        assert exc_info.value.status_code == 400
        assert "invalid_path" in exc_info.value.detail

    @pytest.mark.asyncio
    async def test_browse_session_not_found_404(self, mock_request, tmp_workdir):
        """存在しないセッション → 404 session_not_found。"""
        # Arrange
        mock_request.query_params = {"path": "."}

        def mock_run_sync(func, *args):
            name = getattr(func, "__name__", "")
            if name == "session_exists":
                return False
            return None

        # Act & Assert
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_run_sync),
        ):
            with pytest.raises(HTTPException) as exc_info:
                await browse_files(mock_request)

        assert exc_info.value.status_code == 404
        assert "session_not_found" in exc_info.value.detail

    @pytest.mark.asyncio
    async def test_browse_path_not_found_404(self, mock_request, tmp_workdir):
        """存在しないパス → 404 path_not_found。"""
        # Arrange
        mock_request.query_params = {"path": "nonexistent_dir"}

        def mock_run_sync(func, *args):
            name = getattr(func, "__name__", "")
            if name == "session_exists":
                return True
            if name == "get_active_pane_metadata":
                return ("title", tmp_workdir)
            # validate_path が通った後、os.path.exists → False
            return func(*args)

        # Act & Assert
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_run_sync),
        ):
            with pytest.raises(HTTPException) as exc_info:
                await browse_files(mock_request)

        assert exc_info.value.status_code == 404
        assert "path_not_found" in exc_info.value.detail


# ============================================================================
# read_file テスト
# ============================================================================


class TestReadFile:
    """read_file エンドポイントのテスト。"""

    @pytest.mark.asyncio
    async def test_read_text_file(self, mock_request, tmp_workdir):
        """テキストファイルの内容と言語判定を確認する。"""
        # Arrange
        filepath = os.path.join(tmp_workdir, "readme.md")
        with open(filepath, "w") as f:
            f.write("# Hello World")

        mock_request.query_params = {"path": "readme.md"}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            response = await read_file(mock_request)

        # Assert
        import json

        assert response.status_code == 200
        data = json.loads(response.body)
        assert data["content"] == "# Hello World"
        assert data["language"] == "markdown"
        assert data["size"] == 13
        assert data["truncated"] is False

    @pytest.mark.asyncio
    async def test_read_truncated_large_file(self, mock_request, tmp_workdir):
        """1MB 超で truncated: true になる。"""
        # Arrange
        filepath = os.path.join(tmp_workdir, "large.txt")
        # 1MB + 1バイトのファイルを作成
        content = "A" * (1024 * 1024 + 1)
        with open(filepath, "w") as f:
            f.write(content)

        mock_request.query_params = {"path": "large.txt"}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            response = await read_file(mock_request)

        # Assert
        import json

        data = json.loads(response.body)
        assert data["truncated"] is True
        assert len(data["content"]) == 1024 * 1024

    @pytest.mark.asyncio
    async def test_read_binary_file_422(self, mock_request, tmp_workdir):
        """バイナリファイル → 422 binary_file。"""
        # Arrange
        filepath = os.path.join(tmp_workdir, "image.png")
        with open(filepath, "wb") as f:
            f.write(b"\x89PNG\r\n\x1a\n\x00\x00\x00")

        mock_request.query_params = {"path": "image.png"}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act & Assert
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            with pytest.raises(HTTPException) as exc_info:
                await read_file(mock_request)

        assert exc_info.value.status_code == 422
        assert "binary_file" in exc_info.value.detail

    @pytest.mark.asyncio
    async def test_read_path_traversal_403(self, mock_request, tmp_workdir):
        """パストラバーサル → 403 path_outside_workdir。"""
        # Arrange
        mock_request.query_params = {"path": "../../etc/passwd"}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act & Assert
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            with pytest.raises(HTTPException) as exc_info:
                await read_file(mock_request)

        assert exc_info.value.status_code == 403
        assert "path_outside_workdir" in exc_info.value.detail

    @pytest.mark.asyncio
    async def test_read_invalid_path_400(self, mock_request, tmp_workdir):
        """不正パス → 400 invalid_path。"""
        # Arrange — 不正なセッションID
        mock_request.path_params = {"session_id": "bad;id"}
        mock_request.query_params = {"path": "test.txt"}

        # Act & Assert
        with patch("gateway.api.files.verify_gateway_token"):
            with pytest.raises(HTTPException) as exc_info:
                await read_file(mock_request)

        assert exc_info.value.status_code == 400
        assert "invalid_path" in exc_info.value.detail

    @pytest.mark.asyncio
    async def test_read_file_not_found_404(self, mock_request, tmp_workdir):
        """存在しないファイル → 404 file_not_found。"""
        # Arrange
        mock_request.query_params = {"path": "nonexistent.txt"}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act & Assert
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            with pytest.raises(HTTPException) as exc_info:
                await read_file(mock_request)

        assert exc_info.value.status_code == 404
        assert "file_not_found" in exc_info.value.detail

    @pytest.mark.asyncio
    async def test_browse_symlink_escape_403(self, mock_request, tmp_workdir):
        """外部を指すシンボリックリンク → 403。"""
        # Arrange — tmp_workdir 外を指すシンボリックリンク
        with tempfile.TemporaryDirectory() as outside_dir:
            link_path = os.path.join(tmp_workdir, "escape_link")
            os.symlink(outside_dir, link_path)

            mock_request.query_params = {"path": "escape_link"}
            mock_sync = _setup_session_mocks(tmp_workdir)

            # Act & Assert
            with (
                patch("gateway.api.files.verify_gateway_token"),
                patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
            ):
                with pytest.raises(HTTPException) as exc_info:
                    await browse_files(mock_request)

        assert exc_info.value.status_code == 403
        assert "path_outside_workdir" in exc_info.value.detail

    @pytest.mark.asyncio
    async def test_read_utf8_fallback(self, mock_request, tmp_workdir):
        """UTF-8 でないファイルは代替文字で置換して返す。"""
        # Arrange
        filepath = os.path.join(tmp_workdir, "shiftjis.txt")
        # Shift-JIS エンコードされたテキスト（NUL バイトを含まない）
        with open(filepath, "wb") as f:
            f.write("こんにちは".encode("shift_jis"))

        mock_request.query_params = {"path": "shiftjis.txt"}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            response = await read_file(mock_request)

        # Assert
        import json

        data = json.loads(response.body)
        assert response.status_code == 200
        assert isinstance(data["content"], str)
        assert len(data["content"]) > 0

    @pytest.mark.asyncio
    async def test_read_with_offset(self, mock_request, tmp_workdir):
        """offset/limit パラメータで続きを取得できる。"""
        # Arrange
        filepath = os.path.join(tmp_workdir, "offset.txt")
        with open(filepath, "w") as f:
            f.write("0123456789")

        mock_request.query_params = {"path": "offset.txt", "offset": "5", "limit": "3"}
        mock_sync = _setup_session_mocks(tmp_workdir)

        # Act
        with (
            patch("gateway.api.files.verify_gateway_token"),
            patch("gateway.api.files.anyio.to_thread.run_sync", side_effect=mock_sync),
        ):
            response = await read_file(mock_request)

        # Assert
        import json

        data = json.loads(response.body)
        assert data["content"] == "567"
        assert data["size"] == 10
        assert data["truncated"] is True
