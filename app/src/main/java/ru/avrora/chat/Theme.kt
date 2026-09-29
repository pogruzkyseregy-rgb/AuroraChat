package ru.avrora.chat

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра «флюоритовая ночь»: тёмный индиго, холодное голубое свечение.
val Night = Color(0xFF0B0E1F)
val Panel = Color(0xFF131936)
val BubbleAurora = Color(0xFF1A2150)
val BubbleUser = Color(0xFF1F5B73)
val Glow = Color(0xFF86D8F0)
val TextMain = Color(0xFFE8ECFF)
val TextDim = Color(0xFF98A3D0)
val Edge = Color(0xFF2E376E)

val AuroraColors = darkColorScheme(
    primary = Glow,
    onPrimary = Night,
    background = Night,
    onBackground = TextMain,
    surface = Panel,
    onSurface = TextMain,
    onSurfaceVariant = TextDim,
    outline = Edge,
    primaryContainer = BubbleUser,
    onPrimaryContainer = TextMain,
    error = Color(0xFFFF9A9A)
)

@Composable
fun auroraFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain,
    cursorColor = Glow,
    focusedBorderColor = Glow.copy(alpha = 0.6f),
    unfocusedBorderColor = Edge,
    focusedPlaceholderColor = TextDim,
    unfocusedPlaceholderColor = TextDim,
    focusedLabelColor = Glow,
    unfocusedLabelColor = TextDim
)
