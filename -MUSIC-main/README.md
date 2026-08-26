# MUSIC 프로젝트 실행 가이드

YouTube 기반 음악 스트리밍 및 라이브 방송 프로젝트입니다.

## 1. 사용 기술

### Backend
- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- MySQL
- Redis
- AWS S3
- CloudFront
- WebSocket

### Frontend
- React
- Vite
- Axios
- hls.js
- YouTube IFrame API

### Infrastructure
- Docker
- Redis
- SRS
- OBS
- AWS S3
- CloudFront


---

# 2. 프로젝트 구조

```text
music
├─ backend
│  └─ Spring Boot
│
├─ frontend
│  └─ React
│
└─ infra
   └─ srs
      ├─ docker-compose.yml
      └─ srs.conf
3. 사전 설치

다음 프로그램이 필요합니다.

Java JDK
IntelliJ IDEA
Node.js / npm
MySQL
Docker Desktop
Git
OBS Studio (라이브 방송 테스트 시)
4. MySQL 설정

로컬 MySQL에 프로젝트 DB를 생성합니다.

CREATE DATABASE music_db;

각자 로컬 환경에 맞게 application.properties에서
MySQL 계정 정보를 설정해야 합니다.

예시:

spring.datasource.url=jdbc:mysql://localhost:3306/music_db
spring.datasource.username=본인_MYSQL_ID
spring.datasource.password=본인_MYSQL_PASSWORD
5. Redis 실행

Docker Desktop을 먼저 실행합니다.

Redis 컨테이너가 없는 경우:

docker run -d --name music-redis -p 6379:6379 redis:7-alpine

이미 생성되어 있다면:

docker start music-redis

실행 확인:

docker ps

Redis 연결 테스트:

docker exec -it music-redis redis-cli

Redis 내부:

PING

정상 결과:

PONG
6. Redis 음악 스냅샷

Spring Boot의 스케줄러가 음악 정보를 Redis에 주기적으로 저장합니다.

전체 음악 스냅샷 Redis Key:

music:snapshot:all

확인:

GET music:snapshot:all
7. Redis TOP100

음악을 일정 시간 이상 청취하면 MySQL의 listen_log에
청취 기록이 저장됩니다.

Spring Boot 스케줄러는 해당 로그를 음악별로 집계하여
Redis TOP100을 생성합니다.

Redis Key:

music:ranking:top100

확인:

docker exec -it music-redis redis-cli --raw GET music:ranking:top100

TOP100 조회 API:

GET /api/music-snapshot/top100

동작 구조:

음악 청취
   ↓
listen_log
   ↓
음악별 청취 횟수 집계
   ↓
TOP100 생성
   ↓
Redis
   ↓
GET /api/music-snapshot/top100
8. SRS 라이브 스트리밍 서버

Docker Desktop이 실행 중이어야 합니다.

SRS 폴더:

music/infra/srs

해당 폴더에서:

docker compose up -d

실행 확인:

docker ps

정상 실행 시:

music-srs

컨테이너가 Up 상태로 표시됩니다.

9. SRS 포트
1935 → RTMP 송출
1985 → SRS HTTP API
8081 → HLS 재생

Spring Boot:

8080

따라서 Spring Boot와 SRS의 HTTP 포트가 충돌하지 않습니다.

10. OBS 설정

OBS → 설정 → 방송

서비스:

사용자 지정

서버:

rtmp://localhost/live

스트림 키:

livestream

최종 RTMP 주소:

rtmp://localhost/live/livestream
11. HLS 재생

OBS 방송 시작 후:

http://localhost:8081/live/livestream.m3u8

현재 SRS HLS 설정:

hls_fragment 1;
hls_window 6;

로컬 테스트 기준 약 7~8초 수준의 지연을 확인했습니다.

10분 연속 방송 테스트에서도 끊김 없이 정상 동작했습니다.

12. AWS S3

S3 Bucket:

streamwave-media-sy-2026

S3 버킷은 Public으로 공개하지 않습니다.

AWS 인증 정보는 GitHub에 올리지 말고
각자의 로컬 환경에 등록해야 합니다.

필요한 환경 변수:

AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
AWS_REGION
AWS_S3_BUCKET

예:

AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=streamwave-media-sy-2026

주의:

AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY

값은 README나 GitHub에 절대 작성하지 않습니다.

13. CloudFront CDN

CloudFront가 Private S3의 파일을 사용자에게 전달합니다.

구조:

Spring Boot
   ↓
S3 파일 저장
   ↓
CloudFront
   ↓
React / Browser

CloudFront Domain:

https://d1g99hi566ituv.cloudfront.net

S3 업로드 API는 업로드 완료 후 다음과 같이
CloudFront URL을 반환합니다.

{
  "key": "uploads/example.jpg",
  "url": "https://d1g99hi566ituv.cloudfront.net/uploads/example.jpg",
  "message": "S3 파일 업로드 성공"
}
14. Spring Boot 실행

Backend 폴더를 IntelliJ로 실행합니다.

정상 실행:

http://localhost:8080

Swagger:

http://localhost:8080/swagger-ui/index.html
15. React 실행

Frontend 폴더에서:

npm install
npm run dev

개발 서버:

http://localhost:3000
16. 실행 순서

로컬에서 전체 프로젝트를 테스트할 때 권장 순서:

1. MySQL 실행

2. Docker Desktop 실행

3. Redis 실행

4. SRS 실행

5. Spring Boot 실행

6. React 실행

7. 필요 시 OBS 실행
17. 주요 확인 명령어

Docker 컨테이너:

docker ps

Redis:

docker exec -it music-redis redis-cli

Redis 전체 음악:

GET music:snapshot:all

Redis TOP100:

docker exec -it music-redis redis-cli --raw GET music:ranking:top100
