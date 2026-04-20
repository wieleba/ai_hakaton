import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { YouTubeEmbed } from '../YouTubeEmbed';

describe('YouTubeEmbed', () => {
  it('renders from DTO-provided embed when present', () => {
    render(
      <YouTubeEmbed
        text="ignored"
        embeds={[{
          id: 'e1',
          kind: 'youtube',
          canonicalId: 'dQw4w9WgXcQ',
          sourceUrl: 'https://youtu.be/dQw4w9WgXcQ',
          title: 'Rick Astley',
          thumbnailUrl: 'https://img/rick.jpg',
        }]}
      />,
    );
    expect(screen.getByTitle(/YouTube video dQw4w9WgXcQ/)).toBeInTheDocument();
    expect(screen.getByText('Rick Astley')).toBeInTheDocument();
  });

  it('falls back to regex when embeds is empty', () => {
    render(
      <YouTubeEmbed
        text="watch https://youtu.be/dQw4w9WgXcQ now"
        embeds={[]}
      />,
    );
    expect(screen.getByTitle(/YouTube video dQw4w9WgXcQ/)).toBeInTheDocument();
  });

  it('renders nothing when no embed and no regex hit', () => {
    const { container } = render(
      <YouTubeEmbed text="plain text" embeds={[]} />,
    );
    expect(container.querySelector('iframe')).toBeNull();
  });
});
