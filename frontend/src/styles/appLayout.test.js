import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

const css = readFileSync(resolve(process.cwd(), 'src/styles/app.css'), 'utf8');

describe('app layout CSS', () => {
  it('uses a fixed-slot card grid for document-type, draft, and template cards', () => {
    const fixedSlotGrid =
      'grid-template-columns: repeat(auto-fill, minmax(min(100%, var(--template-card-min-width)), 1fr));';

    expect(css).toContain('--template-card-min-width: 300px');
    expect(css.split(fixedSlotGrid)).toHaveLength(3);
    expect(css).not.toContain('grid-template-columns: repeat(auto-fit, minmax(min(100%, var(--template-card-min-width)), 1fr));');
    expect(css).not.toContain('grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));');
    expect(css).not.toContain('grid-template-columns: repeat(auto-fill, minmax(280px, 320px));');
  });
});
