import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Header from '../components/Header';
import TossCheckoutModal from '../components/TossCheckoutModal';
import api from '../api/axiosInstance';
import { useAuth } from '../context/AuthContext';
import { won } from './ShopPage';

const MAX_QTY = 10;
const SHIPPING_KEY = 'shop:lastShipping'; // 다음 주문 때 배송지 자동 채움

const loadShipping = () => {
  try { return JSON.parse(localStorage.getItem(SHIPPING_KEY) || '{}'); } catch { return {}; }
};

/** 상품 상세 + 바로 구매 (배송지 입력 → 토스 결제) */
export default function ProductPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth() || {};
  const [product, setProduct] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [qty, setQty] = useState(1);
  const [ship, setShip] = useState(() => ({
    recipientName: '', phone: '', zipcode: '', address: '', addressDetail: '', memo: '', ...loadShipping(),
  }));
  const [ordering, setOrdering] = useState(false);
  const [showForm, setShowForm] = useState(false);
  const [error, setError] = useState('');
  const [order, setOrder] = useState(null); // 서버가 만든 주문 → 결제창

  useEffect(() => {
    api.get(`/api/shop/products/${id}`)
      .then((r) => setProduct(r.data))
      .catch((e) => setLoadError(e?.response?.data?.message || '상품을 불러오지 못했습니다.'));
  }, [id]);

  const setField = (k) => (e) => setShip((s) => ({ ...s, [k]: e.target.value }));

  const startBuy = () => {
    if (!user) { navigate('/login'); return; }
    setShowForm(true);
  };

  const submitOrder = async (e) => {
    e.preventDefault();
    if (ordering) return;
    setOrdering(true);
    setError('');
    try {
      const r = await api.post('/api/shop/orders', { productId: product.id, quantity: qty, ...ship });
      try {
        // 메모는 주문마다 다를 수 있어 저장하지 않는다
        const { memo, ...rest } = ship; // eslint-disable-line no-unused-vars
        localStorage.setItem(SHIPPING_KEY, JSON.stringify(rest));
      } catch (_) {}
      setOrder(r.data);
    } catch (err) {
      setError(err?.response?.data?.message || '주문을 만들지 못했습니다.');
    }
    setOrdering(false);
  };

  if (loadError) {
    return (
      <>
        <Header searchTerm="" setSearchTerm={() => {}} />
        <div className="content-section">
          <div className="lib-state"><i className="fa-solid fa-triangle-exclamation" />{loadError}</div>
        </div>
      </>
    );
  }
  if (!product) {
    return (
      <>
        <Header searchTerm="" setSearchTerm={() => {}} />
        <div className="content-section">
          <div className="lib-state"><i className="fa-solid fa-compact-disc fa-spin" />불러오는 중…</div>
        </div>
      </>
    );
  }

  const soldOut = product.stock <= 0;
  const maxQty = Math.max(1, Math.min(MAX_QTY, product.stock));
  const total = product.price * qty;

  return (
    <>
      <Header searchTerm="" setSearchTerm={() => {}} />
      <div className="content-section">
        <button className="pl-back" onClick={() => navigate('/shop')}>
          <i className="fa-solid fa-chevron-left" /> 스토어
        </button>

        <div className="product-detail">
          <div className="product-img">
            {product.imageUrl ? (
              <img src={product.imageUrl} alt={product.name} />
            ) : (
              <i className={`fa-solid ${product.category === 'MERCH' ? 'fa-shirt' : 'fa-compact-disc'}`} />
            )}
          </div>

          <div className="product-info">
            <span className="shop-card-tag static">{product.category === 'MERCH' ? '굿즈' : '음반'}</span>
            <h2>{product.name}</h2>
            {product.artist && <div className="product-artist">{product.artist.split(',')[0]}</div>}
            {product.musicTitle && (
              <div className="product-music"><i className="fa-solid fa-music" /> {product.musicTitle}</div>
            )}
            <div className="product-price">{won(product.price)}</div>
            {product.description && <p className="product-desc">{product.description}</p>}

            <div className="product-stock">
              {soldOut ? '품절' : product.stock <= 5 ? `남은 수량 ${product.stock}개` : '구매 가능'}
            </div>

            {!soldOut && (
              <div className="product-buy">
                <div className="qty">
                  <button onClick={() => setQty((q) => Math.max(1, q - 1))} disabled={qty <= 1}>−</button>
                  <span>{qty}</span>
                  <button onClick={() => setQty((q) => Math.min(maxQty, q + 1))} disabled={qty >= maxQty}>+</button>
                </div>
                <div className="product-total">합계 <b>{won(total)}</b></div>
              </div>
            )}

            {!showForm && (
              <button className="product-buy-btn" disabled={soldOut} onClick={startBuy}>
                {soldOut ? '품절' : user ? '구매하기' : '로그인 후 구매하기'}
              </button>
            )}

            {showForm && (
              <form className="ship-form" onSubmit={submitOrder}>
                <h4>배송지</h4>
                <div className="ship-row">
                  <input placeholder="받는 분" value={ship.recipientName} onChange={setField('recipientName')} required maxLength={30} />
                  <input placeholder="연락처 (010-1234-5678)" value={ship.phone} onChange={setField('phone')} required maxLength={20} />
                </div>
                <div className="ship-row">
                  <input className="zip" placeholder="우편번호" value={ship.zipcode} onChange={setField('zipcode')} required maxLength={5} inputMode="numeric" />
                  <input placeholder="주소" value={ship.address} onChange={setField('address')} required maxLength={200} />
                </div>
                <input placeholder="상세 주소 (선택)" value={ship.addressDetail} onChange={setField('addressDetail')} maxLength={100} />
                <input placeholder="배송 메모 (선택)" value={ship.memo} onChange={setField('memo')} maxLength={100} />
                {error && <div className="ship-error">{error}</div>}
                <button className="product-buy-btn" type="submit" disabled={ordering}>
                  {ordering ? '주문 준비 중…' : `${won(total)} 결제하기`}
                </button>
              </form>
            )}
          </div>
        </div>
      </div>

      {order && (
        <TossCheckoutModal
          orderId={order.orderId}
          amount={order.amount}
          orderName={order.orderName}
          title="스토어 결제"
          successUrl={`${window.location.origin}/shop/result?status=success`}
          failUrl={`${window.location.origin}/shop/result?status=fail&productId=${product.id}`}
          onClose={() => setOrder(null)}
        />
      )}
    </>
  );
}
