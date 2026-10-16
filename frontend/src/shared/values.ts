export const MAX_SAFE_INTEGER = Number.MAX_SAFE_INTEGER;
export function safeInteger(value: number): number {
  if (!Number.isSafeInteger(value) || value < 0) throw new Error('Value must be a non-negative safe integer.');
  return value;
}
export function money(cents: number): string {
  const exact = BigInt(safeInteger(cents));
  return `${exact / 100n}.${String(exact % 100n).padStart(2, '0')}`;
}
export function parseMoney(input: string): number {
  if (!/^\d{1,14}(\.\d{1,2})?$/.test(input)) throw new Error('Enter a valid amount with at most two decimal places.');
  const [whole, fraction = ''] = input.split('.');
  const cents = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
  if (cents > BigInt(MAX_SAFE_INTEGER)) throw new Error('Amount exceeds the supported limit.');
  return Number(cents);
}
export function codePoints(text: string): number {
  // A lone surrogate cannot be stored faithfully in utf8mb4.
  for (const point of text) {
    const code = point.codePointAt(0)!;
    if (code >= 0xd800 && code <= 0xdfff) throw new Error('Text contains invalid Unicode.');
  }
  return Array.from(text).length;
}
export function requiredText(text: string, max: number): string {
  const normalized = text.trim();
  const length = codePoints(normalized);
  if (!length || length > max) throw new Error(`Text must contain 1 to ${max} characters.`);
  return normalized;
}
export function password(text: string): string {
  const length = codePoints(text);
  if (length < 8 || length > 128 || !/[A-Za-z]/.test(text) || !/[0-9]/.test(text)) {
    throw new Error('Password must contain 8 to 128 characters, including a letter and a number.');
  }
  return text;
}
