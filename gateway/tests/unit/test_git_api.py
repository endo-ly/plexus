"""Git API の単体テスト。

git_status / git_diff / git_log / git_commit_detail の
各エンドポイントの正常系・異常系を検証します。
"""

import json
from contextlib import ExitStack
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from gateway.infrastructure.git import (
    DiffFile,
    FileChange,
    GitCommitDetail,
    GitDiffResult,
    GitLogEntry,
    GitStatusResult,
    NotAGitRepoError,
)


@pytest.fixture
def mock_request():
    """テスト用リクエスト。"""

    def _make(
        session_id: str = "agent-0001",
        query_params: dict | None = None,
        sha: str | None = None,
    ):
        request = MagicMock()
        request.headers = {"X-API-Key": "valid_api_key_32_bytes_or_more"}
        request.path_params = {"session_id": session_id}
        if sha is not None:
            request.path_params["sha"] = sha
        request.query_params = query_params or {}
        return request

    return _make


def _patch_deps(
    session_exists_return: bool = True,
    pane_metadata: tuple = (None, "/fake/repo"),
) -> list:
    """共通依存パッチのリストを返す。"""
    return [
        patch("gateway.api.git.verify_gateway_token", new_callable=AsyncMock),
        patch(
            "gateway.api.git.session_exists",
            return_value=session_exists_return,
        ),
        patch(
            "gateway.api.git.get_active_pane_metadata",
            return_value=pane_metadata,
        ),
    ]


def _parse_body(response) -> dict:
    """JSONResponse の body をデコードして dict を返す。"""
    return json.loads(response.body)


# ============================================================================
# git_status
# ============================================================================


class TestGitStatus:
    """git_status エンドポイントのテスト。"""

    @pytest.mark.asyncio
    async def test_status_returns_branch_and_changes(self, mock_request):
        """ブランチ + staged/unstaged/untracked の情報を返すことを検証する。"""
        # Arrange
        request = mock_request()
        status_result = GitStatusResult(
            branch="main",
            staged=[FileChange(path="staged.py", status="added")],
            unstaged=[FileChange(path="modified.py", status="modified")],
            untracked=["new.txt"],
        )
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.status = MagicMock(return_value=status_result)
            from gateway.api.git import git_status

            # Act
            response = await git_status(request)

        # Assert
        assert response.status_code == 200
        body = _parse_body(response)
        assert body["branch"] == "main"
        assert body["staged"] == [{"path": "staged.py", "status": "added"}]
        assert body["unstaged"] == [{"path": "modified.py", "status": "modified"}]
        assert body["untracked"] == [{"path": "new.txt"}]

    @pytest.mark.asyncio
    async def test_status_not_a_git_repo_422(self, mock_request):
        """Git リポジトリ外の場合は 422 を返すことを検証する。"""
        # Arrange
        request = mock_request()
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.status = MagicMock(
                side_effect=NotAGitRepoError("not a repo")
            )
            from gateway.api.git import git_status

            # Act & Assert
            with pytest.raises(Exception) as exc_info:
                await git_status(request)
            assert exc_info.value.status_code == 422
            assert exc_info.value.detail == "not_a_git_repo"

    @pytest.mark.asyncio
    async def test_status_session_not_found_404(self, mock_request):
        """セッション不在の場合は 404 を返すことを検証する。"""
        # Arrange
        request = mock_request()
        with ExitStack() as stack:
            for p in _patch_deps(session_exists_return=False):
                stack.enter_context(p)
            from gateway.api.git import git_status

            # Act & Assert
            with pytest.raises(Exception) as exc_info:
                await git_status(request)
            assert exc_info.value.status_code == 404
            assert exc_info.value.detail == "session_not_found"


# ============================================================================
# git_diff
# ============================================================================


