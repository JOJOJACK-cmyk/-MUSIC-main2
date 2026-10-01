import React, { useEffect, useMemo, useRef, useState } from 'react';
import Header from '../components/Header';
import { useAuth } from '../context/AuthContext';

const authHeaders = () => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
};

// 요금제는 서버(/api/v1/payments/plans)가 단일 소스 — 불러오지 못했을 때만 쓰는 같은 내용의 사본
const FALLBACK_PLANS = [
  { id: 'light', name: '라이트 30곡 이용권', amount: 4900, months: 1, songLimit: 30, storeDiscountPct: 0,
    features: ['LIMITED_PLAY'], highlights: ['한 달 동안 30곡 전곡 재생', '같은 곡은 다시 들어도 차감 없음'] },
  { id: 'standard', name: '스탠다드 이용권', amount: 7900, months: 1, songLimit: 0, storeDiscountPct: 0,
    features: ['UNLIMITED_PLAY', 'PLAYLIST'], highlights: ['모든 곡 전곡 무제한 재생', '내 플레이리스트 만들기'] },
  { id: 'premium', name: '프리미엄 이용권', amount: 10900, months: 1, songLimit: 0, storeDiscountPct: 10,
    features: ['UNLIMITED_PLAY', 'PLAYLIST', 'CHAT_BADGE', 'STORE_DISCOUNT'],
    highlights: ['모든 곡 전곡 무제한 재생', '내 플레이리스트 만들기', '라이브 채팅 프리미엄 ♪ 배지', '스토어 음반·굿즈 10% 할인'] },
  { id: 'premium_annual', name: '프리미엄 연간 이용권', amount: 109000, months: 12, songLimit: 0, storeDiscountPct: 10,
    features: ['UNLIMITED_PLAY', 'PLAYLIST', 'CHAT_BADGE', 'STORE_DISCOUNT'],
    highlights: ['프리미엄 혜택 12개월', '월 결제보다 2개월분 저렴 (약 17% 할인)'] },
];

const won = (n) => `₩${Number(n || 0).toLocaleString('ko-KR')}`;
const periodOf = (p) => (p.months >= 12 ? `${p.months}개월` : '1개월');
const fmtDate = (d) => (d ? new Date(d).toLocaleDateString('ko-KR') : '');
const sameFeatures = (a = [], b = []) => a.length === b.length && a.every((f) => b.includes(f));

// 이용권별 기능 비교표 (무료 포함)
const COMPARE_ROWS = [
  { label: '전곡 재생', free: '하루 1분 미리듣기', cell: (p) => (p.features.includes('UNLIMITED_PLAY') ? '무제한' : p.songLimit ? `${p.songLimit}곡` : '—') },
  { label: '플레이리스트 만들기', free: '—', cell: (p) => (p.features.includes('PLAYLIST') ? '✓' : '—') },
  { label: '라이브 채팅 프리미엄 ♪ 배지', free: '—', cell: (p) => (p.features.includes('CHAT_BADGE') ? '✓' : '—') },
  { label: '스토어 할인', free: '—', cell: (p) => (p.storeDiscountPct ? `${p.storeDiscountPct}%` : '—') },
  { label: '좋아요 · 라이브 시청 · 채팅 · 신청곡 투표 · 음표', free: '✓', cell: () => '✓' },
];

