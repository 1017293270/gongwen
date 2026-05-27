import { describe, expect, it } from 'vitest';

import { estimateAiProgress } from './progress';

describe('estimateAiProgress', () => {
  it('starts from zero and moves through the early range smoothly', () => {
    const firstTick = estimateAiProgress(250);
    const secondTick = estimateAiProgress(500);
    const thirdTick = estimateAiProgress(1000);

    expect(estimateAiProgress(0)).toBe(0);
    expect(firstTick).toBeGreaterThan(0);
    expect(secondTick).toBeGreaterThan(firstTick);
    expect(thirdTick).toBeGreaterThan(secondTick);
  });

  it('does not rush into the final waiting range for long requests', () => {
    expect(estimateAiProgress(12_000)).toBeLessThan(82);
    expect(estimateAiProgress(60_000)).toBeLessThan(90);
  });
});
