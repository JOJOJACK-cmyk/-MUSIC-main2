# 🎵 StreamWave Music & Live (React Client)

기존 Spring Boot + Thymeleaf 기반의 음원 스트리밍 및 소셜 로그인 프로젝트를 완전한 **React (SPA)** 환경으로 전환한 프론트엔드 프로젝트입니다.

---

## 🚀 빠른 시작 (Getting Started)

### 1. 패키지 설치
```bash
npm install
```

### 2. 개발 서버 실행
```bash
npm run dev
```
브라우저에서 `http://localhost:3000`으로 접속합니다. (Spring Boot 백엔드 `http://localhost:8080`과 자동 프록시 연동)

### 3. 빌드 (배포용)
```bash
npm run build
```

---

## 📂 프로젝트 구조 (Structure)

```text
src/
├── api/
│   ├── axiosInstance.js   # 공통 Axios 설정 및 백엔드 예외 인터셉터
│   ├── musicApi.js        # /api/musics CRUD 통신 함수
│   └── authApi.js         # /api/signup, /login, OAuth2 소셜 로그인 연동
├── components/
│   ├── Sidebar.jsx        # 좌측 GNB 내비게이션 & 음원 등록 버튼
│   ├── Header.jsx         # 상단 검색바 & 프로필 영역
│   ├── HeroBanner.jsx     # 메인 피크 배너
│   ├── MusicCard.jsx      # 개별 음원 카드 (재생, 수정, 삭제)
│   ├── MusicModal.jsx     # 음원 등록/수정 모달 팝업
│   └── PlayerBar.jsx      # 하단 고정 플레이어 (재생바, 볼륨 조절, 곡 정보)
├── context/
│   └── PlayerContext.jsx  # 전역 재생 상태 관리 (재생, 일시정지, 셔플, 반복, 볼륨)
├── pages/
│   ├── MainPage.jsx       # 홈 화면 (추천 음악 리스트 & 검색)
│   ├── LoginPage.jsx      # 로그인 & 회원가입 & 소셜 로그인 (카카오/구글/네이버)
│   ├── ChartPage.jsx      # TOP 100 차트
│   ├── LivePage.jsx       # 실시간 라이브
│   └── LibraryPage.jsx    # 내 보관함
├── styles/
│   └── style.css          # 기존 마젠타 핑크 다크 테마 완벽 이전
├── App.jsx                # 라우팅 및 전역 레이아웃
└── main.jsx
```

---

## 🔗 백엔드 API 연동 사양
* **GET `/api/musics`** : 전체 음원 목록 조회
* **POST `/api/musics`** : 새 음원 등록 (`MusicDto.CreateRequest`)
* **PUT `/api/musics/{id}`** : 음원 정보 수정 (`MusicDto.UpdateRequest`)
* **DELETE `/api/musics/{id}`** : 음원 삭제
* **POST `/api/signup`** : 일반 회원가입
* **POST `/login`** : 폼 로그인
* **GET `/oauth2/authorization/{provider}`** : 카카오, 구글, 네이버 소셜 로그인
