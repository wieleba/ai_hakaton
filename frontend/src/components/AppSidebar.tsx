import React from 'react';
import { Link, useLocation } from 'react-router-dom';

interface Props {
  children: React.ReactNode;
}

export const AppSidebar: React.FC<Props> = ({ children }) => {
  const { pathname } = useLocation();
  const item = (to: string, label: string) => {
    const active = pathname === to || pathname.startsWith(to + '/');
    return (
      <Link
        to={to}
        className={`block px-4 py-2 rounded ${active ? 'bg-blue-100 text-blue-700' : 'hover:bg-gray-100'}`}
      >
        {label}
      </Link>
    );
  };

  return (
    <div className="flex h-screen">
      <aside className="w-48 border-r bg-white p-4 space-y-1">
        <h2 className="text-lg font-bold mb-4">Chat</h2>
        {item('/rooms', 'Rooms')}
        {item('/friends', 'Friends')}
        {item('/dms', 'Direct Messages')}
      </aside>
      <main className="flex-1 overflow-hidden">{children}</main>
    </div>
  );
};
