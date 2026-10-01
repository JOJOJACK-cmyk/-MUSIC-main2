import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../api/axiosInstance';

/** 음표 결제 결과 — 성공이면 서버 승인 후 방송으로 돌아간다. */
export default function NoteResultPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const broadcastId = params.get('broadcastId');
  const [state, setState] = useState('confirming'); // confirming | done | error
  const [message, setMessage] = useState('음표를 보내는 중이에요…');
  const [amount, setAmount] = useState(null);
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    if (params.get('status') !== 'success') {
      setState('error');
      setMessage(params.get('message') || '결제가 취소되었거나 실패했어요.');
      return;
    }
    const paymentKey = params.get('paymentKey');
    const orderId = params.get('orderId');
    const amt = params.get('amount');
    if (!paymentKey || !orderId || !amt) {
      setState('error');
      setMessage('결제 정보가 올바르지 않아요.');
      return;
    }

    (async () => {
      try {
        const r = await api.post('/api/notes/confirm', { paymentKey, orderId, amount: Number(amt) });
        setAmount(r.data?.amount);
        setState('done');
        setMessage('방송자에게 음표가 전달됐어요!');
        setTimeout(() => navigate(`/live/${r.data?.broadcastId || broadcastId}`, { replace: true }), 2200);
      } catch (e) {
        setState('error');
        setMessage(e?.response?.data?.message || '음표 승인에 실패했어요.');
      }
    })();
  }, [params, navigate, broadcastId]);

  return (
    <div className="note-result">
      <div className={`note-result-card ${state}`}>
        <div className="note-result-icon">
          {state === 'confirming' ? <span className="note-bounce">♪</span> : state === 'done' ? '♫' : '!'}
        </div>
        <h2>
          {state === 'confirming' ? '음표 보내는 중' : state === 'done'
            ? `음표 ${Number(amount || 0).toLocaleString('ko-KR')}개 전달 완료` : '음표를 보내지 못했어요'}
        </h2>
        <p>{message}</p>
        {state !== 'confirming' && broadcastId && (
          <button onClick={() => navigate(`/live/${broadcastId}`, { replace: true })}>방송으로 돌아가기</button>
        )}
      </div>
    </div>
  );
}
