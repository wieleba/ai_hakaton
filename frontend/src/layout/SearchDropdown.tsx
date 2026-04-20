import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSearch } from '../hooks/useSearch';
import { roomService } from '../services/roomService';
import { directMessageService } from '../services/directMessageService';

export const SearchDropdown: React.FC = () => {
  const [query, setQuery] = useState('');
  const [focused, setFocused] = useState(false);
  const { results, isLoading } = useSearch(query);
  const navigate = useNavigate();

  const reset = () => {
    setQuery('');
    setFocused(false);
  };

  const openRoom = async (id: string) => {
    try {
      await roomService.joinRoom(id);
    } catch {
      /* already member / banned — navigate anyway; ChatPage handles errors */
    }
    navigate(`/rooms/${id}`);
    reset();
  };

  const openUser = async (userId: string) => {
    try {
      const conv = await directMessageService.getOrCreateWith(userId);
      navigate(`/dms/${conv.id}`);
    } catch (e) {
      console.error('Failed to open DM', e);
    }
    reset();
  };

  const show = focused && query.trim().length > 0;

  return (
    <div className="relative">
      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setTimeout(() => setFocused(false), 150)}
        placeholder="Search rooms or users…"
        className="w-full text-sm border rounded px-2 py-1 dark:bg-discord-input dark:border-discord-border dark:text-discord-text dark:placeholder-discord-dim"
      />
      {show && (
        <div className="absolute left-0 right-0 mt-1 bg-white border rounded shadow z-50 max-h-80 overflow-y-auto dark:bg-discord-sidebar dark:border-discord-border">
          {isLoading && (
            <div className="px-3 py-2 text-xs text-gray-400 dark:text-discord-dim">Searching…</div>
          )}
          {!isLoading && results.rooms.length === 0 && results.users.length === 0 && (
            <div className="px-3 py-2 text-xs text-gray-400 dark:text-discord-dim">No matches</div>
          )}
          {results.rooms.length > 0 && (
            <div>
              <div className="px-3 py-1 text-xs font-semibold uppercase text-gray-500 bg-gray-50 dark:bg-discord-deep dark:text-discord-dim">
                Rooms
              </div>
              <ul>
                {results.rooms.map((r) => (
                  <li key={r.id}>
                    <button
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => openRoom(r.id)}
                      className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:text-discord-text dark:hover:bg-discord-hover"
                    >
                      # {r.name}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {results.users.length > 0 && (
            <div>
              <div className="px-3 py-1 text-xs font-semibold uppercase text-gray-500 bg-gray-50 dark:bg-discord-deep dark:text-discord-dim">
                Users
              </div>
              <ul>
                {results.users.map((u) => (
                  <li key={u.id}>
                    <button
                      onMouseDown={(e) => e.preventDefault()}
                      onClick={() => openUser(u.id)}
                      className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:text-discord-text dark:hover:bg-discord-hover"
                    >
                      {u.username}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
