import React, { createContext, useContext, useState } from 'react';

const LiveViewContext = createContext(null);

/**
 * 지금 보고 있(었)던 라이브 방송 정보. 라이브 상세 페이지를 벗어나도 값을 들고 있어서,
 * 전역 미니 플레이어(LiveMiniPlayer)가 화면 한 켠에 작게 이어서 보여줄 수 있게 한다.
 * (유튜브 음악의 "NOW PLAYING" 플로팅 박스와 같은 방식)
 */
export const LiveViewProvider = ({ children }) => {
  const [activeBroadcast, setActiveBroadcast] = useState(null);
  // { id, hlsUrl, title, broadcasterNickname, thumbnailUrl }

  // 지금 라이브 목록/카드가 화면에 이미 보이고 있는지 (메인 페이지 '라이브' 탭 등).
  // true 인 동안은 플로팅 LIVE 버튼(LiveNowButton)이 중복으로 겹쳐 보이지 않도록 숨긴다.
  const [browsingLive, setBrowsingLive] = useState(false);

  return (
    <LiveViewContext.Provider value={{ activeBroadcast, setActiveBroadcast, browsingLive, setBrowsingLive }}>
      {children}
    </LiveViewContext.Provider>
  );
};

export const useLiveView = () => useContext(LiveViewContext);
