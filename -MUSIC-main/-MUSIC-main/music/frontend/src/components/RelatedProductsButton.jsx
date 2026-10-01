import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axiosInstance';

const won = (n) => `${Number(n || 0).toLocaleString('ko-KR')}원`;

/**
 * 플레이어 바의 "관련 상품" 버튼. 지금 재생 중인 곡에 연결된 음반/굿즈가 있을 때만 보인다.
 * 누르면 위로 목록이 펼쳐지고, 상품을 고르면 상품 페이지로 이동한다.
 */
export default function RelatedProductsButton({ musicId }) {
  const navigate = useNavigate();
  const [products, setProducts] = useState([]);
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  useEffect(() => {
    setOpen(false);
    if (!musicId) { setProducts([]); return; }
    let alive = true;
    api.get('/api/shop/products/related', { params: { musicId } })
      .then((r) => alive && setProducts(Array.isArray(r.data) ? r.data : []))
      .catch(() => alive && setProducts([]));
    return () => { alive = false; };
  }, [musicId]);

  useEffect(() => {
    if (!open) return;
    const onDown = (e) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  if (products.length === 0) return null;

  return (
    <div className="related-wrap" ref={wrapRef}>
      <button
        type="button"
        className={`related-btn ${open ? 'open' : ''}`}
        title="이 곡의 관련 상품"
        onClick={(e) => { e.currentTarget.blur(); setOpen((o) => !o); }}
      >
        <i className="fa-solid fa-bag-shopping" />
        <span className="related-count">{products.length}</span>
      </button>
      {open && (
        <div className="related-menu">
          <div className="related-title">이 곡의 관련 상품</div>
          {products.map((p) => (
            <button
              key={p.id}
              className="related-item"
              onClick={() => { setOpen(false); navigate(`/shop/${p.id}`); }}
            >
              <div className="related-thumb">
                {p.imageUrl ? <img src={p.imageUrl} alt="" /> : (
                  <i className={`fa-solid ${p.category === 'MERCH' ? 'fa-shirt' : 'fa-compact-disc'}`} />
                )}
              </div>
              <div className="related-text">
                <strong>{p.name}</strong>
                <small>{p.stock > 0 ? won(p.price) : '품절'}</small>
              </div>
            </button>
          ))}
          <button className="related-all" onClick={() => { setOpen(false); navigate('/shop'); }}>
            스토어 전체 보기 <i className="fa-solid fa-chevron-right" />
          </button>
        </div>
      )}
    </div>
  );
}
