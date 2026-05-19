package dev.plexus.shared.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)

@Immutable
data class PlexusDimens(
    val zero: Dp = 0.dp,
    val space2: Dp = 2.dp,
    val space4: Dp = 4.dp,
    val space6: Dp = 6.dp,
    val space8: Dp = 8.dp,
    val space10: Dp = 10.dp,
    val space12: Dp = 12.dp,
    val space16: Dp = 16.dp,
    val space20: Dp = 20.dp,
    val space24: Dp = 24.dp,
    val space28: Dp = 28.dp,
    val space32: Dp = 32.dp,
    val space36: Dp = 36.dp,
    val space48: Dp = 48.dp,
    val space60: Dp = 60.dp,
    val space64: Dp = 64.dp,
    val size160: Dp = 160.dp,
    val listLoadingHeight: Dp = 128.dp,
    val indicatorSizeSmall: Dp = 8.dp,
    val indicatorSizeMedium: Dp = 10.dp,
    val iconSizeSmall: Dp = 16.dp,
    val iconSize18: Dp = 18.dp,
    val iconSizeMedium: Dp = 20.dp,
    val borderWidthThin: Dp = 1.dp,
    val topBarButtonHeight: Dp = 32.dp,
    val minTapTargetWidth: Dp = 72.dp,
    val terminalHeaderHeight: Dp = 96.dp,
    val chatComposerMinHeight: Dp = 100.dp,
    val chatComposerTextLaneMinHeight: Dp = 30.dp,
    val modelSelectorMaxWidth: Dp = 140.dp,
)

@Immutable
data class PlexusShapes(
    val radiusXs: Shape = RoundedCornerShape(6.dp),
    val radiusSm: Shape = RoundedCornerShape(8.dp),
    val radiusMd: Shape = RoundedCornerShape(12.dp),
    val radiusLg: Shape = RoundedCornerShape(18.dp),
    val radiusXl: Shape = RoundedCornerShape(22.dp),
    val statusCircle: Shape = CircleShape,
)

/** 拡張カラーセット。アプリ固有のセマンティックカラーを提供する。
 *
 * @property success 接続成功・正常状態を表すカラー。
 */
@Immutable
data class MuxportExtendedColors(
    val success: Color = Teal400, // 接続成功、正常状態
)

/** サイズ・間隔の CompositionLocal。 */
internal val LocalPlexusDimens = staticCompositionLocalOf { PlexusDimens() }
/** シェイプの CompositionLocal。 */
internal val LocalPlexusShapes = staticCompositionLocalOf { PlexusShapes() }
/** 拡張カラーの CompositionLocal。 */
internal val LocalMuxportExtendedColors = staticCompositionLocalOf { MuxportExtendedColors() }

/** テーマトークンの公開アクセスポイント。CompositionLocal から現在値を取得する。
 *
 * [dimens] サイズ・間隔。 [shapes] シェイプ。 [extendedColors] 拡張カラー。
 */
object MuxportThemeTokens {
    val dimens: PlexusDimens
        @Composable
        @ReadOnlyComposable
        get() = LocalPlexusDimens.current

    val shapes: PlexusShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalPlexusShapes.current

    val extendedColors: MuxportExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMuxportExtendedColors.current
}
