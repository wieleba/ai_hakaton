import axios from 'axios';
import type { SearchResponse } from '../types/search';

export const searchService = {
  async search(query: string, limit = 5): Promise<SearchResponse> {
    const q = query.trim();
    if (!q) return { rooms: [], users: [] };
    const params = new URLSearchParams({ q, limit: String(limit) });
    return (await axios.get(`/api/search?${params.toString()}`)).data;
  },
};
