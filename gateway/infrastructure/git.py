"""Git コマンドランナー。

Git リポジトリの status / diff / log / commit 情報を取得する機能を提供します。
"""

import logging
import re
import subprocess
from dataclasses import dataclass

logger = logging.getLogger(__name__)

GIT_COMMAND_TIMEOUT_SECONDS = 5

DIFF_LINE_THRESHOLD = 10_000


class NotAGitRepoError(Exception):
    """Git リポジトリ外でコマンドを実行した場合のエラー。"""


@dataclass(frozen=True, slots=True)
class FileChange:
    """ファイル変更情報。

    Attributes:
        path: ファイルパス
        status: 変更種別（added / modified / deleted / untracked）
    """

    path: str
    status: str


@dataclass(frozen=True, slots=True)
class GitStatusResult:
    """git status の結果。

    Attributes:
        branch: 現在のブランチ名
        staged: ステージング済みファイルのリスト
        unstaged: 未ステージングファイルのリスト
        untracked: 未追跡ファイルのリスト
    """

    branch: str
    staged: list[FileChange]
    unstaged: list[FileChange]
    untracked: list[str]


@dataclass(frozen=True, slots=True)
class DiffFile:
    """差分ファイル情報。

    Attributes:
        path: ファイルパス
        additions: 追加行数
        deletions: 削除行数
        patch: パッチテキスト（大規模差分時は None）
    """

    path: str
    additions: int
    deletions: int
    patch: str | None


@dataclass(frozen=True, slots=True)
class GitDiffResult:
    """git diff の結果。

    Attributes:
        files: 差分ファイルのリスト
    """

    files: list[DiffFile]


@dataclass(frozen=True, slots=True)
class GitLogEntry:
    """git log のコミットエントリ。

    Attributes:
        sha: コミット SHA（フル）
        short_sha: コミット SHA（短縮）
        message: コミットメッセージ
        author: 作者名
        date: 日付文字列
    """

    sha: str
    short_sha: str
    message: str
    author: str
    date: str


@dataclass(frozen=True, slots=True)
class GitCommitDetail:
    """コミット詳細情報。

    Attributes:
        sha: コミット SHA（フル）
        message: コミットメッセージ
        author: 作者名
        date: 日付文字列
        diff: コミットの差分
    """

    sha: str
    message: str
    author: str
    date: str
    diff: GitDiffResult


def _run_git(args: list[str], repo_path: str) -> subprocess.CompletedProcess[str]:
    """git サブコマンドを実行し、結果を返します。

    Args:
        args: git サブコマンドの引数リスト
        repo_path: リポジトリパス

    Returns:
        コマンドの実行結果

    Raises:
        OSError: git がインストールされていない、またはタイムアウトした場合
        NotAGitRepoError: リポジトリ外で実行した場合
    """
    cmd = ["git", "-C", repo_path, *args]
    try:
        return subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            check=True,
            timeout=GIT_COMMAND_TIMEOUT_SECONDS,
        )
    except FileNotFoundError as e:
        logger.error("git command not found")
        raise OSError("git is not installed") from e
    except subprocess.TimeoutExpired as e:
        cmd_str = " ".join(cmd)
        logger.error("git command timed out: %s", cmd_str)
        raise OSError(f"git {cmd_str} timed out") from e
    except subprocess.CalledProcessError as e:
        if e.returncode == 128:
            raise NotAGitRepoError(
                f"Not a git repository: {repo_path}"
            ) from e
        raise


_STATUS_MAP = {
    "A": "added",
    "M": "modified",
    "D": "deleted",
}


def _parse_status_v2_line(
    line: str,
) -> tuple[str, FileChange | None, FileChange | None, str | None]:
    """porcelain=v2 の 1 行をパースします。

    Returns:
        (branch_name or "", staged_change or None, unstaged_change or None, untracked_path or None)
    """
    if line.startswith("# branch.head"):
        # フォーマット: "# branch.head <name>"
        # split で最大2分割して3番目の要素を取得
        parts = line.split()
        branch = parts[2] if len(parts) >= 3 else ""
        if branch == "(detached)":
            branch = "detached HEAD"
        return branch, None, None, None

    if line.startswith("1 "):
        # 1 <xy> <sub> <mH> <mI> <mW> <hH> <hI> <path>
        parts = line.split()
        xy = parts[1]
        path = parts[8]
        staged = None
        unstaged = None
        x = xy[0]
        y = xy[1]
        if x in _STATUS_MAP and x != ".":
            staged = FileChange(path=path, status=_STATUS_MAP[x])
        if y in _STATUS_MAP and y != ".":
            unstaged = FileChange(path=path, status=_STATUS_MAP[y])
        return "", staged, unstaged, None

    if line.startswith("2 "):
        # 2 <xy> <sub> <mH> <mI> <mW> <hH> <hI> <X><score> <path> <origPath>
        parts = line.split()
        if len(parts) < 11:
            return "", None, None, None
        xy = parts[1]
        path = parts[9]
        staged = None
        unstaged = None
        x = xy[0]
        y = xy[1]
        if x in _STATUS_MAP and x != ".":
            staged = FileChange(path=path, status=_STATUS_MAP[x])
        if y in _STATUS_MAP and y != ".":
            unstaged = FileChange(path=path, status=_STATUS_MAP[y])
        return "", staged, unstaged, None

    if line.startswith("? "):
        path = line[2:]
        return "", None, None, path

    return "", None, None, None


