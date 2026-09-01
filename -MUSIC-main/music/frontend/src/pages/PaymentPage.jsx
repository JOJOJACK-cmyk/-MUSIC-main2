import React, { useState } from 'react';
import Header from '../components/Header'; // 👈 1. 헤더 컴포넌트 임포트

export default function PaymentPage() {
  const [searchTerm, setSearchTerm] = useState(''); // 👈 2. 검색어 상태 추가
  const [selectedPlan, setSelectedPlan] = useState('monthly');

  const plans = [
    {
      id: 'monthly',
      name: '무제한 스트리밍 정기 이용권',
      price: '₩11,000',
      period: '월 / 부가세 포함',
      features: ['음원 무제한 듣기', '고음질 FLAC 사운드', '오프라인 재생 지원'],
    },
    {
      id: 'annual',
      name: '연간 이용권 (20% 할인)',
      price: '₩105,600',
      period: '연 / 부가세 포함',
      features: ['무제한 스트리밍', '고음질 FLAC 사운드', '2개월 무료 혜택 효과'],
    },
    {
      id: 'ticket30',
      name: '30일 30곡 제한 이용권',
      price: '₩5,500',
      period: '30일 / 부가세 포함',
      features: ['한 달 동안 지정 곡 수만큼 감상', '가성비 실속형 요금제'],
    },
  ];

  const handlePayment = () => {
    alert(`${plans.find(p => p.id === selectedPlan).name} 결제를 진행합니다.`);
  };

  return (
    <div className="payment-page">
      {/* 👈 3. 다른 페이지들처럼 상단 헤더 장착 */}
      <Header searchTerm={searchTerm} setSearchTerm={setSearchTerm} />

      <div className="content-section">
        <h2>프리미엄 이용권 결제</h2>
        <p className="payment-desc">원하시는 이용권을 선택하고 음악을 자유롭게 즐겨보세요.</p>

        <div className="plan-list">
          {plans.map((plan) => (
            <div
              key={plan.id}
              className={`plan-card ${selectedPlan === plan.id ? 'selected' : ''}`}
              onClick={() => setSelectedPlan(plan.id)}
            >
              <div className="plan-header">
                <h3>{plan.name}</h3>
                <span className="plan-price">{plan.price}</span>
              </div>
              <span className="plan-period">{plan.period}</span>
              <ul className="plan-features">
                {plan.features.map((feature, index) => (
                  <li key={index}><i className="fa-solid fa-check"></i> {feature}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <button className="payment-action-btn" onClick={handlePayment}>
          결제하기
        </button>
      </div>
    </div>
  );
}