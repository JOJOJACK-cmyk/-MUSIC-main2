import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../api/axiosInstance';
import { won } from './ShopPage';

/** 스토어 결제 결과 — 성공이면 서버 승인(재고 차감) 후 주문 완료 표시 */
export default function ShopResultPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [state, setState] = useState('confirming'); // confirming | done | error
  const [message, setMessage] = useState('결제를 확인하는 중입니다…');
  const [result, setResult] = useState(null);
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;

    if (params.get('status') !== 'success') {
      setState('error');
      setMessage(params.get('message') || '결제가 취소되었거나 실패했습니다.');
      return;
    }
    const paymentKey = params.get('paymentKey');
    const orderId = params.get('orderId');
    const amount = params.get('amount');
    if (!paymentKey || !orderId || !amount) {
      setState('error');
      setMessage('결제 정보가 올바르지 않습니다.');
      return;
    }
    (async () => {
      try {
        const r = await api.post('/api/shop/orders/confirm', { paymentKey, orderId, amount: Number(amount) });
        setResult(r.data);
        setState('done');
        setMessage('주문이 완료되었습니다. 배송이 시작되면 주문 내역에서 확인할 수 있어요.');
      } catch (e) {
        setState('error');
        setMessage(e?.response?.data?.message || '결제 승인에 실패했습니다.');
      }
    })();
  }, [params]);

  const productId = params.get('productId');

  return (
    <div className="note-result">
      <div className={`note-result-card shop ${state}`}>
        <div className="note-result-icon">
          {state === 'confirming' ? <i className="fa-solid fa-spinner fa-spin" /> : state === 'done'
            ? <i className="fa-solid fa-bag-shopping" /> : '!'}
        </div>
        <h2>{state === 'confirming' ? '결제 확인 중' : state === 'done' ? '주문 완료' : '결제 실패'}</h2>
        {result && (
          <p className="shop-result-line">
            {result.productName} · <b>{won(result.totalAmount)}</b>
          </p>
        )}
        <p>{message}</p>
        {state === 'done' && (
          <button onClick={() => navigate('/shop', { replace: true })}>스토어로 돌아가기</button>
        )}
        {state === 'error' && (
          <button onClick={() => navigate(productId ? `/shop/${productId}` : '/shop', { replace: true })}>
            상품으로 돌아가기
          </button>
        )}
      </div>
    </div>
  );
}
