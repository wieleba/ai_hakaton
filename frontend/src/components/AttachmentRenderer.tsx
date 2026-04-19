import React from 'react';
import type { AttachmentSummary } from '../types/attachment';
import { isImageMime, prettySize } from '../types/attachment';

interface Props {
  attachment: AttachmentSummary;
}

export const AttachmentRenderer: React.FC<Props> = ({ attachment }) => {
  const url = `/api/attachments/${attachment.id}/content`;
  if (isImageMime(attachment.mimeType)) {
    return (
      <a href={url} target="_blank" rel="noopener noreferrer" className="block mt-2">
        <img
          src={url}
          alt={attachment.filename}
          className="max-h-80 max-w-full rounded border"
        />
      </a>
    );
  }
  return (
    <a
      href={url}
      download={attachment.filename}
      className="inline-flex items-center gap-2 mt-2 text-sm text-blue-600 hover:underline"
    >
      📎 {attachment.filename} · <span className="text-gray-500">{prettySize(attachment.sizeBytes)}</span>
    </a>
  );
};