_STAT_PATTERN = re.compile(r"^\s*(.+?)\s*\|\s*(\d+)\s*([+-]+)$")
_STAT_BINARY_PATTERN = re.compile(r"^\s*(.+?)\s*\|\s*Bin\s+")
_STAT_INSERT_DELETE = re.compile(r" (\d+) insertion", re.IGNORECASE)
_STAT_DELETE_ONLY = re.compile(r" (\d+) deletion", re.IGNORECASE)


def _parse_diff_stat(stat_output: str) -> list[tuple[str, int, int]]:
    """`git diff --stat` の出力からファイルごとの増減行数をパースします。

    Returns:
        [(path, additions, deletions), ...]
    """
    results: list[tuple[str, int, int]] = []
    for line in stat_output.splitlines():
        m = _STAT_PATTERN.match(line)
        if m:
            path = m.group(1).strip()
            changes = m.group(3)
            additions = changes.count("+")
            deletions = changes.count("-")
            results.append((path, additions, deletions))
            continue
        m = _STAT_BINARY_PATTERN.match(line)
        if m:
            path = m.group(1).strip()
            results.append((path, 0, 0))
    return results


_NUMSTAT_PATTERN = re.compile(r"^(\d+|-)\t(\d+|-)\t(.+)$")


def _parse_numstat(numstat_output: str) -> list[tuple[str, int, int]]:
    """`git diff --numstat` の出力をパースします。

    Returns:
        [(path, additions, deletions), ...]
    """
    results: list[tuple[str, int, int]] = []
    for line in numstat_output.splitlines():
        m = _NUMSTAT_PATTERN.match(line)
        if m:
            additions = int(m.group(1)) if m.group(1) != "-" else 0
            deletions = int(m.group(2)) if m.group(2) != "-" else 0
            path = m.group(3)
            results.append((path, additions, deletions))
    return results


def _build_diff_args(target: str, path: str | None = None) -> list[str]:
    """diff のターゲットから git 引数を構築します。"""
    args: list[str] = []
    if target == "staged":
        args.append("--cached")
    elif target == "head":
        args.append("HEAD")
    elif target not in ("unstaged", ""):
        args.append(target)
    if path:
        args.extend(["--", path])
    return args


