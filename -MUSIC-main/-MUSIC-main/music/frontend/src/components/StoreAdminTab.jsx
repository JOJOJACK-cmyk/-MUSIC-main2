import React, { useEffect, useState } from 'react';
import api from '../api/axiosInstance';
import { ORDER_STATUS } from './MyOrdersTab';

const won = (n) => `${Number(n || 0).toLocaleString('ko-KR')}원`;

const EMPTY_FORM = {
  name: '', description: '', price: '', stock: '', category: 'ALBUM', artist: '', musicId: '', musicTitle: '', imageUrl: '',
};

/** 관리자가 다음으로 바꿀 수 있는 상태 (서버 ShopOrder.TRANSITIONS 와 동일) */
const NEXT_ACTIONS = {
  PAID: [{ to: 'SHIPPING', label: '배송 시작' }, { to: 'CANCELLED', label: '취소·환불', danger: true }],
  SHIPPING: [{ to: 'DELIVERED', label: '배송 완료' }, { to: 'CANCELLED', label: '취소·환불', danger: true }],
};

/** 프로필 → 스토어 관리 (관리자/부 관리자) */
export default function StoreAdminTab() {
  const [view, setView] = useState('products'); // products | orders
  return (
    <div>
      <h3 className="pp-title">스토어 관리</h3>
      <p className="pp-desc">음반·굿즈를 등록하고 주문 배송 상태를 관리합니다. 곡이나 아티스트를 연결하면 그 곡을 들을 때 관련 상품으로 노출돼요.</p>
      <div className="lib-tabs">
        <button className={`lib-tab ${view === 'products' ? 'active' : ''}`} onClick={() => setView('products')}>상품</button>
        <button className={`lib-tab ${view === 'orders' ? 'active' : ''}`} onClick={() => setView('orders')}>주문</button>
      </div>
      {view === 'products' ? <ProductsAdmin /> : <OrdersAdmin />}
    </div>
  );
}

function ProductsAdmin() {
  const [products, setProducts] = useState(null);
  const [form, setForm] = useState(null); // null = 목록, {...} = 등록/수정 폼
  const [editingId, setEditingId] = useState(null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [musicQuery, setMusicQuery] = useState('');
  const [musicResults, setMusicResults] = useState([]);

  const load = () =>
    api.get('/api/shop/admin/products')
      .then((r) => setProducts(Array.isArray(r.data) ? r.data : []))
      .catch(() => setProducts([]));
  useEffect(() => { load(); }, []);

  const openNew = () => { setForm(EMPTY_FORM); setEditingId(null); setError(''); setMusicResults([]); };
  const openEdit = (p) => {
    setForm({
      name: p.name || '', description: p.description || '', price: String(p.price), stock: String(p.stock),
      category: p.category, artist: p.artist || '', musicId: p.musicId ? String(p.musicId) : '',
      musicTitle: p.musicTitle || '', imageUrl: p.imageUrl || '',
    });
    setEditingId(p.id);
    setError('');
    setMusicResults([]);
  };
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  const searchMusic = async () => {
    const q = musicQuery.trim();
    if (!q) return;
    try {
      const r = await api.get('/api/musics/search', { params: { keyword: q } });
      setMusicResults((Array.isArray(r.data) ? r.data : []).slice(0, 6));
    } catch (_) { setMusicResults([]); }
  };

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    setError('');
    const body = {
      name: form.name, description: form.description, price: form.price, stock: form.stock,
      category: form.category, artist: form.artist, musicId: form.musicId || null, imageUrl: form.imageUrl,
    };
    try {
      if (editingId) await api.put(`/api/shop/admin/products/${editingId}`, body);
      else await api.post('/api/shop/admin/products', body);
      setForm(null);
      load();
    } catch (err) {
      setError(err?.response?.data?.message || '저장하지 못했습니다.');
    }
    setSaving(false);
  };

  const toggleActive = async (p) => {
    try {
      await api.patch(`/api/shop/admin/products/${p.id}/active`, { active: !p.active });
      load();
    } catch (err) {
      alert(err?.response?.data?.message || '변경하지 못했습니다.');
    }
  };

  if (form) {
    return (
      <form className="pp-card admin-form" onSubmit={save}>
        <h4>{editingId ? '상품 수정' : '상품 등록'}</h4>
        <label>상품명<input value={form.name} onChange={set('name')} required maxLength={100} /></label>
        <div className="admin-row">
          <label>분류
            <select value={form.category} onChange={set('category')}>
              <option value="ALBUM">음반</option>
              <option value="MERCH">굿즈</option>
            </select>
          </label>
          <label>가격(원)<input type="number" min={100} value={form.price} onChange={set('price')} required /></label>
          <label>재고<input type="number" min={0} value={form.stock} onChange={set('stock')} required /></label>
        </div>
        <label>아티스트 (콤마로 별칭 여러 개: 르세라핌, LE SSERAFIM)
          <input value={form.artist} onChange={set('artist')} maxLength={100} />
        </label>
        <label>이미지 주소 (https://…)<input value={form.imageUrl} onChange={set('imageUrl')} maxLength={500} /></label>
        <label>설명<textarea rows={3} value={form.description} onChange={set('description')} maxLength={1000} /></label>

        <div className="admin-music">
          <span>연결할 곡 (선택)</span>
          {form.musicId ? (
            <div className="admin-music-linked">
              <i className="fa-solid fa-music" /> {form.musicTitle || `곡 #${form.musicId}`}
              <button type="button" onClick={() => setForm((f) => ({ ...f, musicId: '', musicTitle: '' }))}>연결 해제</button>
            </div>
          ) : (
            <>
              <div className="admin-row">
                <input
                  placeholder="곡 검색"
                  value={musicQuery}
                  onChange={(e) => setMusicQuery(e.target.value)}
                  onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); searchMusic(); } }}
                />
                <button type="button" className="pp-ghost" onClick={searchMusic}>검색</button>
              </div>
              {musicResults.map((m) => (
                <button
                  type="button"
                  key={m.id}
                  className="admin-music-result"
                  onClick={() => { setForm((f) => ({ ...f, musicId: String(m.id), musicTitle: m.title })); setMusicResults([]); }}
                >
                  {m.title} <small>{m.artist}</small>
                </button>
              ))}
            </>
          )}
        </div>

        {error && <div className="ship-error">{error}</div>}
        <div className="admin-actions">
          <button type="button" className="pp-ghost" onClick={() => setForm(null)}>취소</button>
          <button type="submit" className="pp-primary" disabled={saving}>{saving ? '저장 중…' : '저장'}</button>
        </div>
      </form>
    );
  }

  return (
    <>
      <button className="pp-primary" onClick={openNew} style={{ marginBottom: 12 }}>
        <i className="fa-solid fa-plus" /> 상품 등록
      </button>
      {products === null ? (
        <div className="pp-card pp-muted">불러오는 중…</div>
      ) : products.length === 0 ? (
        <div className="pp-card pp-muted">등록된 상품이 없습니다.</div>
      ) : (
        products.map((p) => (
          <div key={p.id} className={`pp-card admin-product ${p.active ? '' : 'inactive'}`}>
            <div className="admin-product-img">
              {p.imageUrl ? <img src={p.imageUrl} alt="" /> : <i className="fa-solid fa-compact-disc" />}
            </div>
            <div className="admin-product-info">
              <strong>{p.name}</strong>
              <small>
                {p.category === 'MERCH' ? '굿즈' : '음반'} · {won(p.price)} · 재고 {p.stock}
                {p.artist ? ` · ${p.artist}` : ''}{p.musicTitle ? ` · ♪ ${p.musicTitle}` : ''}
              </small>
            </div>
            <div className="admin-product-actions">
              <button className="pp-ghost" onClick={() => openEdit(p)}>수정</button>
              <button className={p.active ? 'pp-danger' : 'pp-ghost'} onClick={() => toggleActive(p)}>
                {p.active ? '판매 중지' : '판매 재개'}
              </button>
            </div>
          </div>
        ))
      )}
    </>
  );
}

