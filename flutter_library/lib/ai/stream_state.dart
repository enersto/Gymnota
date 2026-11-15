/// Offline reducer for the planned coach EventChannel. No network or persistence.
enum AiStreamPhase {
  connecting,
  reasoning,
  answering,
  completed,
  incomplete,
  cancelled,
  failed,
}

class AiStreamState {
  static const maxTextLength = 1000000;
  final String requestId, answer, reasoningSummary, providerReasoning, refusal;
  final String? finishReason, errorCode;
  final int sequence;
  final AiStreamPhase phase;
  final Map<String, dynamic> usage;
  const AiStreamState.start(this.requestId)
    : answer = '',
      reasoningSummary = '',
      providerReasoning = '',
      refusal = '',
      finishReason = null,
      errorCode = null,
      sequence = -1,
      phase = AiStreamPhase.connecting,
      usage = const {};
  const AiStreamState._(
    this.requestId,
    this.answer,
    this.reasoningSummary,
    this.providerReasoning,
    this.refusal,
    this.finishReason,
    this.errorCode,
    this.sequence,
    this.phase,
    this.usage,
  );
  bool get terminal => [
    AiStreamPhase.completed,
    AiStreamPhase.incomplete,
    AiStreamPhase.cancelled,
    AiStreamPhase.failed,
  ].contains(phase);

  /// Eligibility for a separate plan validator, not permission to import or save.
  String? get completedAnswerForValidation =>
      phase == AiStreamPhase.completed &&
          finishReason == 'stop' &&
          refusal.isEmpty &&
          answer.isNotEmpty
      ? answer
      : null;
  AiStreamState apply(Map<String, dynamic> event) {
    final seq = event['seq'];
    if (event['requestId'] != requestId || seq is! int || seq <= sequence)
      return this;
    final kind = event['kind'];
    if (terminal && kind != 'usage') return this;
    var body = answer,
        summary = reasoningSummary,
        provider = providerReasoning,
        refused = refusal;
    var reason = finishReason, error = errorCode, nextPhase = phase;
    var tokens = usage;
    final text = event['text'] is String ? event['text'] as String : '';
    switch (kind) {
      case 'answer.delta':
        body += text;
        nextPhase = AiStreamPhase.answering;
      case 'reasoning.summary.delta':
        summary += text;
        nextPhase = body.isEmpty
            ? AiStreamPhase.reasoning
            : AiStreamPhase.answering;
      case 'reasoning.provider.delta':
        provider += text;
        nextPhase = body.isEmpty
            ? AiStreamPhase.reasoning
            : AiStreamPhase.answering;
      case 'refusal.delta':
        refused += text;
        nextPhase = AiStreamPhase.answering;
      case 'finish':
        reason = event['reason'] as String?;
      case 'usage':
        if (event['usage'] is Map)
          tokens = Map<String, dynamic>.unmodifiable(
            Map<String, dynamic>.from(event['usage']),
          );
      case 'completed':
        reason = event['finishReason'] as String? ?? reason;
        nextPhase = event['status'] == 'completed' && reason == 'stop'
            ? AiStreamPhase.completed
            : AiStreamPhase.incomplete;
      case 'cancelled':
        nextPhase = AiStreamPhase.cancelled;
      case 'failed':
        nextPhase = AiStreamPhase.failed;
        error = event['code'] as String? ?? 'remote_error';
      default:
        return this;
    }
    if (body.length + summary.length + provider.length + refused.length >
        maxTextLength) {
      return AiStreamState._(
        requestId,
        answer,
        reasoningSummary,
        providerReasoning,
        refusal,
        finishReason,
        'output_limit_exceeded',
        seq,
        AiStreamPhase.failed,
        usage,
      );
    }
    return AiStreamState._(
      requestId,
      body,
      summary,
      provider,
      refused,
      reason,
      error,
      seq,
      nextPhase,
      tokens,
    );
  }
}
