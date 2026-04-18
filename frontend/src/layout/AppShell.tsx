import React from 'react';
import { Outlet } from 'react-router-dom';
import { TopMenu } from './TopMenu';
import { RightPanel } from './RightPanel';

const getUsername = (): string => {
  const token = localStorage.getItem('authToken');
  if (!token) return 'User';
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return typeof payload.username === 'string' ? payload.username : 'User';
  } catch {
    return 'User';
  }
};

export const AppShell: React.FC = () => {
  const username = getUsername();
  return (
    <div className="flex flex-col h-screen">
      <TopMenu username={username} />
      <div className="flex flex-1 min-h-0">
        <aside className="w-64 border-r bg-white overflow-y-auto" aria-label="Workspace">
          {/* SideTree goes here in Task 4 */}
          <div className="p-4 text-sm text-gray-400">Sidebar coming…</div>
        </aside>
        <main className="flex-1 min-w-0 overflow-hidden">
          <Outlet />
        </main>
        <RightPanel />
      </div>
    </div>
  );
};
