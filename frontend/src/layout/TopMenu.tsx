import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { ProfileMenu } from './ProfileMenu';

interface Props {
  username: string;
}

export const TopMenu: React.FC<Props> = ({ username }) => {
  const { pathname } = useLocation();
  const linkCls = (to: string) => {
    const active = pathname === to || pathname.startsWith(to + '/');
    return active
      ? 'px-3 py-2 rounded bg-blue-100 text-blue-700 dark:bg-discord-accent/20 dark:text-blue-300'
      : 'px-3 py-2 rounded hover:bg-gray-100 dark:text-discord-text dark:hover:bg-discord-hover';
  };
  return (
    <nav
      aria-label="Primary"
      className="flex items-center gap-2 px-4 py-2 border-b bg-white shadow-sm dark:bg-discord-deep dark:border-discord-border"
    >
      <Link to="/rooms" className="font-bold text-lg mr-4 dark:text-discord-text">
        Chat
      </Link>
      <Link to="/rooms" className={linkCls('/rooms')}>
        Public Rooms
      </Link>
      <Link to="/friends" className={linkCls('/friends')}>
        Contacts
      </Link>
      <Link to="/sessions" className={linkCls('/sessions')}>
        Sessions
      </Link>
      <Link to="/jabber" className={linkCls('/jabber')}>
        Jabber
      </Link>
      <div className="flex-1" />
      <ProfileMenu username={username} />
    </nav>
  );
};
