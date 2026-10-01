import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Header from '../components/Header';
import api from '../api/axiosInstance';

const CATEGORIES = [
  { key: '', label: '전체' },
  { key: 'ALBUM', label: '음반' },
  { key: 'MERCH', label: '굿즈' },
];

export const won = (n) => `${Number(n || 0).toLocaleString('ko-KR')}원`;

export function ProductCard({ product, onClick }) {
  const soldOut = product.stock <= 0;
  return (
    <button className={`shop-card ${soldOut ? 'sold-out' : ''}`} onClick={onClick}>
      <div className="shop-card-img">
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.name} />
        ) : (
          <i className={`fa-solid ${product.category === 'MERCH' ? 'fa-shirt' : 'fa-compact-disc'}`} />
        )}
        <span className="shop-card-tag">{product.category === 'MERCH' ? '굿즈' : '음반'}</span>
        {soldOut && <span className="shop-card-soldout">품절</span>}
      </div>
      <strong>{product.name}</strong>
      {product.artist && <small>{product.artist.split(',')[0]}</small>}
      <b>{won(product.price)}</b>
    </button>
  );
}

/** 스토어 — 음반 / 굿즈 목록 */
export default function ShopPage() {
  const navigate = useNavigate();
  const [category, setCategory] = useState('');
  const [products, setProducts] = useState(null);

  useEffect(() => {
    setProducts(null);
    api.get('/api/shop/products', { params: category ? { category } : {} })
      .then((r) => setProducts(Array.isArray(r.data) ? r.data : []))
      .catch(() => setProducts([]));
  }, [category]);

  return (
    <>
      <Header searchTerm="" setSearchTerm={() => {}} />
      <div className="content-section">
        <div className="library-head">
          <div>
            <h2>🛍️ 스토어</h2>
            <p>좋아하는 아티스트의 음반과 굿즈를 만나보세요.</p>
          </div>
        </div>

        <div className="lib-tabs">
          {CATEGORIES.map((c) => (
            <button
              key={c.key}
              className={`lib-tab ${category === c.key ? 'active' : ''}`}
              onClick={() => setCategory(c.key)}
            >
              {c.label}
            </button>
          ))}
        </div>

        {products === null ? (
          <div className="lib-state"><i className="fa-solid fa-compact-disc fa-spin" />불러오는 중…</div>
        ) : products.length === 0 ? (
          <div className="lib-state">
            <i className="fa-solid fa-bag-shopping" />
            아직 판매 중인 상품이 없습니다.
          </div>
        ) : (
          <div className="shop-grid">
            {products.map((p) => (
              <ProductCard key={p.id} product={p} onClick={() => navigate(`/shop/${p.id}`)} />
            ))}
          </div>
        )}
      </div>
    </>
  );
}