export default function PaymentPage() {
  const [searchTerm, setSearchTerm] = useState('');
  const [plans, setPlans] = useState(FALLBACK_PLANS);
  const [selectedPlan, setSelectedPlan] = useState('standard');
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

  const plan = plans.find((p) => p.id === selectedPlan) || plans[0];

  useEffect(() => {
    fetch('/api/v1/payments/plans')
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => { if (Array.isArray(d) && d.length) setPlans(d); })
      .catch(() => {});
  }, []);

  // 지금 이용 중 · 예약된 이용권과 비교해, 이 요금제를 사면 어떻게 되는지
  const ownedPasses = [...(subscription?.passes || []), ...(subscription?.upcoming || [])];
  const myFeatures = subscription?.features || [];
  const planNote = (p) => {
    if (subscription?.staff) return '관리자 계정은 이용권 없이 모든 기능을 쓸 수 있어요.';
    const same = ownedPasses
      .filter((x) => sameFeatures(plans.find((pl) => pl.id === x.planId)?.features, p.features))
      .sort((a, b) => new Date(b.expireDate) - new Date(a.expireDate))[0];
    if (same) return `같은 등급 이용권이 ${fmtDate(same.expireDate)}까지 있어요 — 결제하면 그 뒤로 ${periodOf(p)} 이어 붙어요.`;
    // 무제한 재생이 있으면 곡 수 제한 재생은 이미 포함된 셈
    const covered = (f) => myFeatures.includes(f) || (f === 'LIMITED_PLAY' && myFeatures.includes('UNLIMITED_PLAY'));
    if (p.features.every(covered)) return '지금 이용권에 이미 포함된 기능이에요. 결제하면 오늘부터 따로 시작돼요.';
    return isPremium ? '결제하면 오늘부터 바로 적용되고, 지금 이용권 기능과 합쳐져요.' : '결제하면 오늘부터 바로 적용돼요.';
  };

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
        <h2>이용권</h2>
        <p className="payment-desc">듣는 만큼 고르세요. 이용권마다 쓸 수 있는 기능이 달라요.</p>

        {subscription?.staff ? (
          <div className="pay-current">관리자 계정은 이용권 없이 모든 기능을 쓸 수 있어요.</div>
        ) : isPremium && (
          <div className="pay-current">
            현재 <b>{subscription?.passName}</b> 이용 중
            {subscription?.expireDate && ` · ${fmtDate(subscription.expireDate)}까지`}
            {subscription?.songLimit ? ` · ${subscription.songsUsed || 0}/${subscription.songLimit}곡 사용` : ''}
            {(subscription?.upcoming || []).length > 0 && (
              <span> · 예약: {subscription.upcoming.map((u) => `${u.passName} (${fmtDate(u.startDate)}부터)`).join(', ')}</span>
            )}
          </div>
        )}

        <div className="plan-list">
          {plans.map((p) => {
            const using = (subscription?.passes || []).some((x) => x.planId === p.id);
            return (
              <div
                key={p.id}
                className={`plan-card ${selectedPlan === p.id ? 'selected' : ''}`}
                onClick={() => setSelectedPlan(p.id)}
              >
                {using && <span className="plan-badge">이용 중</span>}
                {p.id === 'premium' && !using && <span className="plan-badge hot">추천</span>}
                <div className="plan-header">
                  <h3>{p.name}</h3>
                  <span className="plan-price">{won(p.amount)}</span>
                </div>
                <span className="plan-period">{periodOf(p)} / 부가세 포함</span>
                <ul className="plan-features">
                  {p.highlights.map((feature, index) => (
                    <li key={index}><i className="fa-solid fa-check"></i> {feature}</li>
                  ))}
                </ul>
              </div>
            );
          })}
        </div>

        <div className="plan-compare-wrap">
          <table className="plan-compare">
            <thead>
              <tr>
                <th>기능</th>
                <th>무료</th>
                {plans.filter((p) => p.months === 1).map((p) => (
                  <th key={p.id} className={selectedPlan === p.id ? 'on' : ''}>{p.name.replace(' 이용권', '')}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {COMPARE_ROWS.map((r) => (
                <tr key={r.label}>
                  <td>{r.label}</td>
                  <td>{r.free}</td>
                  {plans.filter((p) => p.months === 1).map((p) => (
                    <td key={p.id} className={selectedPlan === p.id ? 'on' : ''}>{r.cell(p)}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <p className="plan-note"><i className="fa-solid fa-circle-info" /> {planNote(plan)}</p>

        {!showCheckout && (
          <button className="payment-action-btn" onClick={() => setShowCheckout(true)}>
            {won(plan.amount)} 결제하기
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
              <span style={{ fontSize: 18, fontWeight: 800 }}>{won(plan.amount)}</span>
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
              {paying ? '결제 진행 중…' : widgetReady ? `${won(plan.amount)} 결제하기` : '결제 수단 불러오는 중…'}
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
