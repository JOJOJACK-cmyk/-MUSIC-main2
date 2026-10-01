import React, { useEffect } from 'react';
import { createPortal } from 'react-dom';

/** 모바일 하단 시트 (배경 탭·Esc 로 닫힘) */
export default function MobileSheet({ open, title, onClose, children, tall = false }) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e) => e.key === 'Escape' && onClose?.();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;
  return createPortal(
    <div className="m-sheet-backdrop" onClick={onClose}>
      <div className={`m-sheet ${tall ? 'tall' : ''}`} onClick={(e) => e.stopPropagation()} role="dialog" aria-label={title}>
        <div className="m-sheet-grip" />
        {title && <div className="m-sheet-title">{title}</div>}
        <div className="m-sheet-body">{children}</div>
      </div>
    </div>,
    document.body
  );
}
