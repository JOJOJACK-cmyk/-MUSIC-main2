import React, { useEffect, useRef } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import useLiveChat from '../hooks/useLiveChat';

/**
 * OBS 브라우저 소스용 채팅 오버레이.
 *   URL:  /live/:broadcastId/chat
 *   옵션: ?theme=transparent|dark  (기본 transparent)
 *         ?size=sm|md|lg           (기본 md)
 * 읽기 전용(입력창 없음). 화면 크롬(사이드바/헤더/플레이어바) 없이 렌더된다.
 */
export default function ChatOverlay() {
  const { broadcastId } = useParams();
  const [sp] = useSearchParams();
  const theme = sp.get('theme') === 'dark' ? 'dark' : 'transparent';
  const size = { sm: 13, lg: 20, md: 16 }[sp.get('size')] || 16;

  // 읽기 전용 → sender 없이 구독만
  const { messages } = useLiveChat(broadcastId, { limit: 80 });

  const endRef = useRef(null);
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // OBS 브라우저 소스 배경 투명 처리
  useEffect(() => {
    const prevHtml = document.documentElement.style.background;
    const prevBody = document.body.style.background;
    if (theme === 'transparent') {
      document.documentElement.style.background = 'transparent';
      document.body.style.background = 'transparent';
    } else {
      document.documentElement.style.background = '#0b0b0d';
      document.body.style.background = '#0b0b0d';
    }
    return () => {
      document.documentElement.style.background = prevHtml;
      document.body.style.background = prevBody;
    };
  }, [theme]);

  const shadow =
    theme === 'transparent'
      ? '0 1px 2px rgba(0,0,0,0.9), 0 0 6px rgba(0,0,0,0.7)'
      : 'none';

  return (
    <div
      style={{
        position: 'fixed',
        inset: 0,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'flex-end',
        padding: '14px',
        gap: '8px',
        overflow: 'hidden',
        fontFamily:
          '"Pretendard", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif',
      }}
    >
      {messages
        .filter((m) => m.type !== 'ENTER' && m.type !== 'LEAVE')
        .map((chat, i) => {
          if (chat.type === 'DONATION') {
            // 음표 알림 — 방송 화면에서 눈에 띄게 (음표 모양 배지 + 통통 튀는 애니메이션)
            return (
              <div
                key={chat.messageId || `${chat.timestamp}-${i}`}
                style={{
                  alignSelf: 'flex-start',
                  maxWidth: '96%',
                  background: 'linear-gradient(135deg, rgba(255,47,125,0.92), rgba(124,92,255,0.92))',
                  borderRadius: '18px 18px 18px 4px',
                  padding: '10px 14px',
                  color: '#fff',
                  boxShadow: '0 6px 20px rgba(255,47,125,0.35)',
                  animation: 'noteIn 0.45s cubic-bezier(.2,1.4,.4,1)',
                }}
              >
                <div style={{ fontSize: size, fontWeight: 800, textShadow: shadow }}>
                  <span style={{ display: 'inline-block', marginRight: 6, animation: 'noteBob 1.2s ease-in-out infinite' }}>♪</span>
                  {chat.sender} 님 음표 {Number(chat.amount || 0).toLocaleString('ko-KR')}개
                  <span style={{ display: 'inline-block', marginLeft: 6, animation: 'noteBob 1.2s ease-in-out .3s infinite' }}>♫</span>
                </div>
                {chat.message && (
                  <div style={{ fontSize: size - 1, marginTop: 4, textShadow: shadow, wordBreak: 'break-word' }}>
                    {chat.message}
                  </div>
                )}
              </div>
            );
          }
          if ((chat.type === 'VOTE' || chat.type === 'NOTICE')) {
            return (
              <div
                key={`${chat.timestamp}-${i}`}
                style={{
                  alignSelf: 'flex-start',
                  fontSize: size - 3,
                  fontWeight: 800,
                  color: '#ff9ec4',
                  background: 'rgba(224,40,183,0.28)',
                  borderRadius: 999,
                  padding: '4px 12px',
                  textShadow: shadow,
                  animation: 'chatIn 0.18s ease',
                }}
              >
                📊 {chat.message}
              </div>
            );
          }
          return (
            <div
              key={`${chat.timestamp}-${i}`}
              style={{
                maxWidth: '92%',
                alignSelf: 'flex-start',
                background:
                  theme === 'transparent' ? 'rgba(0,0,0,0.45)' : 'rgba(255,255,255,0.06)',
                borderRadius: '10px',
                padding: '7px 11px',
                backdropFilter: theme === 'transparent' ? 'blur(2px)' : 'none',
                animation: 'chatIn 0.18s ease',
              }}
            >
              <span
                style={{
                  fontSize: size - 2,
                  fontWeight: 800,
                  color: '#ff7ab6',
                  marginRight: 7,
                  textShadow: shadow,
                }}
              >
                {chat.premium && <span style={{ color: '#ffd36e', marginRight: 4 }}>♪</span>}
                {chat.sender}
              </span>
              <span
                style={{
                  fontSize: size,
                  color: '#fff',
                  lineHeight: 1.45,
                  wordBreak: 'break-word',
                  textShadow: shadow,
                }}
              >
                {chat.message}
              </span>
            </div>
          );
        })}
      <div ref={endRef} />
      <style>{`
        @keyframes chatIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: none; } }
        @keyframes noteIn { from { opacity: 0; transform: scale(.6) translateY(10px); } to { opacity: 1; transform: none; } }
        @keyframes noteBob { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }
        ::-webkit-scrollbar { display: none; }
      `}</style>
    </div>
  );
}
