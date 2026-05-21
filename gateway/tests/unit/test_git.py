"""Git コマンドランナーの単体テスト。

GitCommandRunner の status / diff / log / commit_detail の各機能を検証します。
"""

import subprocess
from unittest.mock import Mock, patch

import pytest

from gateway.infrastructure.git import (
    GitCommandRunner,
    GitCommitDetail,
    GitDiffResult,
    GitLogEntry,
    GitStatusResult,
    NotAGitRepoError,
)


def _mock_result(stdout: str = "", returncode: int = 0) -> Mock:
    """subprocess.run の戻り値モックを作成します。"""
    result = Mock()
    result.returncode = returncode
    result.stdout = stdout
    return result


class TestGitStatus:
    """GitCommandRunner.status のテスト。"""

    def test_git_status_returns_branch_and_changes(self) -> None:
        """ブランチ名 + staged/unstaged/untracked ファイル一覧を取得できることを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        status_output = (
            "# branch.head main\n"
            "1 M. N... 100644 100644 100644 abc123 def456 src/main.py\n"
            "1 .M N... 100644 100644 100644 abc123 def456 README.md\n"
            "? untracked.txt\n"
        )
        with patch("subprocess.run") as mock_run:
            mock_run.return_value = _mock_result(status_output)

            # Act
            result = runner.status("/repo")

            # Assert
            assert isinstance(result, GitStatusResult)
            assert result.branch == "main"
            assert len(result.staged) == 1
            assert result.staged[0].path == "src/main.py"
            assert result.staged[0].status == "modified"
            assert len(result.unstaged) == 1
            assert result.unstaged[0].path == "README.md"
            assert result.unstaged[0].status == "modified"
            assert result.untracked == ["untracked.txt"]

    def test_git_status_not_a_repo(self) -> None:
        """Git リポジトリ外で NotAGitRepoError を raise することを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        with patch("subprocess.run") as mock_run:
            mock_run.side_effect = subprocess.CalledProcessError(
                returncode=128, cmd=["git", "-C", "/not-a-repo", "status"]
            )

            # Act & Assert
            with pytest.raises(NotAGitRepoError, match="Not a git repository"):
                runner.status("/not-a-repo")


class TestGitDiff:
    """GitCommandRunner.diff のテスト。"""

    def test_git_diff_unstaged(self) -> None:
        """unstaged diff の files 統計と patch テキストを取得できることを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        numstat_output = "10\t5\tsrc/main.py\n3\t1\tREADME.md\n"
        patch_output = (
            "diff --git a/src/main.py b/src/main.py\n"
            "--- a/src/main.py\n"
            "+++ b/src/main.py\n"
            "@@ -1,3 +1,5 @@\n"
            "+new line\n"
            " context\n"
            "diff --git a/README.md b/README.md\n"
            "--- a/README.md\n"
            "+++ b/README.md\n"
            "@@ -1 +1,2 @@\n"
            "+added\n"
        )
        with patch("subprocess.run") as mock_run:
            mock_run.side_effect = [
                _mock_result(numstat_output),
                _mock_result(patch_output),
            ]

            # Act
            result = runner.diff("/repo", target="unstaged")

            # Assert
            assert isinstance(result, GitDiffResult)
            assert len(result.files) == 2
            assert result.files[0].path == "src/main.py"
            assert result.files[0].additions == 10
            assert result.files[0].deletions == 5
            assert result.files[0].patch is not None
            assert "diff --git a/src/main.py" in result.files[0].patch
            assert result.files[1].path == "README.md"
            assert result.files[1].patch is not None

    def test_git_diff_staged(self) -> None:
        """staged diff (--cached) を取得できることを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        numstat_output = "2\t0\tnew_file.py\n"
        with patch("subprocess.run") as mock_run:
            mock_run.side_effect = [
                _mock_result(numstat_output),
                _mock_result(""),
            ]

            # Act
            result = runner.diff("/repo", target="staged")

            # Assert
            assert len(result.files) == 1
            assert result.files[0].path == "new_file.py"
            # --cached が引数に含まれていること
            first_call = mock_run.call_args_list[0]
            assert "--cached" in first_call.args[0]

    def test_git_diff_specific_file(self) -> None:
        """単一ファイル指定時の diff を取得できることを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        numstat_output = "5\t2\tsrc/target.py\n"
        with patch("subprocess.run") as mock_run:
            mock_run.side_effect = [
                _mock_result(numstat_output),
                _mock_result(""),
            ]

            # Act
            result = runner.diff("/repo", path="src/target.py")

            # Assert
            assert len(result.files) == 1
            assert result.files[0].path == "src/target.py"
            # -- path が引数に含まれていること
            first_call = mock_run.call_args_list[0]
            assert "--" in first_call.args[0]
            assert "src/target.py" in first_call.args[0]

    def test_git_diff_head(self) -> None:
        """HEAD 比較の diff を取得できることを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        numstat_output = "8\t3\tapp.py\n"
        with patch("subprocess.run") as mock_run:
            mock_run.side_effect = [
                _mock_result(numstat_output),
                _mock_result(""),
            ]

            # Act
            result = runner.diff("/repo", target="head")

            # Assert
            assert len(result.files) == 1
            first_call = mock_run.call_args_list[0]
            assert "HEAD" in first_call.args[0]

    def test_git_diff_specific_commit(self) -> None:
        """指定コミット SHA の diff を取得できることを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        sha = "abc123def"
        numstat_output = "1\t1\tconfig.yaml\n"
        with patch("subprocess.run") as mock_run:
            mock_run.side_effect = [
                _mock_result(numstat_output),
                _mock_result(""),
            ]

            # Act
            result = runner.diff("/repo", target=sha)

            # Assert
            assert len(result.files) == 1
            first_call = mock_run.call_args_list[0]
            assert sha in first_call.args[0]

    def test_git_diff_large_stat_fallback(self) -> None:
        """差分が 10K 行超の場合、--stat のみ返し patch は None になることを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        # 10,001 行の合計差分
        numstat_output = "6000\t4001\tbig_file.py\n"
        with patch("subprocess.run") as mock_run:
            mock_run.return_value = _mock_result(numstat_output)

            # Act
            result = runner.diff("/repo")

            # Assert
            assert len(result.files) == 1
            assert result.files[0].patch is None
            assert result.files[0].additions == 6000
            assert result.files[0].deletions == 4001
            # patch 取得のための 2 回目の呼び出しがないこと
            assert mock_run.call_count == 1


