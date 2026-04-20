import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { roomService } from '../services/roomService';
import { friendshipService } from '../services/friendshipService';
import { directMessageService } from '../services/directMessageService';
import type { ChatRoom } from '../types/room';
import type { FriendView } from '../types/friendship';
import type { ConversationView } from '../types/directMessage';
import { RoomCreateModal } from '../components/RoomCreateModal';
import { SideTreeRoomList } from './SideTreeRoomList';
import { SideTreeContactList } from './SideTreeContactList';
import { SearchDropdown } from './SearchDropdown';

export const SideTree: React.FC = () => {
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [friends, setFriends] = useState<FriendView[]>([]);
  const [conversations, setConversations] = useState<ConversationView[]>([]);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const { pathname } = useLocation();

  const reloadRooms = useCallback(async () => {
    try {
      setRooms(await roomService.listMyRooms());
    } catch (e) {
      console.error('Failed to load sidebar rooms', e);
    }
  }, []);

  const reloadFriends = useCallback(async () => {
    try {
      setFriends(await friendshipService.listFriends());
    } catch (e) {
      console.error('Failed to load contacts', e);
    }
  }, []);

  const reloadConversations = useCallback(async () => {
    try {
      setConversations(await directMessageService.listConversations());
    } catch (e) {
      console.error('Failed to load conversations', e);
    }
  }, []);

  useEffect(() => {
    reloadRooms();
    reloadFriends();
    reloadConversations();
  }, [reloadRooms, reloadFriends, reloadConversations, pathname]);

  const publicRooms = useMemo(() => rooms.filter((r) => r.visibility === 'public'), [rooms]);
  const privateRooms = useMemo(() => rooms.filter((r) => r.visibility === 'private'), [rooms]);

  const handleCreateRoom = async (
    name: string,
    description?: string,
    visibility?: 'public' | 'private',
  ) => {
    await roomService.createRoom(name, description, visibility);
    await reloadRooms();
  };

  return (
    <div className="h-full flex flex-col">
      <div className="p-2 border-b dark:border-discord-border">
        <SearchDropdown />
      </div>
      <div className="flex-1 overflow-y-auto">
        <div className="py-1">
          <div className="px-2 pt-2 pb-1 text-xs font-bold uppercase text-gray-600 dark:text-discord-dim">Rooms</div>
          <SideTreeRoomList
            title="Public"
            rooms={publicRooms}
            emptyHint="No public rooms joined"
          />
          <SideTreeRoomList
            title="Private"
            rooms={privateRooms}
            emptyHint="No private rooms"
          />
        </div>
        <div className="py-1 border-t dark:border-discord-border">
          <SideTreeContactList friends={friends} conversations={conversations} />
        </div>
      </div>
      <div className="p-2 border-t dark:border-discord-border">
        <button
          onClick={() => setIsCreateOpen(true)}
          className="w-full text-sm px-3 py-2 border rounded hover:bg-gray-100 dark:border-discord-border dark:text-discord-text dark:hover:bg-discord-hover"
        >
          + Create room
        </button>
      </div>
      <RoomCreateModal
        isOpen={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
        onCreate={handleCreateRoom}
      />
    </div>
  );
};
