// 방송 콘텐츠 카테고리 (프로필 패널 채널 설정 · 메인 라이브 뷰에서 공용)
export const LIVE_CATEGORIES = [
  '음악 라이브',
  '함께 듣기',
  '토크·수다',
  '신곡·발매',
  '커버·연주',
  '기타',
];

export const DEFAULT_LIVE_CATEGORY = '기타';

// 서버에 저장된 값이 목록에 없거나 비어있으면 '기타'로 취급
export const normalizeCategory = (value) =>
  LIVE_CATEGORIES.includes(value) ? value : DEFAULT_LIVE_CATEGORY;
