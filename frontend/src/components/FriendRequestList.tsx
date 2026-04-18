import React from 'react';
import type { Friendship } from '../types/friendship';

interface Props {
  incoming: Friendship[];
  outgoing: Friendship[];
  onAccept: (id: string) => void;
  onReject: (id: string) => void;
  onCancel: (id: string) => void;
}

export const FriendRequestList: React.FC<Props> = ({
  incoming,
  outgoing,
  onAccept,
  onReject,
  onCancel,
}) => (
  <div className="space-y-4">
    <section>
      <h3 className="font-semibold mb-2">Incoming requests</h3>
      {incoming.length === 0 ? (
        <p className="text-gray-500 italic">None</p>
      ) : (
        <ul className="divide-y">
          {incoming.map((r) => (
            <li key={r.id} className="flex justify-between py-2">
              <span>Request from user {r.requesterId.slice(0, 8)}</span>
              <div className="space-x-2">
                <button
                  onClick={() => onAccept(r.id)}
                  className="px-3 py-1 bg-green-500 text-white rounded"
                >
                  Accept
                </button>
                <button
                  onClick={() => onReject(r.id)}
                  className="px-3 py-1 border rounded"
                >
                  Reject
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>

    <section>
      <h3 className="font-semibold mb-2">Outgoing requests</h3>
      {outgoing.length === 0 ? (
        <p className="text-gray-500 italic">None</p>
      ) : (
        <ul className="divide-y">
          {outgoing.map((r) => (
            <li key={r.id} className="flex justify-between py-2">
              <span>To user {r.addresseeId.slice(0, 8)}</span>
              <button
                onClick={() => onCancel(r.id)}
                className="px-3 py-1 border rounded"
              >
                Cancel
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  </div>
);
