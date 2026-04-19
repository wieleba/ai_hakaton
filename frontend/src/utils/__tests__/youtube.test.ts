import { describe, it, expect } from 'vitest';
import { extractYouTubeIds } from '../youtube';

describe('extractYouTubeIds', () => {
  it('returns empty array for null/empty/no-match text', () => {
    expect(extractYouTubeIds(null)).toEqual([]);
    expect(extractYouTubeIds(undefined)).toEqual([]);
    expect(extractYouTubeIds('')).toEqual([]);
    expect(extractYouTubeIds('hello there, nothing to see here')).toEqual([]);
  });

  it('extracts id from youtube.com/watch?v=...', () => {
    expect(extractYouTubeIds('check this https://www.youtube.com/watch?v=dQw4w9WgXcQ cool'))
      .toEqual(['dQw4w9WgXcQ']);
  });

  it('extracts id from youtu.be/...', () => {
    expect(extractYouTubeIds('short: https://youtu.be/dQw4w9WgXcQ'))
      .toEqual(['dQw4w9WgXcQ']);
  });

  it('extracts id from youtube.com/shorts/...', () => {
    expect(extractYouTubeIds('shorts: https://www.youtube.com/shorts/abcdefghijk'))
      .toEqual(['abcdefghijk']);
  });

  it('extracts id from youtube.com/embed/...', () => {
    expect(extractYouTubeIds('embed: https://www.youtube.com/embed/abcdefghijk'))
      .toEqual(['abcdefghijk']);
  });

  it('ignores extra params like &t=30s', () => {
    expect(extractYouTubeIds('https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30s'))
      .toEqual(['dQw4w9WgXcQ']);
  });

  it('handles http:// and no-protocol and missing www', () => {
    expect(extractYouTubeIds('youtu.be/dQw4w9WgXcQ')).toEqual(['dQw4w9WgXcQ']);
    expect(extractYouTubeIds('http://youtube.com/watch?v=dQw4w9WgXcQ')).toEqual(['dQw4w9WgXcQ']);
  });

  it('extracts multiple distinct ids, preserving order', () => {
    const text = 'first https://youtu.be/AAAAAAAAAAA then https://www.youtube.com/watch?v=BBBBBBBBBBB';
    expect(extractYouTubeIds(text)).toEqual(['AAAAAAAAAAA', 'BBBBBBBBBBB']);
  });

  it('deduplicates repeated ids', () => {
    const text = 'https://youtu.be/XXXXXXXXXXX https://youtube.com/watch?v=XXXXXXXXXXX';
    expect(extractYouTubeIds(text)).toEqual(['XXXXXXXXXXX']);
  });

  it('ignores v= with wrong length', () => {
    // 10-char id, should not match
    expect(extractYouTubeIds('https://www.youtube.com/watch?v=short123ID')).toEqual([]);
  });
});
