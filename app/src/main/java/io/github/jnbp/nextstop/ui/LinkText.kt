package io.github.jnbp.nextstop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.jnbp.nextstop.data.findTextLinks

/**
 * [text] of a notice with its web addresses, e-mail addresses and phone numbers as links
 * (see findTextLinks), underlined in [color]. A tap opens them through the system: the
 * browser, the mail app, the phone app. Text without any stays plain.
 */
fun linkedText(text: String, color: Color): AnnotatedString {
    val links = findTextLinks(text)
    if (links.isEmpty()) return AnnotatedString(text)
    val styles = TextLinkStyles(
        style = SpanStyle(color = color, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
    )
    return buildAnnotatedString {
        append(text)
        links.forEach { link -> addLink(LinkAnnotation.Url(link.target, styles), link.start, link.end) }
    }
}
