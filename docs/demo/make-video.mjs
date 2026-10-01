// record-demo.mjs 가 모은 프레임 → StreamWave-demo.mp4 (1280x720) + StreamWave-demo.gif (README 상단용, 2배속)
//   node demo/make-video.mjs
import { spawnSync } from 'node:child_process';
import { statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import ffmpeg from 'ffmpeg-static';

const here = dirname(fileURLToPath(import.meta.url));
const list = join(here, 'out', 'frames.txt');
const mp4 = join(here, 'StreamWave-demo.mp4');
const gif = join(here, 'StreamWave-demo.gif');

// 모바일 구간(390px 폭) 프레임도 같은 1280x720 화면 가운데에 놓는다
const FIT = 'scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2:color=0x0d0710,setsar=1';

function run(args) {
  const r = spawnSync(ffmpeg, ['-hide_banner', '-loglevel', 'error', '-y', ...args], { stdio: 'inherit' });
  if (r.status !== 0) throw new Error('ffmpeg 실패: ' + args.join(' '));
}

run(['-f', 'concat', '-safe', '0', '-i', list,
  '-vf', `${FIT},fps=30,format=yuv420p`, '-c:v', 'libx264', '-preset', 'slow', '-crf', '23', '-movflags', '+faststart', mp4]);

run(['-i', mp4, '-vf',
  'setpts=PTS/2,fps=8,scale=760:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=96:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle',
  gif]);

const mb = (f) => (statSync(f).size / 1024 / 1024).toFixed(1) + 'MB';
console.log('saved', mp4, mb(mp4));
console.log('saved', gif, mb(gif));
