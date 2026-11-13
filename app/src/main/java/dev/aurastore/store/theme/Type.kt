package dev.aurastore.store.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.aurastore.store.R

/**
 * Manrope, in the four weights the scale uses.
 *
 * The type was `FontFamily.Default` throughout, which on this device means Roboto and gives the
 * storefront no voice of its own — the same absence of authorship that made the palette come from
 * the wallpaper. Google Sans, the face Play actually uses, is not redistributable, so the substitute
 * is a grotesk that holds a 12sp bottom-bar label and a 32sp page title without either looking thin.
 *
 * Upstream ships one variable file (weight axis 200–800), so `res/font/manrope_variable.ttf` is that
 * file and the entries below pin the axis to the four weights in use. SIL Open Font License 1.1,
 * text kept at `licenses/Manrope-OFL.txt`.
 */
private val Manrope = FontFamily(
    Font(R.font.manrope_variable, FontWeight.Normal),
    Font(R.font.manrope_variable, FontWeight.Medium),
    Font(R.font.manrope_variable, FontWeight.SemiBold),
    Font(R.font.manrope_variable, FontWeight.Bold),
)

/**
 * The Play Store's scale, in the house face.
 *
 * What makes Play look like Play: page titles are very large (32sp) at medium weight, section titles
 * sit at 22sp, list rows are a 16sp title over a 13sp meta line, and there is almost no
 * letter-spacing. Manrope runs slightly wider than Roboto at the same point size, so the large sizes
 * take negative tracking to keep their edges and the small labels take a little positive — a
 * headline should look set, not merely big.
 */
val Typography = Typography(
    // Page titles: the store name, the category name, the app name on a detail page.
    displaySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    // Section headers such as "About this app" or "Ratings and reviews".
    titleLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    ),
    // List-row titles.
    titleSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // Meta lines under titles, chip labels.
    bodySmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    ),
    // Bottom-bar labels.
    labelSmall = TextStyle(
        fontFamily = Manrope,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
)
