import React, { useEffect, useState } from 'react';
import axios from 'axios';
import type { AttachmentSummary } from '../types/attachment';
import { isImageMime, prettySize } from '../types/attachment';

interface Props {
  attachment: AttachmentSummary;
}

/**
 * Fetches attachment bytes via axios (which injects the auth header) and turns
 * them into a blob: URL. Native <img src> / <a href> can't carry custom headers
 * themselves, so we download once through the authenticated channel and hand
 * the browser a local URL for rendering + downloading. URL is revoked on
 * unmount / attachment change.
 */
export const AttachmentRenderer: React.FC<Props> = ({ attachment }) => {
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    let url: string | null = null;
    (async () => {
      try {
        const response = await axios.get(`/api/attachments/${attachment.id}/content`, {
          responseType: 'blob',
        });
        if (cancelled) return;
        url = URL.createObjectURL(response.data as Blob);
        setBlobUrl(url);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load attachment');
      }
    })();
    return () => {
      cancelled = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [attachment.id]);

  if (error) {
    return (
      <div className="mt-2 text-xs text-red-500">
        Failed to load attachment: {error}
      </div>
    );
  }
  if (!blobUrl) {
    return (
      <div className="mt-2 text-xs text-gray-400 italic">
        📎 Loading {attachment.filename}…
      </div>
    );
  }

  if (isImageMime(attachment.mimeType)) {
    return (
      <a href={blobUrl} target="_blank" rel="noopener noreferrer" className="block mt-2">
        <img
          src={blobUrl}
          alt={attachment.filename}
          className="max-h-80 max-w-full rounded border"
        />
      </a>
    );
  }
  return (
    <a
      href={blobUrl}
      download={attachment.filename}
      className="inline-flex items-center gap-2 mt-2 text-sm text-blue-600 hover:underline"
    >
      📎 {attachment.filename} ·{' '}
      <span className="text-gray-500">{prettySize(attachment.sizeBytes)}</span>
    </a>
  );
};
