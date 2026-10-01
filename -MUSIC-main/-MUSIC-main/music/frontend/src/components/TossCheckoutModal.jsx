import React, { useEffect, useMemo, useRef, useState } from 'react';
import api from '../api/axiosInstance';
import { useAuth } from '../context/AuthContext';

const FALLBACK_CLIENT_KEY = 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm';

/**
 * 토스 결제위젯 모달 (음표·스토어 공용).
 * 서버가 미리 만든 주문(orderId, amount)을 받아 결제창을 띄운다 — 금액은 서버 주문과 같아야 승인된다.
 *
 * @param {{orderId:string, amount:number, orderName:string, successUrl:string, failUrl:string,
 *          title?:string, accent?:string, onClose:()=>void}} props
 */
export default function TossCheckoutModal({ orderId, amount, orderName, successUrl, failUrl, title, accent = '#3182f6', onClose }) {
  const { user } = useAuth() || {};
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  const [paying, setPaying] = useState(false);
  const widgetRef = useRef(null);

  const customerKey = useMemo(() => {
    let k = null;
    try { k = localStorage.getItem('tossCustomerKey'); } catch (_) {}
    if (!k) {
      k = 'cust_' + Math.random().toString(36).slice(2) + Date.now().toString(36);
      try { localStorage.setItem('tossCustomerKey', k); } catch (_) {}
    }
    return k;
  }, []);

  useEffect(() => {
    // StrictMode 에서는 effect 가 두 번 실행되므로, 취소된 실행은 그리지 않고
    // 그릴 때마다 컨테이너를 비워 위젯이 겹치지 않게 한다.
    let cancelled = false;

    (async () => {
      let clientKey = FALLBACK_CLIENT_KEY;
      try {
        const r = await api.get('/api/v1/payments/config');
        if (r.data?.clientKey) clientKey = r.data.clientKey;
      } catch (_) {}

      let tries = 0;
      const boot = () => {
        if (cancelled) return;
        if (!window.PaymentWidget) {
          if (tries++ > 40) { setError('결제 모듈을 불러오지 못했습니다. 새로고침해 주세요.'); return; }
          setTimeout(boot, 150);
          return;
        }
        try {
          ['toss-modal-method', 'toss-modal-agreement'].forEach((id) => {
            const el = document.getElementById(id);
            if (el) el.innerHTML = '';
          });
          const pw = window.PaymentWidget(clientKey, customerKey);
          widgetRef.current = pw;
          const methods = pw.renderPaymentMethods('#toss-modal-method', { value: amount }, { variantKey: 'DEFAULT' });
          pw.renderAgreement('#toss-modal-agreement', { variantKey: 'AGREEMENT' });
          methods.on('ready', () => !cancelled && setReady(true));
        } catch (e) {
          console.error(e);
          setError('결제 UI를 초기화할 수 없습니다.');
        }
      };
      boot();
    })();

    return () => { cancelled = true; };
  }, [amount, customerKey]);

  const pay = async () => {
    if (!widgetRef.current || !ready || paying) return;
    setPaying(true);
    try {
      await widgetRef.current.requestPayment({
        orderId,
        orderName,
        successUrl,
        failUrl,
        customerName: user?.nickname || '고객',
        customerEmail: user?.email || undefined,
      });
    } catch (err) {
      if (err?.code !== 'USER_CANCEL') alert(err?.message || '결제를 시작할 수 없습니다.');
      setPaying(false);
    }
  };

  return (
    <div className="toss-modal-backdrop" onClick={onClose}>
      <div className="toss-modal" onClick={(e) => e.stopPropagation()}>
        <div className="toss-modal-head">
          <strong>{title || orderName}</strong>
          <button className="toss-modal-close" onClick={onClose} aria-label="닫기">
            <i className="fa-solid fa-xmark" />
          </button>
        </div>
        <div className="toss-modal-amount">
          <span>{orderName}</span>
          <b>{Number(amount).toLocaleString('ko-KR')}원</b>
        </div>
        <div id="toss-modal-method" />
        <div id="toss-modal-agreement" />
        {error && <p className="toss-modal-error">{error}</p>}
        <button
          className="toss-modal-pay"
          style={{ background: paying || !ready ? '#c5ccd6' : accent }}
          disabled={paying || !ready}
          onClick={pay}
        >
          {paying ? '결제 진행 중…' : ready ? `${Number(amount).toLocaleString('ko-KR')}원 결제하기` : '결제 수단 불러오는 중…'}
        </button>
        <p className="toss-modal-note">토스페이먼츠 테스트 결제입니다. 실제 금액은 청구되지 않습니다.</p>
      </div>
    </div>
  );
}
