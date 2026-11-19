package com.triforge.app.ui.legal

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.triforge.app.R
import com.triforge.app.ui.common.ScreenTopBar
import com.triforge.app.ui.theme.TriColors

/** The legal texts, kept as Markdown in res/raw so the app and the repository share one copy. */
enum class LegalDoc(val route: String, @RawRes val raw: Int, @StringRes val title: Int) {
    PRIVACY("privacy", R.raw.privacy_policy, R.string.legal_privacy_title),
    TERMS("terms", R.raw.terms_of_service, R.string.legal_terms_title),
}

@Composable
fun LegalScreen(doc: LegalDoc, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val text = rawText(doc.raw)
    val blocks = remember(text) { Markdown.parse(text) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(title = stringResource(doc.title), onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            blocks.forEach { block -> MarkdownBlock(block) }
        }
    }
}

@Composable
private fun MarkdownBlock(block: Markdown.Block) {
    when (block) {
        // The page title is already in the top bar.
        is Markdown.Block.Title -> Unit
        is Markdown.Block.Heading -> Text(
            text = block.text,
            style = if (block.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .padding(top = if (block.level <= 2) 14.dp else 6.dp)
                .semantics { heading() },
        )
        is Markdown.Block.Paragraph -> Text(
            text = inline(block.text),
            style = MaterialTheme.typography.bodyMedium,
            color = TriColors.textDim,
        )
        is Markdown.Block.Bullet -> Row {
            Text(text = "•", style = MaterialTheme.typography.bodyMedium, color = TriColors.accentText)
            Text(
                text = inline(block.text),
                style = MaterialTheme.typography.bodyMedium,
                color = TriColors.textDim,
                modifier = Modifier.padding(start = 10.dp).weight(1f),
            )
        }
    }
}

private val BoldColor = Color(0xFFF2F4F7)

/** **Bold** is the only inline style the legal texts use. */
private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = BoldColor)) { append(part) }
        } else {
            append(part)
        }
    }
}

/**
 * Just enough Markdown for the legal texts: headings, paragraphs, bullets.
 * Lines of one paragraph are joined, as Markdown does.
 */
object Markdown {
    sealed interface Block {
        data class Title(val text: String) : Block
        data class Heading(val level: Int, val text: String) : Block
        data class Paragraph(val text: String) : Block
        data class Bullet(val text: String) : Block
    }

    fun parse(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = StringBuilder()
        var bullet: StringBuilder? = null

        fun flush() {
            if (paragraph.isNotBlank()) blocks += Block.Paragraph(paragraph.toString().trim())
            paragraph.clear()
            bullet?.let { blocks += Block.Bullet(it.toString().trim()) }
            bullet = null
        }

        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> flush()
                line.startsWith("<!--") -> Unit
                line.startsWith("#") -> {
                    flush()
                    val level = line.takeWhile { it == '#' }.length
                    val content = line.drop(level).trim()
                    blocks += if (level == 1) Block.Title(content) else Block.Heading(level, content)
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flush()
                    bullet = StringBuilder(line.drop(2))
                }
                bullet != null && raw.startsWith("  ") -> bullet!!.append(' ').append(line.trim())
                else -> {
                    if (bullet != null) flush()
                    paragraph.append(' ').append(line.trim())
                }
            }
        }
        flush()
        return blocks
    }
}
