package com.example.mypractice.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val DarkColorPalette = darkColors(
    primary = Purple200,
    primaryVariant = Purple700,
    secondary = Teal200
)

private val LightColorPalette = lightColors(
    primary = Purple500,
    primaryVariant = Purple700,
    secondary = Teal200

    /* Other default colors to override
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.Black,
    onSurface = Color.Black,
    */
)

val LocalTextFieldColors = staticCompositionLocalOf<TextFieldColors> {
    error("No TextFieldColors provided") // 如果未提供会抛出错误
}

@Composable
fun MyPracticeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    //定义TextField样式，让其在夜间时将输入字符的颜色显示为白色
    val textFieldColors = TextFieldDefaults.textFieldColors(
        textColor = colors.onSurface,  // 正确：文本颜色，应该是背景色的对比色
        cursorColor = colors.primary,  // 光标颜色，可以是主要颜色
        focusedIndicatorColor = colors.primary,  // 聚焦时的指示器颜色
        unfocusedIndicatorColor = colors.onSurface.copy(alpha = 0.5f),  // 非聚焦时的指示器颜色
        backgroundColor = colors.surface  // 背景颜色
    )

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = {
            CompositionLocalProvider(
            LocalTextFieldColors provides textFieldColors, // 提供自定义的 TextFieldColors
            content = content
        )
        }
    )
}