package com.scrivtech.heartrate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.scrivtech.heartrate.R

// Bundled rather than downloaded: the app has no INTERNET permission and should not gain
// one for a typeface. Licensed under the SIL Open Font License (licenses/Barlow-OFL.txt).
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold)
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold)
)

/**
 * The seven type roles from DESIGN.md. Words are Barlow; every number that is data is
 * Barlow Condensed, whose narrow figures let the live reading be larger in the same width.
 */
object HrmType {
    val DisplayBpm = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 200.sp,
        lineHeight = 0.85.em,
        letterSpacing = (-4).sp
    )
    val NumeralLg = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 1.1.em
    )
    val NumeralMd = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 1.1.em
    )
    val Title = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 1.2.em
    )
    val LabelLg = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 1.2.em
    )
    val Body = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 1.35.em
    )
    val BodySm = TextStyle(
        fontFamily = Barlow,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 1.35.em
    )
}

// Material components that are not styled explicitly (dialog titles, text fields) fall back
// to these, so nothing on screen renders in the platform default typeface.
internal val HrmTypography = Typography(
    headlineSmall = HrmType.Title,
    titleLarge = HrmType.Title,
    titleMedium = HrmType.LabelLg,
    labelLarge = HrmType.LabelLg,
    bodyLarge = HrmType.Body,
    bodyMedium = HrmType.Body,
    bodySmall = HrmType.BodySm,
    labelMedium = HrmType.BodySm,
    labelSmall = HrmType.BodySm
)
