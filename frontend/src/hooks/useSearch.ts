import { useEffect, useState } from 'react';
import { searchService } from '../services/searchService';
import type { SearchResponse } from '../types/search';

const EMPTY: SearchResponse = { rooms: [], users: [] };
const DEBOUNCE_MS = 200;

export function useSearch(query: string) {
  const [results, setResults] = useState<SearchResponse>(EMPTY);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) {
      setResults(EMPTY);
      setIsLoading(false);
      setError(null);
      return;
    }
    let cancelled = false;
    setIsLoading(true);
    const handle = setTimeout(async () => {
      try {
        const r = await searchService.search(trimmed);
        if (!cancelled) {
          setResults(r);
          setError(null);
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }, DEBOUNCE_MS);
    return () => {
      cancelled = true;
      clearTimeout(handle);
    };
  }, [query]);

  return { results, isLoading, error };
}
