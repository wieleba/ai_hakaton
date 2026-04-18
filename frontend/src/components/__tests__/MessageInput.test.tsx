import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MessageInput } from '../MessageInput';

describe('MessageInput', () => {
  it('sends on Send button click', async () => {
    const onSend = vi.fn();
    const user = userEvent.setup();
    render(<MessageInput onSend={onSend} disabled={false} />);

    await user.type(screen.getByPlaceholderText(/type a message/i), 'hello');
    await user.click(screen.getByRole('button', { name: /send/i }));

    expect(onSend).toHaveBeenCalledWith('hello');
  });

  it('sends on Ctrl+Enter', async () => {
    const onSend = vi.fn();
    const user = userEvent.setup();
    render(<MessageInput onSend={onSend} disabled={false} />);

    const textarea = screen.getByPlaceholderText(/type a message/i);
    await user.type(textarea, 'hi with ctrl');
    await user.keyboard('{Control>}{Enter}{/Control}');

    expect(onSend).toHaveBeenCalledWith('hi with ctrl');
  });

  it('sends on Meta+Enter (Cmd+Enter on Mac)', async () => {
    const onSend = vi.fn();
    const user = userEvent.setup();
    render(<MessageInput onSend={onSend} disabled={false} />);

    const textarea = screen.getByPlaceholderText(/type a message/i);
    await user.type(textarea, 'hi with meta');
    await user.keyboard('{Meta>}{Enter}{/Meta}');

    expect(onSend).toHaveBeenCalledWith('hi with meta');
  });

  it('plain Enter does NOT send (allows multi-line input)', async () => {
    const onSend = vi.fn();
    const user = userEvent.setup();
    render(<MessageInput onSend={onSend} disabled={false} />);

    const textarea = screen.getByPlaceholderText(/type a message/i);
    await user.type(textarea, 'line one{Enter}line two');

    expect(onSend).not.toHaveBeenCalled();
    expect(textarea).toHaveValue('line one\nline two');
  });

  it('does not send whitespace-only text', async () => {
    const onSend = vi.fn();
    const user = userEvent.setup();
    render(<MessageInput onSend={onSend} disabled={false} />);

    const textarea = screen.getByPlaceholderText(/type a message/i);
    await user.type(textarea, '   ');
    await user.keyboard('{Control>}{Enter}{/Control}');

    expect(onSend).not.toHaveBeenCalled();
  });

  it('does not send when disabled', async () => {
    const onSend = vi.fn();
    const user = userEvent.setup();
    render(<MessageInput onSend={onSend} disabled={true} />);

    const textarea = screen.getByPlaceholderText(/type a message/i);
    // fill via paste since disabled textarea won't accept typed chars
    await user.click(textarea);
    await user.keyboard('{Control>}{Enter}{/Control}');

    expect(onSend).not.toHaveBeenCalled();
  });

  it('clears the textarea after sending', async () => {
    const onSend = vi.fn();
    const user = userEvent.setup();
    render(<MessageInput onSend={onSend} disabled={false} />);

    const textarea = screen.getByPlaceholderText(/type a message/i);
    await user.type(textarea, 'bye');
    await user.keyboard('{Control>}{Enter}{/Control}');

    expect(textarea).toHaveValue('');
  });
});
