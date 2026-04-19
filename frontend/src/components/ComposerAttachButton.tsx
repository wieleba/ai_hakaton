import React, { useRef } from 'react';

interface Props {
  onFile: (file: File) => void;
  disabled?: boolean;
}

const ACCEPT = 'image/png,image/jpeg,image/gif,image/webp,application/pdf,text/plain,application/zip';

export const ComposerAttachButton: React.FC<Props> = ({ onFile, disabled }) => {
  const inputRef = useRef<HTMLInputElement>(null);

  const onChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) onFile(f);
    e.target.value = '';
  };

  return (
    <>
      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={disabled}
        className="px-2 py-1 text-lg hover:bg-gray-100 rounded disabled:opacity-50"
        aria-label="Attach file"
      >
        📎
      </button>
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        onChange={onChange}
        className="hidden"
      />
    </>
  );
};