class TestGitDiff:
    """git_diff エンドポイントのテスト。"""

    @pytest.mark.asyncio
    async def test_diff_unstaged(self, mock_request):
        """target=unstaged の diff を返すことを検証する。"""
        # Arrange
        request = mock_request(query_params={"target": "unstaged"})
        diff_result = GitDiffResult(
            files=[
                DiffFile(
                    path="main.py",
                    additions=5,
                    deletions=2,
                    patch="@@ -1,3 +1,6 @@",
                )
            ]
        )
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.diff = MagicMock(return_value=diff_result)
            from gateway.api.git import git_diff

            # Act
            response = await git_diff(request)

        # Assert
        assert response.status_code == 200
        body = _parse_body(response)
        assert len(body["files"]) == 1
        assert body["files"][0]["path"] == "main.py"
        assert body["files"][0]["additions"] == 5
        assert body["files"][0]["deletions"] == 2
        assert body["files"][0]["patch"] == "@@ -1,3 +1,6 @@"

    @pytest.mark.asyncio
    async def test_diff_staged(self, mock_request):
        """target=staged の diff を返すことを検証する。"""
        # Arrange
        request = mock_request(query_params={"target": "staged"})
        diff_result = GitDiffResult(
            files=[DiffFile(path="a.py", additions=1, deletions=0, patch="+x")]
        )
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.diff = MagicMock(return_value=diff_result)
            from gateway.api.git import git_diff

            # Act
            response = await git_diff(request)

        # Assert
        assert response.status_code == 200
        body = _parse_body(response)
        assert body["files"][0]["path"] == "a.py"

    @pytest.mark.asyncio
    async def test_diff_head(self, mock_request):
        """target=HEAD の diff を返すことを検証する。"""
        # Arrange
        request = mock_request(query_params={"target": "HEAD"})
        diff_result = GitDiffResult(
            files=[DiffFile(path="b.py", additions=3, deletions=1, patch="+a\n-b")]
        )
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.diff = MagicMock(return_value=diff_result)
            from gateway.api.git import git_diff

            # Act
            response = await git_diff(request)

        # Assert
        assert response.status_code == 200
        body = _parse_body(response)
        assert body["files"][0]["path"] == "b.py"

    @pytest.mark.asyncio
    async def test_diff_specific_commit_sha(self, mock_request):
        """target=<sha> の diff を返すことを検証する。"""
        # Arrange
        sha = "abc123def456"
        request = mock_request(query_params={"target": sha})
        diff_result = GitDiffResult(
            files=[DiffFile(path="c.py", additions=10, deletions=0, patch="+new")]
        )
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.diff = MagicMock(return_value=diff_result)
            from gateway.api.git import git_diff

            # Act
            response = await git_diff(request)

        # Assert
        assert response.status_code == 200
        mock_runner.diff.assert_called_once_with(
            "/fake/repo", target=sha, path=None
        )

    @pytest.mark.asyncio
    async def test_diff_specific_file(self, mock_request):
        """path パラメータでファイル絞り込みできることを検証する。"""
        # Arrange
        request = mock_request(
            query_params={"target": "unstaged", "path": "src/main.py"}
        )
        diff_result = GitDiffResult(
            files=[DiffFile(path="src/main.py", additions=2, deletions=1, patch="+x")]
        )
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.diff = MagicMock(return_value=diff_result)
            from gateway.api.git import git_diff

            # Act
            response = await git_diff(request)

        # Assert
        assert response.status_code == 200
        mock_runner.diff.assert_called_once_with(
            "/fake/repo", target="unstaged", path="src/main.py"
        )

    @pytest.mark.asyncio
    async def test_diff_large_stat_only(self, mock_request):
        """10K 行超 diff は patch が null で stat のみ返ることを検証する。"""
        # Arrange
        request = mock_request()
        diff_result = GitDiffResult(
            files=[DiffFile(path="big.py", additions=12000, deletions=0, patch=None)]
        )
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.diff = MagicMock(return_value=diff_result)
            from gateway.api.git import git_diff

            # Act
            response = await git_diff(request)

        # Assert
        assert response.status_code == 200
        body = _parse_body(response)
        assert body["files"][0]["patch"] is None
        assert body["files"][0]["additions"] == 12000

    @pytest.mark.asyncio
    async def test_diff_not_a_git_repo_422(self, mock_request):
        """Git リポジトリ外の場合は 422 を返すことを検証する。"""
        # Arrange
        request = mock_request()
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.diff = MagicMock(
                side_effect=NotAGitRepoError("not a repo")
            )
            from gateway.api.git import git_diff

            # Act & Assert
            with pytest.raises(Exception) as exc_info:
                await git_diff(request)
            assert exc_info.value.status_code == 422
            assert exc_info.value.detail == "not_a_git_repo"


# ============================================================================
# git_log
# ============================================================================


