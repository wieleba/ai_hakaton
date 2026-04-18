import React from 'react';

export const DirectMessagesPage: React.FC = () => {
  return (
    <div className="p-8 max-w-2xl">
      <h1 className="text-2xl font-bold mb-2">Direct Messages</h1>
      <p className="text-gray-600">
        Pick a contact from the sidebar to start a direct message.
      </p>
    </div>
  );
};
