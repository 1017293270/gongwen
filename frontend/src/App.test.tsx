import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { App } from './App';

describe('App', () => {
  it('renders the gongwen workbench shell', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: '公文助手' })).toBeInTheDocument();
    expect(screen.getByLabelText('起草信息')).toBeInTheDocument();
    expect(screen.getByLabelText('公文预览')).toBeInTheDocument();
    expect(screen.getByLabelText('AI 建议和质检')).toBeInTheDocument();
  });
});
