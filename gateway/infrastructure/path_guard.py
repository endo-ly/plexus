"""パス検証ユーティリティ。

ファイルアクセス時のパストラバーサル攻撃を防止します。
許可ルート配下のパスのみアクセス可能にします。
"""

import logging
import os
from pathlib import PurePath

logger = logging.getLogger(__name__)


class PathValidationError(ValueError):
    """パス検証に失敗した場合のエラー。"""


def validate_path(workdir: str, requested_path: str) -> str:
    """指定パスが workdir 配下にあることを検証し、解決済みパスを返す。

    os.path.realpath() でシンボリックリンクを解決し、絶対パスに正規化する。
    正規化後のパスと workdir を os.path.commonpath() で比較し、
    commonpath が workdir と一致することを検証する。
    文字列プレフィックス一致は /work/app vs /work/app2 の誤許可を生むため避ける。

    Args:
        workdir: 許可ルートディレクトリのパス
        requested_path: 検証対象のパス

    Returns:
        解決済み（realpath）の絶対パス

    Raises:
        PathValidationError: パスが workdir 配下にない場合
    """
    # 空パスの拒否
    if not requested_path:
        logger.debug("Rejected empty path for workdir=%s", workdir)
        raise PathValidationError("empty path is not allowed")

    # 許可ルートとリクエストパスをそれぞれ正規化
    real_workdir = os.path.realpath(workdir)

    # 相対パスは workdir 基準で解決してから realpath に通す
    if not os.path.isabs(requested_path):
        resolved = os.path.realpath(os.path.join(real_workdir, requested_path))
    else:
        resolved = os.path.realpath(requested_path)

    # PurePath で統一的に扱い、末尾スラッシュやケース差異を吸収
    workdir_pure = PurePath(real_workdir)
    resolved_pure = PurePath(resolved)

    # commonpath による containment チェック
    # 文字列プレフィックス一致は /work/app vs /work/app2 の誤許可を生む
    try:
        common = os.path.commonpath([str(workdir_pure), str(resolved_pure)])
    except ValueError:
        # 異なるドライブ（Windows）等で共通パスが計算できない場合
        logger.debug(
            "Rejected path (no common path): workdir=%s, resolved=%s",
            real_workdir,
            resolved,
        )
        raise PathValidationError(
            "path is outside the allowed directory"
        ) from None

    if common != str(workdir_pure):
        logger.debug(
            "Rejected path outside workdir: workdir=%s, resolved=%s",
            real_workdir,
            resolved,
        )
        raise PathValidationError("path is outside the allowed directory")

    logger.debug("Validated path: %s", resolved)
    return resolved
