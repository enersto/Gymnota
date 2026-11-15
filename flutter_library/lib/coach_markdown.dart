import 'package:flutter/material.dart';

/// Mirrors the block and inline styles used by the original coach.
class CoachMarkdown extends StatelessWidget {
  const CoachMarkdown({super.key, required this.source});

  final String source;
  static final _inline = RegExp(r'(\*\*|`|\*)(.*?)\1');
  static final _heading = RegExp(r'^(#{1,4})\s+(.+)$');
  static final _list = RegExp(r'^([-*](?=\s)|\d+\.)\s*(.+)$');

  TextSpan _span(String text, TextStyle style, Color codeBackground) {
    var end = 0;
    final children = <TextSpan>[];
    for (final match in _inline.allMatches(text)) {
      children.add(TextSpan(text: text.substring(end, match.start)));
      final token = match[1];
      children.add(
        TextSpan(
          text: token == '`' ? ' ${match[2]} ' : match[2],
          style: switch (token) {
            '**' => const TextStyle(fontWeight: FontWeight.bold),
            '*' => const TextStyle(fontStyle: FontStyle.italic),
            _ => TextStyle(
              fontFamily: 'monospace',
              color: const Color(0xFFE91E63),
              backgroundColor: codeBackground,
            ),
          },
        ),
      );
      end = match.end;
    }
    children.add(TextSpan(text: text.substring(end)));
    return TextSpan(style: style, children: children);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final body = theme.textTheme.bodyMedium!.copyWith(
      color: colors.onSurface,
      height: 24 / (theme.textTheme.bodyMedium!.fontSize ?? 14),
    );
    Widget rich(String text, TextStyle style) =>
        Text.rich(_span(text, style, colors.onSurface.withValues(alpha: .12)));
    final blocks = <Widget>[];
    var inCode = false;
    for (final raw in source.split('\n')) {
      final line = raw.trim();
      if (line.startsWith('```')) {
        inCode = !inCode;
        continue;
      }
      if (inCode) {
        blocks.add(
          ColoredBox(
            color: colors.surfaceContainerHighest.withValues(alpha: .5),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              child: Text(
                raw,
                style: body.copyWith(fontFamily: 'monospace', fontSize: 12),
              ),
            ),
          ),
        );
      } else if (line.isEmpty) {
        continue;
      } else if (line == '---' || line == '***') {
        blocks.add(const Divider(height: 16));
      } else if (_heading.firstMatch(line) case final match?) {
        final style = [
          theme.textTheme.headlineMedium,
          theme.textTheme.titleLarge,
          theme.textTheme.titleMedium,
          theme.textTheme.titleSmall,
        ][match[1]!.length - 1]!;
        blocks.add(
          Padding(
            padding: const EdgeInsets.only(top: 12, bottom: 4),
            child: rich(
              match[2]!,
              style.copyWith(
                color: colors.onSurface,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        );
      } else if (line.startsWith('> ')) {
        blocks.add(
          Container(
            margin: const EdgeInsets.symmetric(vertical: 4),
            padding: const EdgeInsets.only(left: 12),
            decoration: BoxDecoration(
              border: Border(
                left: BorderSide(
                  color: colors.primary.withValues(alpha: .5),
                  width: 4,
                ),
              ),
            ),
            child: rich(
              line.substring(2),
              body.copyWith(fontStyle: FontStyle.italic),
            ),
          ),
        );
      } else if (_list.firstMatch(line) case final match?) {
        final ordered = match[1]!.endsWith('.');
        blocks.add(
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: Text(
                    ordered ? match[1]! : '•',
                    style: body.copyWith(
                      fontWeight: ordered ? FontWeight.bold : FontWeight.normal,
                    ),
                  ),
                ),
                Expanded(child: rich(match[2]!, body)),
              ],
            ),
          ),
        );
      } else {
        blocks.add(
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: rich(line, body),
          ),
        );
      }
    }
    return SelectionArea(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: blocks,
      ),
    );
  }
}