class TestGitLog:
    """git_log エンドポイントのテスト。"""

    @pytest.mark.asyncio
    async def test_log_returns_commits(self, mock_request):
        """コミット一覧を返すことを検証する。"""
        # Arrange
        request = mock_request()
        log_entries = [
            GitLogEntry(
                sha="abc123def456789012345678901234567890abcd",
                short_sha="abc123d",
                message="Initial commit",
                author="Alice",
                date="2025-01-01 00:00:00 +0000",
            ),
        ]
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.log = MagicMock(return_value=log_entries)
            from gateway.api.git import git_log

            # Act
            response = await git_log(request)

        # Assert
        assert response.status_code == 200
        body = _parse_body(response)
        assert len(body["commits"]) == 1
        assert body["commits"][0]["sha"] == "abc123def456789012345678901234567890abcd"
        assert body["commits"][0]["short_sha"] == "abc123d"
        assert body["commits"][0]["message"] == "Initial commit"
        assert body["commits"][0]["author"] == "Alice"

    @pytest.mark.asyncio
    async def test_log_with_count(self, mock_request):
        """count パラメータが正しく渡ることを検証する。"""
        # Arrange
        request = mock_request(query_params={"count": "5"})
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.log = MagicMock(return_value=[])
            from gateway.api.git import git_log

            # Act
            response = await git_log(request)

        # Assert
        assert response.status_code == 200
        mock_runner.log.assert_called_once_with("/fake/repo", count=5)

    @pytest.mark.asyncio
    async def test_log_not_a_git_repo_422(self, mock_request):
        """Git リポジトリ外の場合は 422 を返すことを検証する。"""
        # Arrange
        request = mock_request()
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.log = MagicMock(
                side_effect=NotAGitRepoError("not a repo")
            )
            from gateway.api.git import git_log

            # Act & Assert
            with pytest.raises(Exception) as exc_info:
                await git_log(request)
            assert exc_info.value.status_code == 422
            assert exc_info.value.detail == "not_a_git_repo"


# ============================================================================
# git_commit_detail
# ============================================================================


class TestGitCommitDetail:
    """git_commit_detail エンドポイントのテスト。"""

    @pytest.mark.asyncio
    async def test_commit_detail(self, mock_request):
        """指定 SHA のコミット詳細 + diff を返すことを検証する。"""
        # Arrange
        sha = "abc123def456789012345678901234567890abcd"
        request = mock_request(sha=sha)
        commit_detail = GitCommitDetail(
            sha=sha,
            message="Add feature",
            author="Bob",
            date="2025-06-01 12:00:00 +0000",
            diff=GitDiffResult(
                files=[
                    DiffFile(path="feat.py", additions=10, deletions=0, patch="+new")
                ]
            ),
        )
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.commit_detail = MagicMock(return_value=commit_detail)
            from gateway.api.git import git_commit_detail

            # Act
            response = await git_commit_detail(request)

        # Assert
        assert response.status_code == 200
        body = _parse_body(response)
        assert body["commit"]["sha"] == sha
        assert body["commit"]["message"] == "Add feature"
        assert body["commit"]["author"] == "Bob"
        assert body["diff"]["files"][0]["path"] == "feat.py"
        assert body["diff"]["files"][0]["patch"] == "+new"

    @pytest.mark.asyncio
    async def test_commit_not_found_404(self, mock_request):
        """存在しないコミット SHA の場合は 404 を返すことを検証する。"""
        # Arrange
        request = mock_request(sha="nonexistent0000000000000000000000000dead")
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.commit_detail = MagicMock(
                side_effect=ValueError("sha not found")
            )
            from gateway.api.git import git_commit_detail

            # Act & Assert
            with pytest.raises(Exception) as exc_info:
                await git_commit_detail(request)
            assert exc_info.value.status_code == 404
            assert exc_info.value.detail == "commit_not_found"

    @pytest.mark.asyncio
    async def test_commit_not_a_git_repo_422(self, mock_request):
        """Git リポジトリ外の場合は 422 を返すことを検証する。"""
        # Arrange
        request = mock_request(sha="abc123")
        with ExitStack() as stack:
            for p in _patch_deps():
                stack.enter_context(p)
            mock_runner = stack.enter_context(
                patch("gateway.api.git._runner")
            )
            mock_runner.commit_detail = MagicMock(
                side_effect=NotAGitRepoError("not a repo")
            )
            from gateway.api.git import git_commit_detail

            # Act & Assert
            with pytest.raises(Exception) as exc_info:
                await git_commit_detail(request)
            assert exc_info.value.status_code == 422
            assert exc_info.value.detail == "not_a_git_repo"
