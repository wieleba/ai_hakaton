import React from 'react';
import { SendFriendRequestForm } from '../components/SendFriendRequestForm';
import { FriendsList } from '../components/FriendsList';
import { FriendRequestList } from '../components/FriendRequestList';
import { useFriends } from '../hooks/useFriends';
import { useFriendRequests } from '../hooks/useFriendRequests';

export const FriendsPage: React.FC = () => {
  const { friends, removeFriend, banUser, reload: reloadFriends } = useFriends();
  const { incoming, outgoing, sendRequest, accept, reject, cancel } = useFriendRequests();

  const handleSendRequest = async (username: string) => {
    const req = await sendRequest(username);
    if (req.status === 'accepted') {
      await reloadFriends();
    }
  };

  const handleAccept = async (id: string) => {
    await accept(id);
    await reloadFriends();
  };

  return (
    <div className="max-w-2xl mx-auto p-6 space-y-6 overflow-y-auto h-full dark:text-discord-text">
      <h1 className="text-2xl font-bold">Friends</h1>
      <SendFriendRequestForm onSubmit={handleSendRequest} />
      <FriendRequestList
        incoming={incoming}
        outgoing={outgoing}
        onAccept={handleAccept}
        onReject={reject}
        onCancel={cancel}
      />
      <hr className="dark:border-discord-border" />
      <h2 className="text-xl font-semibold">Friends list</h2>
      <FriendsList friends={friends} onRemove={removeFriend} onBan={banUser} />
    </div>
  );
};
