import React, { useState, useEffect } from 'react';
import { ChatRoom } from '../types/room';
import { roomService } from '../services/roomService';
import { RoomCreateModal } from '../components/RoomCreateModal';
import { RoomInvitationList } from '../components/RoomInvitationList';
import { useMyRooms } from '../hooks/useMyRooms';
import { useRoomInvitations } from '../hooks/useRoomInvitations';
import { useNavigate } from 'react-router-dom';

type Tab = 'public' | 'mine';

export const RoomListPage: React.FC = () => {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('public');
  const [publicRooms, setPublicRooms] = useState<ChatRoom[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const { rooms: myRooms, reload: reloadMyRooms } = useMyRooms();
  const { invitations, accept, decline } = useRoomInvitations();

  useEffect(() => {
    (async () => {
      try {
        const result = await roomService.listPublicRooms(0, 20);
        setPublicRooms(result.content);
      } catch (err) {
        setError(err instanceof Error ? err.message : String(err));
      }
    })();
  }, []);

  const handleCreateRoom = async (
    name: string,
    description?: string,
    visibility?: 'public' | 'private',
  ) => {
    const newRoom = await roomService.createRoom(name, description, visibility);
    await reloadMyRooms();
    navigate(`/rooms/${newRoom.id}`);
  };

  const handleAccept = async (id: string) => {
    await accept(id);
    await reloadMyRooms();
  };

  const renderRoomCard = (room: ChatRoom) => (
    <div
      key={room.id}
      className="bg-white rounded-lg shadow p-4 cursor-pointer hover:shadow-lg"
      onClick={() => navigate(`/rooms/${room.id}`)}
    >
      <div className="flex justify-between items-start mb-2">
        <h2 className="text-lg font-bold">{room.name}</h2>
        <span
          className={`text-xs px-2 py-1 rounded ${
            room.visibility === 'private'
              ? 'bg-purple-100 text-purple-700'
              : 'bg-green-100 text-green-700'
          }`}
        >
          {room.visibility}
        </span>
      </div>
      {room.description && <p className="text-gray-600 text-sm mb-4">{room.description}</p>}
      <div className="text-xs text-gray-400">
        Created {new Date(room.createdAt).toLocaleDateString()}
      </div>
    </div>
  );

  return (
    <div className="min-h-screen bg-gray-100 p-6">
      <div className="max-w-6xl mx-auto">
        <div className="flex justify-between items-center mb-6">
          <h1 className="text-3xl font-bold">Chat Rooms</h1>
          <button
            onClick={() => setIsModalOpen(true)}
            className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
          >
            New Room
          </button>
        </div>

        <div className="flex gap-6 border-b mb-6">
          <button
            onClick={() => setTab('public')}
            className={`pb-2 ${
              tab === 'public' ? 'border-b-2 border-blue-500 font-semibold' : 'text-gray-500'
            }`}
          >
            Public rooms
          </button>
          <button
            onClick={() => setTab('mine')}
            className={`pb-2 ${
              tab === 'mine' ? 'border-b-2 border-blue-500 font-semibold' : 'text-gray-500'
            }`}
          >
            My rooms
            {myRooms.length > 0 && (
              <span className="ml-1 text-xs text-gray-400">({myRooms.length})</span>
            )}
          </button>
        </div>

        {error && <div className="text-red-500 mb-4">{error}</div>}

        {tab === 'public' && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {publicRooms.map(renderRoomCard)}
          </div>
        )}

        {tab === 'mine' && (
          <>
            <RoomInvitationList
              invitations={invitations}
              onAccept={handleAccept}
              onDecline={decline}
            />
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {myRooms.map(renderRoomCard)}
            </div>
            {myRooms.length === 0 && invitations.length === 0 && (
              <p className="text-gray-500 italic">You haven't joined any rooms yet.</p>
            )}
          </>
        )}

        <RoomCreateModal
          isOpen={isModalOpen}
          onClose={() => setIsModalOpen(false)}
          onCreate={handleCreateRoom}
        />
      </div>
    </div>
  );
};
