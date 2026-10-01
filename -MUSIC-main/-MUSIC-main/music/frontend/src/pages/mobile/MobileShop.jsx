import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axiosInstance';

const CATS = [{ key: '', label: '전체' }, { key: 'ALBUM', label: '음반' }, { key: 'MERCH', label: '굿즈' }];
const won = (n) => `${Number(n || 0).toLocaleString('ko-KR')}원`;

/** 모바일 스토어: 분류 칩 + 2열 상품 그리드 */
export default function MobileShop() {
  const navigate = useNavigate();
  const [cat, setCat] = useState('');
  const [items, setItems] = useState(null);

  useEffect(() => {
    setItems(null);
    api.get('/api/shop/products', { params: cat ? { category: cat } : {} })
      .then((r) => setItems(Array.isArray(r.data) ? r.data : []))
      .catch(() => setItems([]));
  }, [cat]);

  return (
    <div className="m-page">
      <div className="m-chips">
        {CATS.map((c) => (
          <button key={c.key} className={`m-chip ${cat === c.key ? 'active' : ''}`} onClick={() => setCat(c.key)}>{c.label}</button>
        ))}
      </div>
      {items === null ? (
        <div className="m-empty"><i className="fa-solid fa-compact-disc fa-spin" />불러오는 중…</div>
      ) : items.length === 0 ? (
        <div className="m-empty"><i className="fa-solid fa-bag-shopping" />판매 중인 상품이 없어요</div>
      ) : (
        <div className="m-grid">
          {items.map((p) => (
            <button key={p.id} className={`m-product ${p.stock <= 0 ? 'soldout' : ''}`} onClick={() => navigate(`/shop/${p.id}`)}>
              <div className="m-product-img">
                {p.imageUrl ? <img src={p.imageUrl} alt="" loading="lazy" /> : <i className={`fa-solid ${p.category === 'MERCH' ? 'fa-shirt' : 'fa-compact-disc'}`} />}
                <span className="m-product-tag">{p.category === 'MERCH' ? '굿즈' : '음반'}</span>
                {p.stock <= 0 && <span className="m-product-soldout">품절</span>}
              </div>
              <strong>{p.name}</strong>
              {p.artist && <small>{p.artist.split(',')[0]}</small>}
              <b>{won(p.price)}</b>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
