import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const authHeaders = () => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
};

// 토스 결제창 성공 리다이렉트 → 서버에 승인 요청 → 이용권 발급 확인
export default function PaymentSuccessPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const { refreshSubscription } = useAuth();

  const [state, setState] = useState('confirming'); // confirming | done | error
  const [message, setMessage] = useState('결제를 확인하는 중입니다…');
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    const paymentKey = params.get('paymentKey');
    const orderId = params.get('orderId');
    const amount = params.get('amount');
    const planId = params.get('planId');

    if (!paymentKey || !orderId || !amount) {
      setState('error');
      setMessage('결제 정보가 올바르지 않습니다.');
      return;
    }

    (async () => {
      try {
        const res = await fetch('/api/v1/payments/confirm', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...authHeaders() },
          credentials: 'include',
          body: JSON.stringify({ paymentKey, orderId, amount: Number(amount), planId }),
        });
        const data = await res.json().catch(() => ({}));

        if (res.ok && data.status === 'SUCCESS') {
          await refreshSubscription();
          setState('done');
          setMessage('결제가 완료되었습니다. 이제 모든 곡을 제한 없이 감상할 수 있어요!');
        } else {
          setState('error');
          setMessage(data.message || '결제 승인에 실패했습니다.');
        }
      } catch (e) {
        setState('error');
        setMessage('결제 승인 중 오류가 발생했습니다.');
      }
    })();
  }, [params, refreshSubscription]);

  return (
    <div style={{ minHeight: '70vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
      <div
        style={{
          width: 420, maxWidth: '92vw', background: '#161719', border: '1px solid #2a2e35',
          borderRadius: 16, padding: '32px 26px', textAlign: 'center', color: '#e9edf1',
        }}
      >
        <div style={{ fontSize: 40, marginBottom: 14 }}>
          {state === 'confirming' ? '⏳' : state === 'done' ? '✅' : '⚠️'}
        </div>
        <h2 style={{ margin: '0 0 10px', fontSize: 18 }}>
          {state === 'confirming' ? '결제 확인 중' : state === 'done' ? '결제 완료' : '결제 실패'}
        </h2>
        <p style={{ margin: '0 0 22px', fontSize: 14, color: '#a7adb8', lineHeight: 1.6 }}>{message}</p>

        {state === 'done' && (
          <button
            onClick={() => navigate('/')}
            style={{ background: '#00FFA3', color: '#04160f', border: 'none', borderRadius: 8, padding: '11px 18px', fontSize: 14, fontWeight: 800, cursor: 'pointer' }}
          >
            메인으로
          </button>
        )}
        {state === 'error' && (
          <button
            onClick={() => navigate('/payment')}
            style={{ background: 'transparent', color: '#c7ccd4', border: '1px solid #363b44', borderRadius: 8, padding: '10px 18px', fontSize: 13, fontWeight: 600, cursor: 'pointer' }}
          >
            결제 페이지로 돌아가기
          </button>
        )}
      </div>
    </div>
  );
}
