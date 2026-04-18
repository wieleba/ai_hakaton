import React, { useEffect, useRef, useState } from 'react';
import EmojiPicker, { EmojiClickData } from 'emoji-picker-react';

interface Props {
  onPick: (emoji: string) => void;
}

export const EmojiPickerButton: React.FC<Props> = ({ onPick }) => {
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onDocClick = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    if (open) document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [open]);

  const handlePick = (d: EmojiClickData) => {
    onPick(d.emoji);
    setOpen(false);
  };

  return (
    <div ref={wrapperRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="px-2 py-1 text-lg hover:bg-gray-100 rounded"
        aria-label="Insert emoji"
      >
        😀
      </button>
      {open && (
        <div className="absolute bottom-full left-0 z-50 mb-1">
          <EmojiPicker onEmojiClick={handlePick} />
        </div>
      )}
    </div>
  );
};
