// 시연용 라이브 테스트 스트림 (SRS · OBS 없이)
//   node demo/live-test-stream.mjs        (docs 폴더에서, seed-demo.py 실행 후)
//
// ffmpeg 로 "방송 화면"을 실시간 HLS 로 만들고, 백엔드 기본 hls.base-url(http://localhost:8081)과 같은 주소로 서빙한다.
//   http://localhost:8081/live/{playbackId}.m3u8   ← 라이브 상세 페이지가 재생하는 주소
//   http://localhost:8081/live/{playbackId}.jpg    ← 라이브 목록 썸네일
// 영상: 방송 제목 + 시계 + 음파(오디오 파형). Ctrl+C 로 종료.
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { createReadStream, existsSync, mkdirSync, readFileSync, rmSync } from 'node:fs';
import { dirname, extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import ffmpegPath from 'ffmpeg-static';

const here = dirname(fileURLToPath(import.meta.url));
const state = JSON.parse(readFileSync(join(here, 'out', 'demo_state.json'), 'utf8'));
const pb = state.playbackId;
const root = join(here, 'out', 'hls');
const liveDir = join(root, 'live');
rmSync(root, { recursive: true, force: true });
mkdirSync(liveDir, { recursive: true });

const FONT = 'C\\:/Windows/Fonts/malgunbd.ttf'; // 한글 폰트 (Windows). 다른 OS 면 경로 변경
const title = 'DJ하루의 금요일 밤 신청곡 라이브';

// 화음처럼 들리는 오디오(파형 시각화용) — 실제 소리는 페이지에서 음소거 상태
const audio = 'aevalsrc=0.22*sin(2*PI*220*t)*(0.6+0.4*sin(2*PI*0.5*t))'
  + '+0.16*sin(2*PI*277.18*t)*(0.6+0.4*sin(2*PI*0.7*t+1))'
  + '+0.12*sin(2*PI*329.63*t)*(0.6+0.4*sin(2*PI*0.9*t+2)):s=44100';

const filter = [
  '[0:v]drawbox=x=0:y=0:w=iw:h=ih:color=0x1b0a24@1:t=fill,',
  `drawtext=fontfile='${FONT}':text='● LIVE':fontcolor=0xff2f7d:fontsize=34:x=60:y=50,`,
  `drawtext=fontfile='${FONT}':text='${title}':fontcolor=white:fontsize=52:x=(w-text_w)/2:y=170,`,
  `drawtext=fontfile='${FONT}':text='StreamWave · 신청곡 투표 진행 중':fontcolor=0xcdb6c6:fontsize=28:x=(w-text_w)/2:y=245,`,
  `drawtext=fontfile='${FONT}':text='%{localtime\\:%H\\\\\\:%M\\\\\\:%S}':fontcolor=0xa98db9:fontsize=28:x=w-text_w-60:y=54[bg];`,
  '[1:a]asplit=2[a1][aout];',
  '[a1]showwaves=s=1280x300:mode=cline:rate=30:colors=0xff2f7d|0x7c5cff:scale=sqrt[w];',
  '[bg][w]overlay=0:360[v]',
].join('');

const args = [
  '-hide_banner', '-loglevel', 'error',
  '-re', '-f', 'lavfi', '-i', 'color=c=black:s=1280x720:r=30',
  '-re', '-f', 'lavfi', '-i', audio,
  '-filter_complex', filter,
  '-map', '[v]', '-map', '[aout]',
  '-c:v', 'libx264', '-preset', 'veryfast', '-tune', 'zerolatency', '-g', '30', '-pix_fmt', 'yuv420p',
  '-c:a', 'aac', '-b:a', '96k',
  '-f', 'hls', '-hls_time', '1', '-hls_list_size', '6', '-hls_flags', 'delete_segments+omit_endlist',
  join(liveDir, `${pb}.m3u8`),
];
const ff = spawn(ffmpegPath, args, { stdio: ['ignore', 'inherit', 'inherit'] });
ff.on('exit', (code) => { console.log('ffmpeg 종료', code); process.exit(code ?? 0); });

// 썸네일: 몇 초 뒤 재생 목록에서 한 프레임
setTimeout(() => {
  spawn(ffmpegPath, ['-hide_banner', '-loglevel', 'error', '-y', '-i', join(liveDir, `${pb}.m3u8`),
    '-frames:v', '1', '-vf', 'scale=640:-2', join(liveDir, `${pb}.jpg`)], { stdio: 'inherit' });
}, 6000);

const TYPES = { '.m3u8': 'application/vnd.apple.mpegurl', '.ts': 'video/mp2t', '.jpg': 'image/jpeg' };
createServer((req, res) => {
  const path = normalize(decodeURIComponent(req.url.split('?')[0])).replace(/^([/\\])+/, '');
  const file = join(root, path);
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Cache-Control', 'no-cache');
  if (!file.startsWith(root) || !existsSync(file)) { res.statusCode = 404; res.end(); return; }
  res.setHeader('Content-Type', TYPES[extname(file)] || 'application/octet-stream');
  createReadStream(file).pipe(res);
}).listen(8081, () => console.log(`HLS: http://localhost:8081/live/${pb}.m3u8`));

process.on('SIGINT', () => { ff.kill('SIGINT'); });
