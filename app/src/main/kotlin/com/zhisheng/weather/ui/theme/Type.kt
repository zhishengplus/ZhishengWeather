package com.zhisheng.weather.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.R

// 磷光终端：命令、数据和标签使用等宽字体；长段中文说明改用系统无衬线，避免密集难读。
val ZhishengMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

val ZhishengReading = FontFamily.SansSerif

val ZhishengTypography = Typography(
    displayLarge = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Bold, fontSize = 84.sp, lineHeight = 88.sp),
    displayMedium = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = ZhishengMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

// 澄空终端：数字保持仪表感，中文标题与说明使用系统无衬线。
val PhosphorVistaTypography = Typography(
    displayLarge = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Light, fontSize = 80.sp, lineHeight = 84.sp),
    displayMedium = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 50.sp),
    displaySmall = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 17.sp),
    labelSmall = TextStyle(fontFamily = ZhishengReading, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)
