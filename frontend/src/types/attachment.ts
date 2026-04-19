export interface AttachmentSummary {
  id: string;
  filename: string;
  mimeType: string;
  sizeBytes: number;
}

export const isImageMime = (m?: string | null): boolean =>
  !!m && /^image\/(png|jpeg|gif|webp)$/.test(m);

export const prettySize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};
