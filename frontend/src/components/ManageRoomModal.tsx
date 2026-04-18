import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { ChatRoom } from '../types/room';
import type { RoomBan, RoomInvitation, RoomMemberView } from '../types/roomModeration';
import { roomService } from '../services/roomService';
import { roomInvitationService } from '../services/roomInvitationService';
import { useRoomMembersWithRole } from '../hooks/useRoomMembersWithRole';
import { useRoomAdminActions } from '../hooks/useRoomAdminActions';

type Tab = 'members' | 'invitations' | 'banned' | 'settings';

interface Props {
  isOpen: boolean;
  onClose: () => void;
  roomId: string;
  currentUserId: string;
  room: ChatRoom | null;
}

export const ManageRoomModal: React.FC<Props> = ({
  isOpen,
  onClose,
  roomId,
  currentUserId,
  room,
}) => {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('members');
  const { members, isOwner, reload: reloadMembers } = useRoomMembersWithRole(
    roomId,
    currentUserId,
  );
  const { kick, promote, demote, unban, deleteRoom } = useRoomAdminActions(roomId);

  const [invitations, setInvitations] = useState<RoomInvitation[]>([]);
  const [bans, setBans] = useState<RoomBan[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [deleteConfirm, setDeleteConfirm] = useState(false);

  const reloadInvitations = useCallback(async () => {
    try {
      setInvitations(await roomInvitationService.listOutgoingForRoom(roomId));
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  }, [roomId]);

  const reloadBans = useCallback(async () => {
    try {
      setBans(await roomService.listBans(roomId));
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  }, [roomId]);

  useEffect(() => {
    if (!isOpen) return;
    setErr(null);
    if (tab === 'invitations') reloadInvitations();
    if (tab === 'banned') reloadBans();
  }, [isOpen, tab, reloadInvitations, reloadBans]);

  if (!isOpen) return null;

  const renderMembers = () => (
    <ul className="divide-y">
      {members.map((m: RoomMemberView) => {
        const isMe = m.userId === currentUserId;
        return (
          <li key={m.userId} className="flex items-center justify-between py-2">
            <div className="flex items-center gap-2">
              <span className="font-medium">{m.username}</span>
              {m.isOwner && (
                <span className="text-xs bg-yellow-100 text-yellow-800 px-2 py-0.5 rounded">
                  owner
                </span>
              )}
              {!m.isOwner && m.role === 'admin' && (
                <span className="text-xs bg-blue-100 text-blue-800 px-2 py-0.5 rounded">
                  admin
                </span>
              )}
              {isMe && <span className="text-xs text-gray-400">(you)</span>}
            </div>
            {!isMe && !m.isOwner && (
              <div className="flex gap-1 text-xs">
                <button
                  onClick={async () => {
                    await kick(m.userId);
                    await reloadMembers();
                  }}
                  className="px-2 py-1 border border-red-400 text-red-600 rounded hover:bg-red-50"
                >
                  Kick
                </button>
                {m.role === 'admin' ? (
                  <button
                    onClick={async () => {
                      await demote(m.userId);
                      await reloadMembers();
                    }}
                    className="px-2 py-1 border rounded hover:bg-gray-100"
                  >
                    Demote
                  </button>
                ) : (
                  <button
                    onClick={async () => {
                      await promote(m.userId);
                      await reloadMembers();
                    }}
                    className="px-2 py-1 border rounded hover:bg-gray-100"
                  >
                    Promote
                  </button>
                )}
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );

  const renderInvitations = () =>
    invitations.length === 0 ? (
      <p className="text-gray-500 italic py-4">No pending invitations.</p>
    ) : (
      <ul className="divide-y">
        {invitations.map((inv) => (
          <li key={inv.id} className="flex items-center justify-between py-2">
            <div>
              <div className="font-medium">
                Invitation {inv.id.slice(0, 8)}
              </div>
              <div className="text-xs text-gray-500">
                sent by {inv.inviterUsername} ·{' '}
                {new Date(inv.createdAt).toLocaleString()}
              </div>
            </div>
            <button
              onClick={async () => {
                await roomInvitationService.cancelInvitation(roomId, inv.id);
                await reloadInvitations();
              }}
              className="px-3 py-1 border rounded hover:bg-gray-100"
            >
              Cancel
            </button>
          </li>
        ))}
      </ul>
    );

  const renderBanned = () =>
    bans.length === 0 ? (
      <p className="text-gray-500 italic py-4">No banned users.</p>
    ) : (
      <ul className="divide-y">
        {bans.map((b) => (
          <li key={b.bannedUserId} className="flex items-center justify-between py-2">
            <div>
              <div className="font-medium">{b.bannedUsername}</div>
              <div className="text-xs text-gray-500">
                banned by {b.bannedByUsername} ·{' '}
                {new Date(b.bannedAt).toLocaleString()}
              </div>
            </div>
            <button
              onClick={async () => {
                await unban(b.bannedUserId);
                await reloadBans();
              }}
              className="px-3 py-1 border rounded hover:bg-gray-100"
            >
              Unban
            </button>
          </li>
        ))}
      </ul>
    );

  const renderSettings = () => (
    <div className="space-y-4 py-2">
      <div>
        <div className="text-xs font-semibold uppercase text-gray-500">Name</div>
        <div className="py-1 font-medium">{room?.name}</div>
      </div>
      {room?.description && (
        <div>
          <div className="text-xs font-semibold uppercase text-gray-500">Description</div>
          <div className="py-1 text-sm text-gray-700">{room.description}</div>
        </div>
      )}
      {isOwner && (
        <div className="pt-4 border-t">
          {!deleteConfirm ? (
            <button
              onClick={() => setDeleteConfirm(true)}
              className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600"
            >
              Delete room
            </button>
          ) : (
            <div className="space-y-2">
              <p className="text-sm text-gray-700">
                This will permanently delete <strong>{room?.name}</strong> and all its messages.
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => setDeleteConfirm(false)}
                  className="px-4 py-2 border rounded hover:bg-gray-100"
                >
                  Cancel
                </button>
                <button
                  onClick={async () => {
                    await deleteRoom();
                    onClose();
                    navigate('/rooms');
                  }}
                  className="px-4 py-2 bg-red-500 text-white rounded hover:bg-red-600"
                >
                  Confirm delete
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );

  const tabClass = (t: Tab) =>
    `px-3 py-2 text-sm ${
      tab === t ? 'border-b-2 border-blue-500 font-semibold' : 'text-gray-500 hover:text-gray-700'
    }`;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg w-[40rem] max-h-[80vh] flex flex-col">
        <div className="flex justify-between items-center px-5 py-3 border-b">
          <h2 className="text-xl font-bold">Manage room</h2>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-700">
            ×
          </button>
        </div>
        <div className="flex gap-2 px-5 border-b">
          <button onClick={() => setTab('members')} className={tabClass('members')}>
            Members
          </button>
          <button onClick={() => setTab('invitations')} className={tabClass('invitations')}>
            Invitations
          </button>
          <button onClick={() => setTab('banned')} className={tabClass('banned')}>
            Banned
          </button>
          <button onClick={() => setTab('settings')} className={tabClass('settings')}>
            Settings
          </button>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-3">
          {err && <div className="text-red-500 text-sm mb-2">{err}</div>}
          {tab === 'members' && renderMembers()}
          {tab === 'invitations' && renderInvitations()}
          {tab === 'banned' && renderBanned()}
          {tab === 'settings' && renderSettings()}
        </div>
      </div>
    </div>
  );
};