function OrdersAdmin() {
  const [status, setStatus] = useState('');
  const [orders, setOrders] = useState(null);
  const [busy, setBusy] = useState(null);

  const load = () => {
    setOrders(null);
    api.get('/api/shop/admin/orders', { params: status ? { status } : {} })
      .then((r) => setOrders(Array.isArray(r.data) ? r.data : []))
      .catch(() => setOrders([]));
  };
  useEffect(load, [status]); // eslint-disable-line react-hooks/exhaustive-deps

  const change = async (o, to, label) => {
    if (to === 'CANCELLED' && !window.confirm(`'${o.productName}' 주문을 취소하고 ${won(o.totalAmount)}을 환불할까요?`)) return;
    if (to !== 'CANCELLED' && !window.confirm(`주문 상태를 '${label}'(으)로 바꿀까요?`)) return;
    setBusy(o.orderId);
    try {
      await api.patch(`/api/shop/admin/orders/${o.orderId}/status`, { status: to });
      load();
    } catch (err) {
      alert(err?.response?.data?.message || '상태를 바꾸지 못했습니다.');
    }
    setBusy(null);
  };

  return (
    <>
      <div className="admin-row" style={{ marginBottom: 12 }}>
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">전체</option>
          {Object.entries(ORDER_STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
        </select>
      </div>
      {orders === null ? (
        <div className="pp-card pp-muted">불러오는 중…</div>
      ) : orders.length === 0 ? (
        <div className="pp-card pp-muted">주문이 없습니다.</div>
      ) : (
        orders.map((o) => {
          const st = ORDER_STATUS[o.status] || { label: o.status, cls: '' };
          return (
            <div key={o.orderId} className="pp-card pp-order">
              <div className="pp-order-head">
                <strong>{o.productName} × {o.quantity}</strong>
                <span className={`order-badge ${st.cls}`}>{st.label}</span>
              </div>
              <div className="pp-order-meta"><b>{won(o.totalAmount)}</b> · {o.buyerEmail}</div>
              <div className="pp-order-meta">
                {o.recipientName} · {o.phone} · ({o.zipcode}) {o.address} {o.addressDetail || ''}
                {o.memo ? ` · 메모: ${o.memo}` : ''}
              </div>
              <div className="pp-order-foot">
                <span>{o.paidAt ? new Date(o.paidAt).toLocaleString('ko-KR') : ''} · {o.orderId}</span>
                <span className="admin-order-actions">
                  {(NEXT_ACTIONS[o.status] || []).map((a) => (
                    <button
                      key={a.to}
                      className={a.danger ? 'pp-danger' : 'pp-ghost'}
                      disabled={busy === o.orderId}
                      onClick={() => change(o, a.to, a.label)}
                    >
                      {a.label}
                    </button>
                  ))}
                </span>
              </div>
            </div>
          );
        })
      )}
    </>
  );
}
