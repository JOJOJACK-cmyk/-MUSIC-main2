import React, { useEffect, useMemo, useRef, useState } from 'react';
import Header from '../components/Header';
import { useAuth } from '../context/AuthContext';

const authHeaders = () => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
};

// 백엔드 PricingPlan 과 planId 가 일치해야 한다 (monthly / annual / ticket30)
const PLANS = [
  {
    id: 'monthly',
    name: '무제한 스트리밍 정기 이용권',
    price: '₩11,000',
    amount: 11000,
    period: '월 / 부가세 포함',
    features: ['음원 무제한 듣기', '고음질 FLAC 사운드', '오프라인 재생 지원'],
  },
  {
    id: 'annual',
    name: '연간 이용권 (20% 할인)',
    price: '₩105,600',
    amount: 105600,
    period: '연 / 부가세 포함',
    features: ['무제한 스트리밍', '고음질 FLAC 사운드', '2개월 무료 혜택 효과'],
  },
  {
    id: 'ticket30',
    name: '30일 30곡 제한 이용권',
    price: '₩5,500',
    amount: 5500,
    period: '30일 / 부가세 포함',
    features: ['한 달 동안 지정 곡 수만큼 감상', '가성비 실속형 요금제'],
  },
];

export default function PaymentPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedPlan, setSelectedPlan] = useState('monthly');
  const [clientKey, setClientKey] = useState('');
  const [showCheckout, setShowCheckout] = useState(false);
  const [widgetReady, setWidgetReady] = useState(false);
  const [widgetError, setWidgetError] = useState('');
  const [paying, setPaying] = useState(false);

  const { user, isPremium, subscription } = useAuth();

  const widgetRef = useRef(null);
  const methodsRef = useRef(null);
  const initRef = useRef(false);
  const checkoutRef = useRef(null);

  const plan = PLANS.find((p) => p.id === selectedPlan) || PLANS[0];

  const customerKey = useMemo(() => {
    let k = localStorage.getItem('tossCustomerKey');
    if (!k) {
      k = 'cust_' + Math.random().toString(36).slice(2) + Date.now().toString(36);
      localStorage.setItem('tossCustomerKey', k);
    }
    return k;
  }, []);

  useEffect(() => {
    fetch('/api/v1/payments/config', {
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      credentials: 'include',
    })
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => setClientKey(d?.clientKey || 'test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm'))
      .catch(() => setClientKey('test_gck_docs_Ovk5rk1EwkEbP0W43n07xlzm'));
  }, []);

  // '결제하기'를 눌러 결제 영역이 나타나면 그때 위젯을 초기화한다.
  useEffect(() => {
    if (!showCheckout || !clientKey || initRef.current) return;

    let tries = 0;
    const boot = () => {
      if (initRef.current) return;
      if (!window.PaymentWidget) {
        if (tries++ > 40) { setWidgetError('결제 모듈을 불러오지 못했습니다. 새로고침해 주세요.'); return; }
        setTimeout(boot, 150);
        return;
      }
      initRef.current = true;
      try {
        const pw = window.PaymentWidget(clientKey, customerKey);
        widgetRef.current = pw;
        const methods = pw.renderPaymentMethods(
          '#payment-method',
          { value: plan.amount },
          { variantKey: 'DEFAULT' }
        );
        methodsRef.current = methods;
        pw.renderAgreement('#agreement', { variantKey: 'AGREEMENT' });
        methods.on('ready', () => setWidgetReady(true));
        checkoutRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      } catch (e) {
        console.error(e);
        setWidgetError('결제 UI를 초기화할 수 없습니다.');
      }
    };
    boot();
  }, [showCheckout, clientKey, customerKey]); // eslint-disable-line react-hooks/exhaustive-deps

  // 요금제 변경 시 결제 금액 갱신
  useEffect(() => {
    if (widgetReady && methodsRef.current) {
      try { methodsRef.current.updateAmount(plan.amount); } catch (_) {}
    }
  }, [selectedPlan, widgetReady, plan.amount]);

  const handleConfirm = async () => {
    if (!widgetRef.current || !widgetReady || paying) return;
    setPaying(true);
    try {
      const orderId = `order_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
      await widgetRef.current.requestPayment({
        orderId,
        orderName: plan.name,
        successUrl: `${window.location.origin}/payment/success?planId=${plan.id}`,
        failUrl: `${window.location.origin}/payment/fail`,
        customerName: user?.nickname || '고객',
        customerEmail: user?.email || undefined,
      });
    } catch (err) {
      if (err?.code !== 'USER_CANCEL') {
        console.error(err);
        alert(err?.message || '결제를 시작할 수 없습니다.');
      }
      setPaying(false);
    }
  };

  return (
    <>
      <Header searchTerm={searchTerm} setSearchTerm={setSearchTerm} />

      <div className="payment-page">
      <div className="content-section">
        <h2>프리미엄 이용권 결제</h2>
        <p className="payment-desc">원하시는 이용권을 선택하고 음악을 자유롭게 즐겨보세요.</p>

        {isPremium && (
          <div
            style={{
              background: '#12352a', border: '1px solid #1f6b4f', borderRadius: 10,
              padding: '12px 16px', margin: '12px 0 20px', fontSize: 13, color: '#8fe9c4',
            }}
          >
            현재 <b>{subscription?.passName || '프리미엄 이용권'}</b> 이용 중입니다.
            {subscription?.expireDate &&
              ` (${new Date(subscription.expireDate).toLocaleDateString('ko-KR')}까지)`}
          </div>
        )}

        <div className="plan-list">
          {PLANS.map((p) => (
            <div
              key={p.id}
              className={`plan-card ${selectedPlan === p.id ? 'selected' : ''}`}
              onClick={() => setSelectedPlan(p.id)}
            >
              <div className="plan-header">
                <h3>{p.name}</h3>
                <span className="plan-price">{p.price}</span>
              </div>
              <span className="plan-period">{p.period}</span>
              <ul className="plan-features">
                {p.features.map((feature, index) => (
                  <li key={index}><i className="fa-solid fa-check"></i> {feature}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        {!showCheckout && (
          <button className="payment-action-btn" onClick={() => setShowCheckout(true)}>
            {plan.price} 결제하기
          </button>
        )}

        {showCheckout && (
          <div
            ref={checkoutRef}
            style={{
              marginTop: 24, padding: '20px 18px', background: '#fff', color: '#191f28',
              borderRadius: 14, maxWidth: 560,
            }}
          >
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 12 }}>
              <strong style={{ fontSize: 15 }}>{plan.name}</strong>
              <span style={{ fontSize: 18, fontWeight: 800 }}>{plan.price}</span>
            </div>

            <div id="payment-method" />
            <div id="agreement" />

            {widgetError && <p style={{ color: '#e11d48', fontSize: 13 }}>{widgetError}</p>}

            <button
              onClick={handleConfirm}
              disabled={paying || !widgetReady}
              style={{
                width: '100%', marginTop: 14, padding: '13px 0', borderRadius: 10, border: 'none',
                fontSize: 15, fontWeight: 800, cursor: paying || !widgetReady ? 'default' : 'pointer',
                background: paying || !widgetReady ? '#c5ccd6' : '#3182f6', color: '#fff',
              }}
            >
              {paying ? '결제 진행 중…' : widgetReady ? `${plan.price} 결제하기` : '결제 수단 불러오는 중…'}
            </button>
          </div>
        )}

        <p style={{ fontSize: 12, color: 'var(--text-sub)', marginTop: 12 }}>
          토스페이먼츠 테스트 결제입니다. 실제 금액은 청구되지 않습니다.
        </p>
      </div>
      </div>
    </>
  );
}
