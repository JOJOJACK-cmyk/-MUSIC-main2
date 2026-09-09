#!/bin/sh
# SRS exec.publish 로 방송 시작 시 실행됨.
#   인자 $1 = 스트림 키 (SRS 의 [stream] 변수)
#   방송 중인 HLS 에서 한 프레임을 뽑아 <키>.jpg 로 저장하고 2분마다 갱신한다.
#   ffmpeg 은 매번 짧게 실행되고 끝나므로(1초 내외) CPU 부담이 거의 없다.
#   방송이 끊기면 SRS 가 이 프로세스를 종료한다.

KEY="$1"
[ -z "$KEY" ] && exit 1

FF=/usr/local/srs/objs/ffmpeg/bin/ffmpeg
DIR=/usr/local/srs/objs/nginx/html/live
OUT="$DIR/$KEY.jpg"
TMP="$DIR/$KEY.jpg.part"
SRC="http://127.0.0.1:8080/live/$KEY.m3u8"

# 첫 HLS 세그먼트가 만들어질 때까지 잠깐 대기
sleep 6

while true; do
  if "$FF" -hide_banner -loglevel fatal -y -rw_timeout 8000000 \
       -i "$SRC" -frames:v 1 -an -vf "scale=640:-2" -qscale:v 5 \
       -f image2 -update 1 "$TMP" 2>/dev/null; then
    mv -f "$TMP" "$OUT"
  fi
  sleep 120
done