class TestGitLog:
    """GitCommandRunner.log のテスト。"""

    def test_git_log_returns_commits(self) -> None:
        """コミット一覧を取得できることを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        log_output = (
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0\x00a1b2c3d\x00feat: add feature\x00Alice\x002025-05-01 12:00:00 +0900\n"
            "f1e2d3c4b5a697886970605040302010feed\x00f1e2d3c\x00fix: fix bug\x00Bob\x002025-04-30 10:00:00 +0900"
        )
        with patch("subprocess.run") as mock_run:
            mock_run.return_value = _mock_result(log_output)

            # Act
            entries = runner.log("/repo")

            # Assert
            assert len(entries) == 2
            assert isinstance(entries[0], GitLogEntry)
            assert entries[0].sha == "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0"
            assert entries[0].short_sha == "a1b2c3d"
            assert entries[0].message == "feat: add feature"
            assert entries[0].author == "Alice"
            assert entries[0].date == "2025-05-01 12:00:00 +0900"

    def test_git_log_with_count(self) -> None:
        """count パラメータで件数制限できることを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        log_output = (
            "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0\x00a1b2c3d\x00msg\x00Alice\x002025-05-01 12:00:00 +0900"
        )
        with patch("subprocess.run") as mock_run:
            mock_run.return_value = _mock_result(log_output)

            # Act
            entries = runner.log("/repo", count=5)

            # Assert
            assert len(entries) == 1
            call_args = mock_run.call_args
            assert "--max-count=5" in call_args.args[0]


class TestGitCommitDetail:
    """GitCommandRunner.commit_detail のテスト。"""

    def test_git_commit_detail(self) -> None:
        """指定コミットの diff を含む詳細情報を取得できることを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        sha = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0"
        log_output = f"{sha}\x00a1b2c3d\x00feat: new feature\x00Alice\x002025-05-01 12:00:00 +0900"
        numstat_output = "10\t5\tsrc/main.py\n"

        with patch("subprocess.run") as mock_run:
            mock_run.side_effect = [
                _mock_result(log_output),       # git log -1
                _mock_result(numstat_output),    # git diff --numstat
                _mock_result(""),                # git diff (patch)
            ]

            # Act
            detail = runner.commit_detail("/repo", sha=sha)

            # Assert
            assert isinstance(detail, GitCommitDetail)
            assert detail.sha == sha
            assert detail.message == "feat: new feature"
            assert detail.author == "Alice"
            assert detail.date == "2025-05-01 12:00:00 +0900"
            assert len(detail.diff.files) == 1
            assert detail.diff.files[0].path == "src/main.py"


class TestGitTimeout:
    """Git コマンドのタイムアウト処理のテスト。"""

    def test_git_timeout(self) -> None:
        """コマンドタイムアウト時に OSError を raise することを検証します。"""
        # Arrange
        runner = GitCommandRunner()
        with patch("subprocess.run") as mock_run:
            mock_run.side_effect = subprocess.TimeoutExpired(
                cmd=["git", "-C", "/repo", "status"], timeout=5
            )

            # Act & Assert
            with pytest.raises(OSError, match="timed out"):
                runner.status("/repo")
