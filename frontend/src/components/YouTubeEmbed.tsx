import React from 'react';

interface Props {
  videoId: string;
}

export const YouTubeEmbed: React.FC<Props> = ({ videoId }) => {
  const src = `https://www.youtube-nocookie.com/embed/${videoId}`;
  return (
    <div className="mt-2 relative w-full max-w-xl" style={{ aspectRatio: '16 / 9' }}>
      <iframe
        src={src}
        title={`YouTube video ${videoId}`}
        className="absolute inset-0 w-full h-full rounded border"
        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
        allowFullScreen
        loading="lazy"
      />
    </div>
  );
};
