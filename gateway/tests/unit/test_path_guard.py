"""path_guard モジュールの単体テスト。

パス検証機能の各種シナリオを検証します。
"""

import os
from pathlib import Path

import pytest

from gateway.infrastructure.path_guard import PathValidationError, validate_path


class TestValidatePath:
    """validate_path 関数のテスト。

    パストラバーサル防止と正規化の各種シナリオを検証します。
    """

    def test_valid_path_inside_workdir(self, tmp_path: Path) -> None:
        """正規パスが workdir 配下の場合はそのまま返すことを検証します。"""
        # Arrange: workdir 配下にファイルを作成
        workdir = str(tmp_path)
        target = tmp_path / "project" / "readme.md"
        target.parent.mkdir(parents=True)
        target.write_text("hello")

        # Act: workdir 配下のパスを検証
        result = validate_path(workdir, str(target))

        # Assert: 解決済みの絶対パスが返されること
        assert result == str(target.resolve())

    def test_dotdot_traversal_rejected(self, tmp_path: Path) -> None:
        """../etc/passwd のようなパストラバーサルを拒否することを検証します。"""
        # Arrange: workdir 配下のパスを指定しつつ外部へ脱出を試みる
        workdir = str(tmp_path)
        escape_path = os.path.join(workdir, "..", "etc", "passwd")

        # Act & Assert: PathValidationError が発生すること
        with pytest.raises(PathValidationError, match="outside"):
            validate_path(workdir, escape_path)

    def test_symlink_escape_rejected(self, tmp_path: Path) -> None:
        """workdir 外を指すシンボリックリンクを拒否することを検証します。"""
        # Arrange: workdir 外部へのシンボリックリンクを作成
        outside = tmp_path / "outside_target"
        outside.mkdir()
        link = tmp_path / "escape_link"
        link.symlink_to(outside)

        workdir = str(tmp_path / "sandbox")
        workdir_path = tmp_path / "sandbox"
        workdir_path.mkdir()

        # リンクを sandbox 内に配置
        sandbox_link = workdir_path / "escape_link"
        sandbox_link.symlink_to(outside)

        # Act & Assert: 外部へ脱出するシンボリックリンクは拒否されること
        with pytest.raises(PathValidationError, match="outside"):
            validate_path(workdir, str(sandbox_link))

    def test_same_prefix_safety(self, tmp_path: Path) -> None:
        """/work/app vs /work/app2 の誤許可を防ぐことを検証します。"""
        # Arrange: 共通プレフィックスを持つ別ディレクトリを準備
        app1 = tmp_path / "app"
        app2 = tmp_path / "app2"
        app1.mkdir()
        app2.mkdir()

        workdir = str(app1)
        target = str(app2 / "secret.txt")

        # Act & Assert: プレフィックスが一致するだけで許可されないこと
        with pytest.raises(PathValidationError, match="outside"):
            validate_path(workdir, target)

    def test_empty_path_rejected(self, tmp_path: Path) -> None:
        """空パスを拒否することを検証します。"""
        # Arrange: 空のパス
        workdir = str(tmp_path)

        # Act & Assert: PathValidationError が発生すること
        with pytest.raises(PathValidationError, match="empty"):
            validate_path(workdir, "")

    def test_root_path_rejected(self, tmp_path: Path) -> None:
        """ルート / を拒否することを検証します。"""
        # Arrange: ルートパス
        workdir = str(tmp_path)

        # Act & Assert: PathValidationError が発生すること
        with pytest.raises(PathValidationError, match="outside"):
            validate_path(workdir, "/")

    def test_path_with_trailing_slash(self, tmp_path: Path) -> None:
        """末尾スラッシュありでも正規化して判定することを検証します。"""
        # Arrange: 末尾スラッシュ付きのパス
        workdir = str(tmp_path)
        target_dir = tmp_path / "subdir"
        target_dir.mkdir()

        # Act: 末尾スラッシュ付きで検証
        result = validate_path(workdir, str(target_dir) + "/")

        # Assert: 正規化されたパスが返されること
        assert result == str(target_dir.resolve())

    def test_relative_path_resolved(self, tmp_path: Path) -> None:
        """相対パスを workdir 基準で解決することを検証します。"""
        # Arrange: workdir 配下にファイルを作成
        workdir = str(tmp_path)
        target = tmp_path / "src" / "main.py"
        target.parent.mkdir(parents=True)
        target.write_text("print('hello')")

        # Act: 相対パスで検証
        result = validate_path(workdir, "src/main.py")

        # Assert: workdir 基準で解決された絶対パスが返されること
        assert result == str(target.resolve())
