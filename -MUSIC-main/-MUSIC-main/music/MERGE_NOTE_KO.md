# 통합본 병합 메모

기준: 첫 번째(세영) 파일을 BASE로 사용하고, 두 번째(팀원) 파일의 Swagger/WebSocket/STOMP 작업을 병합했습니다.

## 세영 버전 유지
- 일반 로그인: `/api/auth/login` + BCrypt 검증 + Spring Security 세션 저장
- 회원가입: `/api/auth/signup`
- OAuth2: Google/Kakao/Naver 및 React `localhost:3000` 성공 리다이렉트
- React `authApi.js`의 JSON 로그인/회원가입 및 `oauthLogin()`
- YouTube API 호출 코드 (`tools.jackson` 사용)
- YouTube -> MusicService -> MySQL 저장 구조

## 팀원 버전 추가
- SwaggerConfig
- Swagger/OpenAPI 애노테이션 및 DTO 스키마
- WebSocketConfig (STOMP/SockJS)
- ChatController
- ChatMessageDto
- Swagger 공개 경로 및 `/ws-chat/**` Security 허용

## 충돌 방지 정리
- 구형 `/api/login`, `/api/signup` 임시 API를 가진 `UserController.java` 제거
- `pom.xml`의 Lombok 중복 제거 상태 유지
- Spring Boot 4.1에 맞게 springdoc-openapi 3.0.3 사용
- Google `prompt=select_account` 강제 authorization-uri 설정은 포함하지 않음
- application.properties의 DB/API/OAuth 비밀값은 모두 환경변수 참조로 변경

## 필요한 환경변수
- DB_PASSWORD
- YOUTUBE_API_KEY
- GOOGLE_CLIENT_ID
- GOOGLE_CLIENT_SECRET
- KAKAO_CLIENT_ID
- KAKAO_CLIENT_SECRET
- NAVER_CLIENT_ID
- NAVER_CLIENT_SECRET

## 확인 URL
- React: http://localhost:3000
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- STOMP/SockJS endpoint: http://localhost:8080/ws-chat