class GitCommandRunner:
    """Git コマンドの実行ランナー。

    Git リポジトリの status / diff / log / commit 情報を取得します。
    各メソッドは同期実行され、API 層で anyio.to_thread.run_sync により
    ラップされることを前提としています。
    """

    def status(self, repo_path: str) -> GitStatusResult:
        """リポジトリのステータスを取得します。

        Args:
            repo_path: Git リポジトリのパス

        Returns:
            ブランチ名とファイル変更情報

        Raises:
            NotAGitRepoError: Git リポジトリ外の場合
            OSError: git がインストールされていない、またはタイムアウトした場合
        """
        result = _run_git(["status", "--porcelain=v2", "--branch"], repo_path)

        branch = ""
        staged: list[FileChange] = []
        unstaged: list[FileChange] = []
        untracked: list[str] = []

        for line in result.stdout.splitlines():
            if not line:
                continue
            br, sc, uc, ut = _parse_status_v2_line(line)
            if br:
                branch = br
            if sc:
                staged.append(sc)
            if uc:
                unstaged.append(uc)
            if ut:
                untracked.append(ut)

        return GitStatusResult(
            branch=branch,
            staged=staged,
            unstaged=unstaged,
            untracked=untracked,
        )

    def diff(
        self, repo_path: str, target: str = "unstaged", path: str | None = None
    ) -> GitDiffResult:
        """リポジトリの差分を取得します。

        大規模差分（10K 行超）の場合は patch を省略します。

        Args:
            repo_path: Git リポジトリのパス
            target: 差分ターゲット（unstaged/staged/head/コミットSHA）
            path: 特定ファイルパス（省略時は全体）

        Returns:
            差分ファイルのリスト

        Raises:
            NotAGitRepoError: Git リポジトリ外の場合
            OSError: git がインストールされていない、またはタイムアウトした場合
        """
        diff_args = _build_diff_args(target, path)

        # まず --numstat で総行数を確認
        stat_result = _run_git(
            ["diff", "--numstat", *diff_args], repo_path
        )
        stat_entries = _parse_numstat(stat_result.stdout)
        total_lines = sum(a + d for _, a, d in stat_entries)

        if total_lines > DIFF_LINE_THRESHOLD:
            # 大規模差分: stat のみ返す
            files = [
                DiffFile(path=p, additions=a, deletions=d, patch=None)
                for p, a, d in stat_entries
            ]
            return GitDiffResult(files=files)

        # 通常差分: patch も取得
        patch_result = _run_git(
            ["diff", *diff_args], repo_path
        )
        patch_text = patch_result.stdout

        # patch をファイルごとに分割
        file_patches = self._split_patch_by_file(patch_text)

        files: list[DiffFile] = []
        for p, a, d in stat_entries:
            files.append(
                DiffFile(
                    path=p,
                    additions=a,
                    deletions=d,
                    patch=file_patches.get(p),
                )
            )
        return GitDiffResult(files=files)

    def diff_stat(
        self, repo_path: str, target: str = "unstaged"
    ) -> list[tuple[str, int, int]]:
        """`--stat` のみの差分統計を取得します。

        Args:
            repo_path: Git リポジトリのパス
            target: 差分ターゲット

        Returns:
            [(path, additions, deletions), ...] のリスト

        Raises:
            NotAGitRepoError: Git リポジトリ外の場合
            OSError: git がインストールされていない、またはタイムアウトした場合
        """
        diff_args = _build_diff_args(target)
        result = _run_git(["diff", "--stat", *diff_args], repo_path)
        return _parse_diff_stat(result.stdout)

    def log(self, repo_path: str, count: int = 10) -> list[GitLogEntry]:
        """コミットログを取得します。

        Args:
            repo_path: Git リポジトリのパス
            count: 取得件数

        Returns:
            コミットエントリのリスト

        Raises:
            NotAGitRepoError: Git リポジトリ外の場合
            OSError: git がインストールされていない、またはタイムアウトした場合
        """
        result = _run_git(
            [
                "log",
                f"--max-count={count}",
                "--pretty=format:%H%x00%h%x00%s%x00%an%x00%ai",
            ],
            repo_path,
        )

        entries: list[GitLogEntry] = []
        for line in result.stdout.splitlines():
            if not line:
                continue
            parts = line.split("\x00")
            if len(parts) != 5:
                logger.warning("Invalid git log output: %s", line[:80])
                continue
            entries.append(
                GitLogEntry(
                    sha=parts[0],
                    short_sha=parts[1],
                    message=parts[2],
                    author=parts[3],
                    date=parts[4],
                )
            )
        return entries

    def commit_detail(self, repo_path: str, sha: str) -> GitCommitDetail:
        """指定コミットの詳細情報を取得します。

        Args:
            repo_path: Git リポジトリのパス
            sha: コミット SHA

        Returns:
            コミット詳細と差分

        Raises:
            NotAGitRepoError: Git リポジトリ外の場合
            OSError: git がインストールされていない、またはタイムアウトした場合
        """
        log_result = _run_git(
            [
                "log",
                "-1",
                "--pretty=format:%H%x00%h%x00%s%x00%an%x00%ai",
                sha,
            ],
            repo_path,
        )
        parts = log_result.stdout.strip().split("\x00")
        if len(parts) != 5:
            raise ValueError(f"Invalid git log output for sha={sha}")

        diff_result = self.diff(repo_path, target=sha)

        return GitCommitDetail(
            sha=parts[0],
            message=parts[2],
            author=parts[3],
            date=parts[4],
            diff=diff_result,
        )

    @staticmethod
    def _split_patch_by_file(patch_text: str) -> dict[str, str]:
        """patch テキストをファイルごとに分割します。

        `diff --git a/path b/path` を区切りとしてファイルごとの patch に分割します。
        """
        if not patch_text:
            return {}

        files: dict[str, str] = {}
        current_path: str | None = None
        current_lines: list[str] = []

        for line in patch_text.splitlines():
            if line.startswith("diff --git "):
                # 前のファイルを保存
                if current_path is not None:
                    files[current_path] = "\n".join(current_lines)
                # a/path b/path から path を抽出
                # "diff --git a/foo.txt b/foo.txt" → "foo.txt"
                parts = line.split(" b/", 1)
                if len(parts) == 2:
                    current_path = parts[1].strip()
                else:
                    current_path = None
                current_lines = [line]
            else:
                current_lines.append(line)

        # 最後のファイルを保存
        if current_path is not None:
            files[current_path] = "\n".join(current_lines)

        return files
