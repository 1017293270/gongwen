const EARLY_PROGRESS_MS = 2400;
const STEADY_PROGRESS_MS = 9600;
const EARLY_PROGRESS_VALUE = 48;
const STEADY_PROGRESS_VALUE = 78;
const MAX_WAITING_PROGRESS = 88;
const LONG_WAIT_DECAY_MS = 18000;

function clamp01(value: number) {
  return Math.min(1, Math.max(0, value));
}

function easeOutCubic(value: number) {
  const progress = clamp01(value);
  return 1 - Math.pow(1 - progress, 3);
}

export function estimateAiProgress(elapsedMs: number) {
  if (elapsedMs <= 0) {
    return 0;
  }

  if (elapsedMs <= EARLY_PROGRESS_MS) {
    return EARLY_PROGRESS_VALUE * easeOutCubic(elapsedMs / EARLY_PROGRESS_MS);
  }

  const steadyElapsed = elapsedMs - EARLY_PROGRESS_MS;
  if (steadyElapsed <= STEADY_PROGRESS_MS) {
    const steadyRange = STEADY_PROGRESS_VALUE - EARLY_PROGRESS_VALUE;
    return EARLY_PROGRESS_VALUE + steadyRange * easeOutCubic(steadyElapsed / STEADY_PROGRESS_MS);
  }

  const longWaitElapsed = steadyElapsed - STEADY_PROGRESS_MS;
  const longWaitRange = MAX_WAITING_PROGRESS - STEADY_PROGRESS_VALUE;
  return STEADY_PROGRESS_VALUE + longWaitRange * (1 - Math.exp(-longWaitElapsed / LONG_WAIT_DECAY_MS));
}
