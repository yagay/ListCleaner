package com.yagay.ListCleaner.ui

import androidx.compose.material3.Text as MaterialText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** Native theme text without forked labels, validation strings or business logic. */
@Composable
internal fun AdaptiveText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = TextStyle.Default
) {
    MaterialText(
        text = text, modifier = modifier, color = color,
        fontSize = if (fontSize == TextUnit.Unspecified) fontSize else fontSize * (UiPreferencesRuntime.current.fontScalePercent / 100f),
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else lineHeight * (UiPreferencesRuntime.current.fontScalePercent / 100f),
        overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        style = (if (style == TextStyle.Default) LocalTextStyle.current else style).let { base ->
            val scale = UiPreferencesRuntime.current.fontScalePercent / 100f
            base.copy(
                fontSize = if (base.fontSize == TextUnit.Unspecified) base.fontSize else base.fontSize * scale,
                lineHeight = if (base.lineHeight == TextUnit.Unspecified) base.lineHeight else base.lineHeight * scale
            )
        }
    )
}

@Composable
internal fun AdaptiveText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = TextStyle.Default
) {
    MaterialText(
        text = text, modifier = modifier, color = color,
        fontSize = if (fontSize == TextUnit.Unspecified) fontSize else fontSize * (UiPreferencesRuntime.current.fontScalePercent / 100f),
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily,
        letterSpacing = letterSpacing, textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = if (lineHeight == TextUnit.Unspecified) lineHeight else lineHeight * (UiPreferencesRuntime.current.fontScalePercent / 100f),
        overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines,
        style = (if (style == TextStyle.Default) LocalTextStyle.current else style).let { base ->
            val scale = UiPreferencesRuntime.current.fontScalePercent / 100f
            base.copy(
                fontSize = if (base.fontSize == TextUnit.Unspecified) base.fontSize else base.fontSize * scale,
                lineHeight = if (base.lineHeight == TextUnit.Unspecified) base.lineHeight else base.lineHeight * scale
            )
        }
    )
}
