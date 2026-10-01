# 시연 영상 · 스크린샷용 데모 데이터 시드
#   - 반드시 "데모 전용 스키마"로 띄운 백엔드에 실행할 것 (운영/개발 DB 에 넣지 않는다)
#   - 환경변수: DEMO_API (기본 http://localhost:8080), DEMO_DB (기본 music_readme_demo),
#               MYSQL_BIN (mysql 실행 파일 경로), MYSQL_PWD (mysql root 비밀번호)
#   - 결과(데모 계정 토큰·방송 ID·재생 ID)는 docs/demo/out/demo_state.json 에 저장 → 다른 스크립트가 읽는다
import json, os, subprocess, urllib.request
from pathlib import Path

BASE = os.environ.get("DEMO_API", "http://localhost:8080")
DB = os.environ.get("DEMO_DB", "music_readme_demo")
MYSQL = os.environ.get("MYSQL_BIN", "mysql")
OUT = Path(__file__).resolve().parent / "out"
OUT.mkdir(exist_ok=True)
PW = "test1234!"


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            raw = r.read().decode()
            try:
                return json.loads(raw)
            except Exception:
                return raw
    except urllib.error.HTTPError as e:
        print("HTTP", e.code, method, path, e.read().decode()[:200])
        raise


def sql(q):
    # 비밀번호는 MYSQL_PWD 환경변수로 전달 (명령줄·저장소에 남기지 않음)
    # SQL 은 표준입력(UTF-8)으로 보낸다 — Windows 명령줄 인자는 시스템 코드페이지로 바뀌어 한글이 깨진다
    out = subprocess.run([MYSQL, "-uroot", "--default-character-set=utf8mb4", DB, "-N"], input=q,
                         capture_output=True, text=True, encoding="utf-8")
    return out.stdout.strip()


def user(email, nick):
    call("POST", "/api/auth/signup", {"email": email, "password": PW, "nickname": nick})
    return call("POST", "/api/auth/login", {"email": email, "password": PW})


def thumb(mid):
    return sql(f"SELECT thumbnail_url FROM music WHERE id={mid}")


user("admin@demo.streamwave.kr", "스트림웨이브")
sql("UPDATE users SET role='ROLE_ADMIN' WHERE email='admin@demo.streamwave.kr'")
admin = call("POST", "/api/auth/login", {"email": "admin@demo.streamwave.kr", "password": PW})
streamer = user("dj@demo.streamwave.kr", "DJ하루")
viewer = user("viewer@demo.streamwave.kr", "음악러버")

# 상품 (곡 ID 는 데모 카탈로그 기준 — 다른 카탈로그면 musicId 를 바꿀 것)
products = [
    ("BLACKPINK 2nd Mini Album 'KILL THIS LOVE'", "ALBUM", 23000, 40, "BLACKPINK, 블랙핑크", 1235,
     "포토북 + 랜덤 포토카드 1종 + 가사지 구성의 정규 미니앨범입니다."),
    ("BLACKPINK 공식 응원봉 VER.2", "MERCH", 45000, 15, "BLACKPINK, 블랙핑크", None,
     "블루투스 연동 공식 응원봉. 콘서트 현장 연출과 연동됩니다."),
    ("Linkin Park - Hybrid Theory (LP)", "ALBUM", 39000, 8, "Linkin Park", 2237,
     "'In The End' 수록. 180g 블랙 바이닐."),
    ("Bruno Mars - Doo-Wops & Hooligans (CD)", "ALBUM", 18000, 25, "Bruno Mars", 1350,
     "'Just The Way You Are' 수록 데뷔 정규 앨범."),
    ("Post Malone 월드투어 티셔츠", "MERCH", 35000, 0, "Post Malone", 353,
     "투어 한정 프린팅 반팔 티셔츠 (현재 품절)."),
    ("Shawn Mendes - Illuminate (CD)", "ALBUM", 19000, 30, "Shawn Mendes", 403,
     "'Treat You Better' 수록 2집."),
]
for name, cat, price, stock, artist, mid, desc in products:
    call("POST", "/api/shop/admin/products", {
        "name": name, "category": cat, "price": price, "stock": stock, "artist": artist,
        "musicId": mid, "imageUrl": thumb(mid if mid else 1240), "description": desc,
    }, admin["token"])

# 라이브 채널 + 신청곡 투표 (카탈로그 곡 연결)
st = streamer["token"]
call("POST", "/api/broadcast/stream-key", None, st)
call("PATCH", "/api/broadcast/channel", {
    "title": "🎧 금요일 밤 신청곡 라이브", "description": "듣고 싶은 곡을 투표해 주세요!",
    "category": "음악", "songRequestEnabled": "true"}, st)
call("PATCH", "/api/broadcast/status?status=ON", None, st)
mine = call("GET", "/api/broadcast/mine", None, st)
bid = mine["id"]
playback_id = mine["streamKey"].split("?")[0]  # OBS 키 "pb_xxx?key=..." 앞부분 = 공개 재생 ID
call("PUT", f"/api/broadcast/{bid}/poll",
     {"options": [{"musicId": 1235}, {"musicId": 1350}, {"musicId": 2237}, {"musicId": 403}]}, st)

# 시청자: 프리미엄 이용권(전곡 재생 · 플레이리스트 · 채팅 배지 · 스토어 할인), 좋아요, 플레이리스트, 최근 들은 곡
sql("INSERT INTO tb_pass (user_id, pass_name, plan_id, start_date, expire_date, is_active) "
    "SELECT id, '프리미엄 이용권', 'premium', NOW(), NOW() + INTERVAL 30 DAY, b'1' FROM users "
    "WHERE email='viewer@demo.streamwave.kr'")
vt = viewer["token"]
for mid in [1235, 1240, 1350, 403, 353]:
    call("POST", f"/api/musics/{mid}/like", {}, vt)
pl = call("POST", "/api/playlists", {"name": "드라이브할 때 듣는 노래"}, vt)
for mid in [353, 1350, 403, 2237, 399, 1235]:
    call("POST", f"/api/playlists/{pl['id']}/tracks", {"musicId": mid}, vt)
pl2 = call("POST", "/api/playlists", {"name": "K-POP 모음"}, vt)
for mid in [1240, 1235]:
    call("POST", f"/api/playlists/{pl2['id']}/tracks", {"musicId": mid}, vt)
for mid in [2237, 403, 1350, 1235, 353, 399, 376]:
    call("POST", "/api/v1/logs/listen", {"musicId": mid, "listenSeconds": 30}, vt)

json.dump({"viewer": viewer, "streamer": streamer, "admin": admin, "broadcastId": bid,
           "playbackId": playback_id, "playlistId": pl["id"]},
          open(OUT / "demo_state.json", "w", encoding="utf-8"), ensure_ascii=False)
print("seeded broadcast", bid, "playback", playback_id, "playlist", pl["id"])
