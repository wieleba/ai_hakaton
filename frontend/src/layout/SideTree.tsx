import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { roomService } from '../services/roomService';
import { friendshipService } from '../services/friendshipService';
import type { ChatRoom } from '../types/room';
import type { FriendView } from '../types/friendship';
import { RoomCreateModal } from '../components/RoomCreateModal';
import { SideTreeRoomList } from './SideTreeRoomList';
import { SideTreeContactList } from './SideTreeContactList';

export const SideTree: React.FC = () => {
  const [rooms, setRooms] = useState<ChatRoom[]>([]);
  const [friends, setFriends] = useState<FriendView[]>([]);
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

  useEffect(() => {
    reloadRooms();
    reloadFriends();
  }, [reloadRooms, reloadFriends, pathname]);

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
      <div className="p-2 border-b">
        <div className="text-xs text-gray-400">Search coming…</div>
      </div>
      <div className="flex-1 overflow-y-auto">
        <div className="py-1">
          <div className="px-2 pt-2 pb-1 text-xs font-bold uppercase text-gray-600">Rooms</div>
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
        <div className="py-1 border-t">
          <SideTreeContactList friends={friends} />
        </div>
      </div>
      <div className="p-2 border-t">
        <button
          onClick={() => setIsCreateOpen(true)}
          className="w-full text-sm px-3 py-2 border rounded hover:bg-gray-100"
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
