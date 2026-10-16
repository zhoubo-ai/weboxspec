import { render, screen } from '@testing-library/react';
import { expect, test } from 'vitest';
import { Bootstrap } from './Bootstrap';

test('the React entry renders an accessible application landmark', () => {
  render(<Bootstrap />);
  expect(screen.getByRole('main')).toContainElement(screen.getByRole('heading', { level: 1, name: 'WeBox' }));
});
