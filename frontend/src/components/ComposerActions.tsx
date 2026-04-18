import React from 'react';

interface Props {
  children?: React.ReactNode;
}

// Renders a flex row for composer controls (emoji picker, attach, reply pill).
// Feature #5 (content) and Feature #6 (attachments) inject controls here.
// Returns null when empty to avoid an empty container in the DOM.
export const ComposerActions: React.FC<Props> = ({ children }) => {
  if (!children || (Array.isArray(children) && children.length === 0)) return null;
  return <div className="flex items-center gap-2 mt-2">{children}</div>;
};
