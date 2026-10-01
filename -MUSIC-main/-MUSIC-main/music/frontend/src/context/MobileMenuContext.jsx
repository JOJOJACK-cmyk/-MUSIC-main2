import { createContext, useContext } from 'react';

/**
 * 모바일(좁은 화면) ☰ 메뉴 열림 상태.
 * AppShell 이 값을 제공하고, Header 의 ☰ 버튼과 Sidebar(서랍)가 함께 쓴다.
 * 데스크톱에서는 CSS 로 ☰ 버튼이 숨겨지고 사이드바가 항상 보이므로 영향이 없다.
 */
export const MobileMenuContext = createContext({ open: false, setOpen: () => {} });

export const useMobileMenu = () => useContext(MobileMenuContext);
