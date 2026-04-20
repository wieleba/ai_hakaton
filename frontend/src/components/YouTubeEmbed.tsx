import React from 'react';
import type { Embed } from '../types/room';
import { extractYouTubeIds } from '../utils/youtube';

interface Props {
  text: string | null | undefined;
  embeds?: Embed[];
}

export const YouTubeEmbed: React.FC<Props> = ({ text, embeds }) => {
  // Prefer DTO-provided embeds; fall back to regex for unbackfilled messages.
  const youtubeEmbeds = (embeds ?? []).filter((e) => e.kind === 'youtube');
  const items: { id: string; title: string | null; thumbnailUrl: string | null }[] =
    youtubeEmbeds.length > 0
      ? youtubeEmbeds.map((e) => ({
          id: e.canonicalId,
          title: e.title,
          thumbnailUrl: e.thumbnailUrl,
        }))
      : extractYouTubeIds(text).map((id) => ({ id, title: null, thumbnailUrl: null }));

  if (items.length === 0) return null;

  return (
    <>
      {items.map((it) => (
        <div key={it.id} className="mt-2 max-w-xl">
          {it.title && (
            <div className="text-sm font-medium mb-1 dark:text-discord-text">{it.title}</div>
          )}
          <div className="relative w-full" style={{ aspectRatio: '16 / 9' }}>
            <iframe
              src={`https://www.youtube-nocookie.com/embed/${it.id}`}
              title={`YouTube video ${it.id}`}
              className="absolute inset-0 w-full h-full rounded border dark:border-discord-border"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
              loading="lazy"
            />
          </div>
        </div>
      ))}
    </>
  );
};
