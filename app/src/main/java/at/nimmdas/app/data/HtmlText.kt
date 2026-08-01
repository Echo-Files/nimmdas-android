package at.nimmdas.app.data

import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.HtmlCompat

/**
 * Many listings — especially OpenImmo real-estate imports — carry HTML in their
 * description (`<p>`, `<strong>`, `<br>`, entities like `&gt;`). Rendering that string
 * directly shows the raw markup, so it is converted to styled text here.
 *
 * Compose 1.6 has no `AnnotatedString.fromHtml`, hence the manual span mapping.
 */
fun String.htmlToAnnotatedString(): AnnotatedString {
    if (!looksLikeHtml()) return AnnotatedString(this)

    val spanned: Spanned = HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT)
    val text = spanned.toString().trim()

    return AnnotatedString.Builder(text).apply {
        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span).coerceIn(0, text.length)
            val end = spanned.getSpanEnd(span).coerceIn(0, text.length)
            if (start >= end) return@forEach
            when (span) {
                is StyleSpan -> when (span.style) {
                    android.graphics.Typeface.BOLD ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    android.graphics.Typeface.ITALIC ->
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    android.graphics.Typeface.BOLD_ITALIC ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                }
                is UnderlineSpan ->
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
            }
        }
    }.toAnnotatedString()
}

/** Plain-text version — for previews, cards and anywhere styling isn't rendered. */
fun String.stripHtml(): String =
    if (looksLikeHtml())
        HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
    else this

private fun String.looksLikeHtml(): Boolean =
    contains(Regex("</?[a-zA-Z][^>]*>")) || contains(Regex("&(gt|lt|amp|nbsp|quot|#\\d+);"))
