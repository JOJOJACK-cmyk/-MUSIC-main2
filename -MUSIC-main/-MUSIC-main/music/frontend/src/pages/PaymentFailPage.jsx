import React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

// 토스 결제창 실패/취소 리다이렉트
export default function PaymentFailPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();

  const code = params.get('code');
  const message = params.get('message') || '결제가 취소되었거나 실패했습니다.';

  return (
    <div style={{ minHeight: '70vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 24 }}>
      <div
        style={{
          width: 420, maxWidth: '92vw', background: '#161719', border: '1px solid #2a2e35',
          borderRadius: 16, padding: '32px 26px', textAlign: 'center', color: '#e9edf1',
        }}
      >
        <div style={{ fontSize: 40, marginBottom: 14 }}>⚠️</div>
        <h2 style={{ margin: '0 0 10px', fontSize: 18 }}>결제 실패</h2>
        <p style={{ margin: '0 0 6px', fontSize: 14, color: '#a7adb8', lineHeight: 1.6 }}>{message}</p>
        {code && <p style={{ margin: '0 0 22px', fontSize: 12, color: '#6b7280' }}>오류 코드: {code}</p>}

        <button
          onClick={() => navigate('/payment')}
          style={{ background: '#00FFA3', color: '#04160f', border: 'none', borderRadius: 8, padding: '11px 18px', fontSize: 14, fontWeight: 800, cursor: 'pointer' }}
        >
          다시 시도하기
        </button>
      </div>
    </div>
  );
}
