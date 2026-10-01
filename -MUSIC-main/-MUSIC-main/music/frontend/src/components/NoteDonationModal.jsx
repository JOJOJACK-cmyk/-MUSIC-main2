import React, { useState } from 'react';
import api from '../api/axiosInstance';
import TossCheckoutModal from './TossCheckoutModal';

const PRESETS = [1000, 5000, 10000, 50000];
const MIN = 1000;
const MAX = 500000;

/**
 * 음표 보내기 (라이브 방송자 후원). 1음표 = 1원.
 * 금액·메시지를 고르면 서버가 음표 주문을 만들고, 토스 결제 후 /notes/result 에서 승인된다.
 */
export default function NoteDonationModal({ broadcastId, broadcasterName, onClose }) {
  const [amount, setAmount] = useState(5000);
  const [custom, setCustom] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [order, setOrder] = useState(null); // 서버가 만든 음표 주문 → 결제창

  const value = custom !== '' ? Number(custom) : amount;
  const valid = Number.isInteger(value) && value >= MIN && value <= MAX;

  const submit = async () => {
    if (!valid || busy) return;
    setBusy(true);
    setError('');
    try {
      const r = await api.post('/api/notes', { broadcastId: Number(broadcastId), amount: value, message });
      setOrder(r.data);
    } catch (e) {
      setError(e?.response?.data?.message || '음표 주문을 만들지 못했어요.');
    }
    setBusy(false);
  };

  if (order) {
    const back = encodeURIComponent(broadcastId);
    return (
      <TossCheckoutModal
        orderId={order.orderId}
        amount={order.amount}
        orderName={order.orderName}
        title="♪ 음표 결제"
        accent="#ff2f7d"
        successUrl={`${window.location.origin}/notes/result?status=success&broadcastId=${back}`}
        failUrl={`${window.location.origin}/notes/result?status=fail&broadcastId=${back}`}
        onClose={onClose}
      />
    );
  }

  return (
    <div className="toss-modal-backdrop" onClick={onClose}>
      <div className="note-modal" onClick={(e) => e.stopPropagation()}>
        {/* 떠다니는 음표 장식 */}
        <div className="note-modal-deco" aria-hidden="true">
          <span>♪</span><span>♫</span><span>♩</span><span>♬</span><span>♪</span>
        </div>

        <div className="note-modal-head">
          <div className="note-badge"><i className="fa-solid fa-music" /></div>
          <div>
            <h3>음표 보내기</h3>
            <p><b>{broadcasterName || '방송자'}</b> 님에게 음표로 마음을 전해요</p>
          </div>
          <button className="toss-modal-close" onClick={onClose} aria-label="닫기">
            <i className="fa-solid fa-xmark" />
          </button>
        </div>

        <div className="note-presets">
          {PRESETS.map((p) => (
            <button
              key={p}
              className={`note-preset ${custom === '' && amount === p ? 'active' : ''}`}
              onClick={() => { setAmount(p); setCustom(''); }}
            >
              <span className="note-preset-icon">♪</span>
              {p.toLocaleString('ko-KR')}
            </button>
          ))}
        </div>

        <label className="note-field">
          <span>직접 입력</span>
          <div className="note-input-wrap">
            <span className="note-input-icon">♪</span>
            <input
              type="number"
              min={MIN}
              max={MAX}
              step={100}
              value={custom}
              placeholder={`${MIN.toLocaleString('ko-KR')} ~ ${MAX.toLocaleString('ko-KR')}`}
              onChange={(e) => setCustom(e.target.value)}
            />
            <span className="note-input-unit">개</span>
          </div>
        </label>

        <label className="note-field">
          <span>메시지 (선택)</span>
          <input
            className="note-message"
            maxLength={100}
            value={message}
            placeholder="방송자에게 남길 한마디"
            onChange={(e) => setMessage(e.target.value)}
          />
        </label>

        {error && <div className="note-error">{error}</div>}

        <button className="note-send" disabled={!valid || busy} onClick={submit}>
          {busy ? '준비 중…' : valid
            ? <>♪ 음표 {value.toLocaleString('ko-KR')}개 보내기 <small>({value.toLocaleString('ko-KR')}원)</small></>
            : `${MIN.toLocaleString('ko-KR')}~${MAX.toLocaleString('ko-KR')}개 사이로 입력해 주세요`}
        </button>
        <p className="note-hint">음표 1개 = 1원 · 보낸 음표는 채팅창과 방송 화면에 표시돼요</p>
      </div>
    </div>
  );
}
