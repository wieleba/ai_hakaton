import React from 'react';
import { Outlet } from 'react-router-dom';
import { TopMenu } from './TopMenu';
import { RightPanel } from './RightPanel';
import { SideTree } from './SideTree';
import { useAfkTracking } from '../hooks/useAfkTracking';
import { useEvictedSessionWatcher } from '../hooks/useEvictedSessionWatcher';

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
  useAfkTracking();
  useEvictedSessionWatcher();
  const username = getUsername();
  return (
    <div className="flex flex-col h-screen dark:bg-discord-base">
      <TopMenu username={username} />
      <div className="flex flex-1 min-h-0">
        <aside
          className="w-64 border-r bg-white overflow-y-auto dark:bg-discord-sidebar dark:border-discord-border"
          aria-label="Workspace"
        >
          <SideTree />
        </aside>
        <main className="flex-1 min-w-0 overflow-hidden dark:bg-discord-base">
          <Outlet />
        </main>
        <RightPanel />
      </div>
    </div>
  );
};
