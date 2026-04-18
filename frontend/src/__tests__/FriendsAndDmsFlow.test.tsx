import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { FriendsPage } from '../pages/FriendsPage';
import { friendshipService } from '../services/friendshipService';
import { banService } from '../services/banService';
import { directMessageService } from '../services/directMessageService';

vi.mock('../services/friendshipService');
vi.mock('../services/banService');
vi.mock('../services/directMessageService');

describe('FriendsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (friendshipService.listFriends as any).mockResolvedValue([]);
    (friendshipService.listIncoming as any).mockResolvedValue([]);
    (friendshipService.listOutgoing as any).mockResolvedValue([]);
  });

  it('renders empty state', async () => {
    render(
      <MemoryRouter>
        <FriendsPage />
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.getByText(/No friends yet/i)).toBeInTheDocument());
  });

  it('sends a friend request', async () => {
    (friendshipService.sendRequest as any).mockResolvedValue({
      id: 'r1',
      requesterId: 'me',
      addresseeId: 'u2',
      status: 'pending',
      createdAt: '',
      updatedAt: '',
    });

    const user = userEvent.setup();
    render(
      <MemoryRouter>
        <FriendsPage />
      </MemoryRouter>,
    );

    const input = await screen.findByPlaceholderText('Username');
    await user.type(input, 'bob');
    await user.click(screen.getByText('Send request'));

    await waitFor(() =>
      expect(friendshipService.sendRequest).toHaveBeenCalledWith('bob'),
    );
  });
});
