/** Extract unique YouTube video IDs from a text blob, preserving encounter order. */
const PATTERNS: RegExp[] = [
  // youtube.com/watch?v=ID (with any other params)
  /(?:https?:\/\/)?(?:www\.)?youtube\.com\/watch\?(?:[^\s"'<>]*&)?v=([a-zA-Z0-9_-]{11})(?:[^a-zA-Z0-9_-][^\s"'<>]*)?/g,
  // youtu.be/ID
  /(?:https?:\/\/)?(?:www\.)?youtu\.be\/([a-zA-Z0-9_-]{11})(?:[?#&][^\s"'<>]*)?/g,
  // youtube.com/shorts/ID
  /(?:https?:\/\/)?(?:www\.)?youtube\.com\/shorts\/([a-zA-Z0-9_-]{11})(?:[?#&][^\s"'<>]*)?/g,
  // youtube.com/embed/ID
  /(?:https?:\/\/)?(?:www\.)?youtube\.com\/embed\/([a-zA-Z0-9_-]{11})(?:[?#&][^\s"'<>]*)?/g,
];

interface Hit {
  id: string;
  index: number;
}

export function extractYouTubeIds(text: string | null | undefined): string[] {
  if (!text) return [];
  const hits: Hit[] = [];
  for (const re of PATTERNS) {
    // Reset lastIndex because regex has /g flag.
    re.lastIndex = 0;
    let m: RegExpExecArray | null;
    while ((m = re.exec(text)) !== null) {
      hits.push({ id: m[1], index: m.index });
    }
  }
  // Sort by encounter order in the original text, deduplicate.
  hits.sort((a, b) => a.index - b.index);
  const seen = new Set<string>();
  const result: string[] = [];
  for (const h of hits) {
    if (!seen.has(h.id)) {
      seen.add(h.id);
      result.push(h.id);
    }
  }
  return result;
}
