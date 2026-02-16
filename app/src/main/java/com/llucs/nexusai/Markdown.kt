package com.llucs.nexusai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class BulletList(val items: List<String>) : MdBlock
    data class CodeFence(val lang: String?, val code: String) : MdBlock
    data object Hr : MdBlock
}

fun splitMarkdown(input: String): List<MdBlock> {
    val text = input.replace("\r\n", "\n")
    	.replace(Regex("""(?<=\S)(#{1,6})"""), "\n$1")
    val lines = text.split("\n")

    val out = ArrayList<MdBlock>(maxOf(4, lines.size / 2))

    fun isTableLine(s: String): Boolean = s.contains('|') && s.count { it == '|' } >= 2

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block
        if (line.trimStart().startsWith("```")) {
            val fence = line.trimStart()
            val lang = fence.removePrefix("```").trim().ifBlank { null }
            i++
            val buf = StringBuilder()
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                buf.append(lines[i])
                if (i != lines.lastIndex) buf.append('\n')
                i++
            }
            // skip closing fence if present
            if (i < lines.size && lines[i].trimStart().startsWith("```")) i++
            out.add(MdBlock.CodeFence(lang, buf.toString().trimEnd('\n')))
            continue
        }

        // Heading (#, ##, ###)
        val trimmed = line.trimStart()

        val tline = trimmed.trim()
        if (tline == "---" || tline == "***" || tline == "___") {
            out.add(MdBlock.Hr)
            i++
            continue
        }

        if (isTableLine(trimmed)) {
            val buf = StringBuilder(trimmed.trimEnd())
            i++
            while (i < lines.size) {
                val tl = lines[i].trimEnd()
                if (tl.isBlank()) break
                if (!isTableLine(tl)) break
                buf.append(\'\n\').append(tl)
                i++
            }
            out.add(MdBlock.CodeFence(null, buf.toString()))
            continue
        }
        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            if (level in 1..6 && trimmed.length > level && trimmed[level] == ' ') {
                out.add(MdBlock.Heading(level, trimmed.drop(level + 1)))
                i++
                continue
            }
        }

        // Bullet list
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val items = ArrayList<String>()
            while (i < lines.size) {
                val t = lines[i].trimStart()
                if (t.startsWith("- ") || t.startsWith("* ")) {
                    items.add(t.drop(2))
                    i++
                } else if (t.isBlank()) {
                    i++
                    break
                } else {
                    break
                }
            }
            if (items.isNotEmpty()) out.add(MdBlock.BulletList(items))
            continue
        }

        // Blank line
        if (trimmed.isBlank()) {
            i++
            continue
        }

        // Paragraph (merge consecutive non-empty, non-special lines)
        val p = StringBuilder(trimmed)
        i++
        while (i < lines.size) {
            val t = lines[i].trimStart()
            if (t.isBlank()) break
            if (t.startsWith("```")) break
            if (t.startsWith("#") && t.dropWhile { it == '#' }.startsWith(" ")) break
            if (t.startsWith("- ") || t.startsWith("* ")) break
            p.append('\n')
            p.append(t)
            i++
        }
        out.add(MdBlock.Paragraph(p.toString()))
    }

    return out
}

@Composable
fun MarkdownTextBlock(
    block: MdBlock,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onBackground
) {
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val inlineCodeFg = MaterialTheme.colorScheme.onSurfaceVariant

    when (block) {
        is MdBlock.Heading -> {
            val size = when (block.level) {
                1 -> 24.sp
                2 -> 20.sp
                3 -> 18.sp
                else -> 16.sp
            }
            Text(
                text = block.text,
                fontSize = size,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp)
            )
        }

        is MdBlock.BulletList -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                block.items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("• ", color = contentColor)
                        Text(
                            text = renderInlineMarkdown(item, inlineCodeBg, inlineCodeFg),
                            color = contentColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        is MdBlock.CodeFence -> {
            CodeBlock(code = block.code, lang = block.lang, modifier = modifier)
        }

        is MdBlock.Paragraph -> {
            Text(
                text = renderInlineMarkdown(block.text, inlineCodeBg, inlineCodeFg),
                color = contentColor,
                modifier = modifier.fillMaxWidth(),
                overflow = TextOverflow.Clip
            )
        }


        is MdBlock.Hr -> {
            androidx.compose.material3.HorizontalDivider(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                thickness = 2.dp,
                color = contentColor.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
private fun CodeBlock(code: String, lang: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val shape = remember { RoundedCornerShape(16.dp) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = lang?.ifBlank { "" } ?: "code",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            IconButton(onClick = { copyToClipboard(ctx, code) }) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copiar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val scroll = rememberScrollState()
        SelectionContainer {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("code", text))
}

private fun renderInlineMarkdown(src: String, inlineCodeBg: Color, inlineCodeFg: Color): AnnotatedString {
    // Handles: **bold**, *italic*, `code`
    val s = src
        .replace(Regex("""([:;,.!?])([A-Za-zÀ-ÿ])"""), "$1 $2")
    val b = AnnotatedString.Builder()

    var i = 0
    while (i < s.length) {
        // Inline code
        if (s[i] == '`') {
            val j = s.indexOf('`', i + 1)
            if (j != -1) {
                val inside = s.substring(i + 1, j)
                val start = b.length
                b.append(inside)
                b.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = inlineCodeBg,
                        color = inlineCodeFg
                    ),
                    start,
                    b.length
                )
                i = j + 1
                continue
            }
        }

        // Bold (**text**)
        if (i + 1 < s.length && s[i] == '*' && s[i + 1] == '*') {
            val j = s.indexOf("**", i + 2)
            if (j != -1) {
                val inside = s.substring(i + 2, j)
                val start = b.length
                b.append(inside)
                b.addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, b.length)
                i = j + 2
                continue
            }
        }

        // Italic (*text*)
        if (s[i] == '*') {
            val j = s.indexOf('*', i + 1)
            if (j != -1) {
                val inside = s.substring(i + 1, j)
                val start = b.length
                b.append(inside)
                b.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, b.length)
                i = j + 1
                continue
            }
        }

        // Plain character
        b.append(s[i])
        i++
    }

    return b.toAnnotatedString()
}