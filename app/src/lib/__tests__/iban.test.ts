import { describe, it, expect } from 'vitest';
import { isValidIbanFormat, normalizeIban } from '../iban';

describe('normalizeIban', () => {
  it('uppercases and strips whitespace', () => {
    expect(normalizeIban('de89 3704 0044 0532 0130 00')).toBe('DE89370400440532013000');
  });
});

describe('isValidIbanFormat', () => {
  it('accepts a valid German IBAN', () => {
    expect(isValidIbanFormat('DE89370400440532013000')).toBe(true);
  });

  it('accepts a valid French IBAN', () => {
    expect(isValidIbanFormat('FR7630006000011234567890189')).toBe(true);
  });

  it('accepts a valid IBAN with spaces (auto-normalised)', () => {
    expect(isValidIbanFormat('DE89 3704 0044 0532 0130 00')).toBe(true);
  });

  it('accepts lowercase input (auto-normalised)', () => {
    expect(isValidIbanFormat('de89370400440532013000')).toBe(true);
  });

  it('rejects a checksum failure (last digit tampered)', () => {
    expect(isValidIbanFormat('DE89370400440532013001')).toBe(false);
  });

  it('rejects a string that is not IBAN-shaped', () => {
    expect(isValidIbanFormat('NOTANIBAN123')).toBe(false);
  });

  it('rejects an empty string', () => {
    expect(isValidIbanFormat('')).toBe(false);
  });

  it('rejects a BBAN shorter than 10 characters', () => {
    expect(isValidIbanFormat('DE8937040')).toBe(false);
  });
});
