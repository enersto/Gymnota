package com.example.myfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.selection.SelectionContainer

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
    val lines = markdown.split("\n")
    var inCodeBlock by remember { mutableStateOf(false) }

    // [修改] 在最外层包裹 SelectionContainer，即可实现全局文本选择与复制
    SelectionContainer(modifier = modifier.fillMaxWidth()) {

    Column(modifier = modifier.fillMaxWidth()) {
        lines.forEach { line ->
            // 1. 处理代码块标记 ```
            if (line.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock
                return@forEach // 跳过标记行本身
            }

            // 2. 处于代码块模式中
            if (inCodeBlock) {
                CodeBlockLine(line)
            }
            // 3. 普通 Markdown 解析模式
            else {
                when {
                    // 标题 H1 - H4
                    line.startsWith("# ") -> MarkdownHeading(line.removePrefix("# "), MaterialTheme.typography.headlineMedium)
                    line.startsWith("## ") -> MarkdownHeading(line.removePrefix("## "), MaterialTheme.typography.titleLarge)
                    line.startsWith("### ") -> MarkdownHeading(line.removePrefix("### "), MaterialTheme.typography.titleMedium)
                    line.startsWith("#### ") -> MarkdownHeading(line.removePrefix("#### "), MaterialTheme.typography.titleSmall)

                    // 引用 >
                    line.trim().startsWith("> ") -> BlockQuote(line.trim().removePrefix("> "))

                    // 无序列表 * 或 -
                    line.trim().startsWith("* ") || line.trim().startsWith("- ") -> {
                        val content = line.trim().removePrefix("* ").removePrefix("- ")
                        BulletPoint(content)
                    }

                    // 有序列表 1.
                    line.trim().matches(Regex("^\\d+\\..*")) -> {
                        // 提取序号和内容
                        val dotIndex = line.indexOf(".")
                        if (dotIndex != -1) {
                            val number = line.substring(0, dotIndex + 1)
                            val content = line.substring(dotIndex + 1).trim()
                            OrderedListPoint(number, content)
                        } else {
                            RegularText(line)
                        }
                    }

                    // 分割线 ---
                    line.trim() == "---" || line.trim() == "***" -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // 普通文本
                    else -> {
                        if (line.isNotBlank()) {
                            RegularText(line)
                        }
                    }
                }
            }
        }
    }
}}

// --- 组件部分 ---

@Composable
fun MarkdownHeading(text: String, style: androidx.compose.ui.text.TextStyle) {
    Text(
        text = parseRichText(text),
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun CodeBlockLine(text: String) {
    // 简单的单行代码块渲染，背景色更深一点
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun BlockQuote(text: String) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        // 引用左侧竖线
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(IntrinsicSize.Min) // 随内容高度
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = parseRichText(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
        )
        Text(
            text = parseRichText(text),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 24.sp
        )
    }
}

@Composable
fun OrderedListPoint(number: String, text: String) {
    Row(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(
            text = number,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
        )
        Text(
            text = parseRichText(text),
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 24.sp
        )
    }
}

@Composable
fun RegularText(text: String) {
    Text(
        text = parseRichText(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp),
        lineHeight = 24.sp
    )
}

// --- 核心解析逻辑 ---

/**
 * 解析 Markdown 行内样式：
 * 1. **粗体**
 * 2. `行内代码`
 * 3. *斜体* (简单支持)
 */
fun parseRichText(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0

        // 简单的状态机或者正则替换
        // 这里使用正则匹配 tokens
        // 匹配 **bold**, `code`, *italic*
        val regex = Regex("(\\*\\*|`|\\*)(.*?)(\\1)")

        val matches = regex.findAll(text)

        for (match in matches) {
            // 追加匹配前的普通文本
            if (match.range.first > currentIndex) {
                append(text.substring(currentIndex, match.range.first))
            }

            val token = match.groupValues[1] // **, `, or *
            val content = match.groupValues[2] // 内部内容

            when (token) {
                "**" -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(content)
                    }
                }
                "`" -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color.Gray.copy(alpha = 0.2f),
                            color = Color(0xFFE91E63) // 类似 Markdown 的红色代码高亮
                        )
                    ) {
                        append(" $content ") // 前后加点间距
                    }
                }
                "*" -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(content)
                    }
                }
            }

            currentIndex = match.range.last + 1
        }

        // 追加剩余文本
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}