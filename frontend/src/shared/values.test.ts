import { describe, expect, it } from 'vitest';
import samples from '../../../shared/fixtures/values.json';
import { codePoints, money, parseMoney, password, requiredText, safeInteger } from './values';
describe('shared value contract', () => {
  it('displays every safe integer without floating point rounding', () => {
    for (const row of samples.money) {
      expect(money(row.cents)).toBe(row.display);
      expect(parseMoney(row.display)).toBe(row.cents);
    }
    for (const input of ['-1', '1e3', 'NaN', 'Infinity', '1.001', '90071992547409.92', '', '1.']) expect(() => parseMoney(input)).toThrow();
    for (const value of [-1, 0.1, Infinity, NaN, Number.MAX_SAFE_INTEGER + 1]) expect(() => safeInteger(value)).toThrow();
  });
  it('normalizes text and measures Unicode code points consistently', () => {
    for (const row of samples.texts) {
      expect(requiredText(row.input, 200)).toBe(row.normalized);
      expect(codePoints(row.normalized)).toBe(row.codePoints);
    }
    expect(requiredText('🍜'.repeat(200), 200)).toBe('🍜'.repeat(200));
    for (const text of ['🍜'.repeat(201), ' \n　', '\uD800']) expect(() => requiredText(text, 200)).toThrow();
  });
  it('keeps password whitespace and accepts 128 code points', () => {
    expect(password(samples.password)).toBe(samples.password);
    expect(password('A1' + '🍜'.repeat(126))).toBe('A1' + '🍜'.repeat(126));
    for (const input of ['A123456', 'abcdefgh', '12345678', 'A1' + 'x'.repeat(127)]) expect(() => password(input)).toThrow();
  });
});
