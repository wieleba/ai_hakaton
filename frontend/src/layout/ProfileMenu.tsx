import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTheme } from '../hooks/useTheme';

interface Props {
  username: string;
}

export const ProfileMenu: React.FC<Props> = ({ username }) => {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { theme, toggle: toggleTheme } = useTheme();

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const signOut = () => {
    localStorage.removeItem('authToken');
    navigate('/login', { replace: true });
  };

  const goToAccount = () => {
    setOpen(false);
    navigate('/account');
  };

  return (
    <div ref={ref} className="relative">
      <button
        onClick={() => setOpen((v) => !v)}
        aria-haspopup="menu"
        aria-expanded={open}
        className="flex items-center gap-1 px-3 py-2 rounded hover:bg-gray-100 dark:text-discord-text dark:hover:bg-discord-hover"
      >
        {username} <span aria-hidden>▼</span>
      </button>
      {open && (
        <div
          role="menu"
          className="absolute right-0 mt-1 w-48 rounded shadow z-50 bg-white border dark:bg-discord-sidebar dark:border-discord-border"
        >
          <div className="px-3 py-2 text-xs text-gray-500 border-b dark:text-discord-dim dark:border-discord-border">
            Signed in as
          </div>
          <div className="px-3 py-2 text-sm truncate dark:text-discord-text">{username}</div>
          <button
            onClick={goToAccount}
            className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:text-discord-text dark:hover:bg-discord-hover"
            role="menuitem"
          >
            Account settings
          </button>
          <button
            onClick={() => {
              setOpen(false);
              toggleTheme();
            }}
            className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:text-discord-text dark:hover:bg-discord-hover"
            role="menuitem"
          >
            {theme === 'dark' ? '☀ Light theme' : '☾ Dark theme'}
          </button>
          <div className="border-t my-1 dark:border-discord-border" />
          <button
            onClick={signOut}
            className="w-full text-left px-3 py-2 text-sm text-red-600 hover:bg-red-50 dark:hover:bg-red-900/30"
            role="menuitem"
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  );
};
