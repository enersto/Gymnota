import 'package:flutter_test/flutter_test.dart';
import 'package:gymnota_library/ai/stream_state.dart';

void main() {
  Map<String, dynamic> event(
    int seq,
    String kind, [
    Map<String, dynamic> values = const {},
  ]) => {'requestId': 'r1', 'seq': seq, 'kind': kind, ...values};
  test('summary and public reasoning stay separate from the final answer', () {
    var s = const AiStreamState.start('r1');
    s = s.apply(event(0, 'reasoning.summary.delta', {'text': 'Summary'}));
    s = s.apply(event(1, 'answer.delta', {'text': 'Plan'}));
    s = s.apply(
      event(2, 'reasoning.provider.delta', {'text': 'Provider public content'}),
    );
    expect(s.phase, AiStreamPhase.answering);
    s = s.apply(
      event(3, 'completed', {'status': 'completed', 'finishReason': 'stop'}),
    );
    expect(s.reasoningSummary, 'Summary');
    expect(s.providerReasoning, 'Provider public content');
    expect(s.completedAnswerForValidation, 'Plan');
  });
  test('request identity, duplicate sequence and cancellation reject late answer chunks', () {
    var s = const AiStreamState.start('r1')
        .apply(event(0, 'answer.delta', {'text': 'partial'}));
    expect(
      identical(
        s,
        s.apply({
          ...event(1, 'answer.delta', {'text': 'wrong'}),
          'requestId': 'old',
        }),
      ),
      isTrue,
    );
    expect(
      identical(s, s.apply(event(0, 'answer.delta', {'text': 'duplicate'}))),
      isTrue,
    );
    s = s.apply(event(1, 'cancelled'));
    expect(
      s.apply(event(2, 'answer.delta', {'text': 'late'})).answer,
      'partial',
    );
    expect(s.completedAnswerForValidation, isNull);
  });
  test(
    'truncated, refused and failed output cannot be passed as a completed plan',
    () {
      var s = const AiStreamState.start('r1')
          .apply(event(0, 'answer.delta', {'text': 'draft'}));
      expect(
        s
            .apply(
              event(1, 'completed', {
                'status': 'incomplete',
                'finishReason': 'length',
              }),
            )
            .completedAnswerForValidation,
        isNull,
      );
      expect(
        s.apply(event(1, 'failed', {'code': 'network_interrupted'})).answer,
        'draft',
      );
      s = s.apply(event(1, 'refusal.delta', {'text': 'Refused'}));
      expect(
        s
            .apply(
              event(2, 'completed', {
                'status': 'completed',
                'finishReason': 'stop',
              }),
            )
            .completedAnswerForValidation,
        isNull,
      );
    },
  );
  test('late usage is allowed without reopening a terminal message', () {
    var s = const AiStreamState.start('r1').apply(
      event(0, 'completed', {'status': 'completed', 'finishReason': 'stop'}),
    );
    s = s.apply(
      event(1, 'usage', {
        'usage': {'total_tokens': 4},
      }),
    );
    expect(s.phase, AiStreamPhase.completed);
    expect(s.usage['total_tokens'], 4);
  });
  test(
    'oversized output fails without silently retaining an unbounded buffer',
    () {
      final s = const AiStreamState.start('r1').apply(
        event(0, 'answer.delta', {
          'text': 'x' * (AiStreamState.maxTextLength + 1),
        }),
      );
      expect(s.phase, AiStreamPhase.failed);
      expect(s.answer, isEmpty);
      expect(s.errorCode, 'output_limit_exceeded');
    },
  );
}
