import { useEffect, useState } from 'react';

// 모바일 전용 화면(하단 탭바·미니 플레이어)으로 전환하는 기준 폭. 이보다 넓으면 데스크톱 화면.
export const MOBILE_QUERY = '(max-width: 768px)';

/** 화면 폭이 모바일 기준 이하인지. 창 크기·기기 회전에 따라 바뀐다. */
export default function useIsMobile() {
  const get = () => typeof window !== 'undefined' && window.matchMedia(MOBILE_QUERY).matches;
  const [isMobile, setIsMobile] = useState(get);
  useEffect(() => {
    const mq = window.matchMedia(MOBILE_QUERY);
    const onChange = () => setIsMobile(mq.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);
  return isMobile;
}
