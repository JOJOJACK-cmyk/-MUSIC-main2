import React, { useEffect, useState } from 'react';
import api from '../api/axiosInstance';

export const ORDER_STATUS = {
  PAID: { label: '결제 완료', cls: 'paid' },
  SHIPPING: { label: '배송 중', cls: 'shipping' },
  DELIVERED: { label: '배송 완료', cls: 'delivered' },
  CANCELLED: { label: '취소·환불', cls: 'cancelled' },
};

const won = (n) => `${Number(n || 0).toLocaleString('ko-KR')}원`;

/** 프로필 → 주문 내역 (스토어) */
export default function MyOrdersTab() {
  const [orders, setOrders] = useState(null);
  const [busy, setBusy] = useState(null);

  const load = () =>
    api.get('/api/shop/orders/mine')
      .then((r) => setOrders(Array.isArray(r.data) ? r.data : []))
      .catch(() => setOrders([]));

  useEffect(() => { load(); }, []);

  const cancel = async (o) => {
    if (!window.confirm(`'${o.productName}' 주문을 취소하고 환불받을까요?`)) return;
    setBusy(o.orderId);
    try {
      await api.post(`/api/shop/orders/${o.orderId}/cancel`);
      await load();
    } catch (e) {
      alert(e?.response?.data?.message || '주문을 취소하지 못했습니다.');
    }
    setBusy(null);
  };

  return (
    <div>
      <h3 className="pp-title">주문 내역</h3>
      <p className="pp-desc">스토어에서 주문한 음반·굿즈입니다. 배송이 시작되기 전까지 직접 취소할 수 있어요.</p>

      {orders === null ? (
        <div className="pp-card pp-muted">불러오는 중…</div>
      ) : orders.length === 0 ? (
        <div className="pp-card pp-muted">
          주문 내역이 없습니다. <a href="/shop" className="pp-link">스토어 둘러보기</a>
        </div>
      ) : (
        orders.map((o) => {
          const st = ORDER_STATUS[o.status] || { label: o.status, cls: '' };
          return (
            <div key={o.orderId} className="pp-card pp-order">
              <div className="pp-order-head">
                <strong>{o.productName}</strong>
                <span className={`order-badge ${st.cls}`}>{st.label}</span>
              </div>
              <div className="pp-order-meta">
                {won(o.unitPrice)} × {o.quantity} = <b>{won(o.totalAmount)}</b>
              </div>
              <div className="pp-order-meta">
                {o.recipientName} · {o.address} {o.addressDetail || ''}
              </div>
              <div className="pp-order-foot">
                <span>{o.paidAt ? new Date(o.paidAt).toLocaleString('ko-KR') : ''} · {o.orderId}</span>
                {o.status === 'PAID' && (
                  <button className="pp-danger" disabled={busy === o.orderId} onClick={() => cancel(o)}>
                    {busy === o.orderId ? '취소 중…' : '주문 취소'}
                  </button>
                )}
              </div>
            </div>
          );
        })
      )}
    </div>
  );
}
