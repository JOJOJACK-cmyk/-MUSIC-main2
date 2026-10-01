// OBS 커스텀 브라우저 독처럼 사이트 탭과 세션 쿠키를 공유하지 않는 창(별도 브라우저 프로필)에서도
// 인증된 STOMP 연결이 되도록, 저장된 Bearer 토큰을 핸드셰이크 쿼리 파라미터로 실어 보낸다.
// (백엔드 TokenAuthFilter 가 ?token= 을 명시적으로 지원함 — 세션이 있으면 무해하게 무시됨)
export const wsUrl = (path) => {
  const token = localStorage.getItem('accessToken') || localStorage.getItem('token');
  return token ? `${path}?token=${encodeURIComponent(token)}` : path;
};
