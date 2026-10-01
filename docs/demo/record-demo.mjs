// StreamWave 시연 영상 녹화 (CleanEat demo.mjs 방식)
//   준비: 데모 전용 스키마로 백엔드(8080)·프론트(3000) 실행 → python demo/seed-demo.py → node demo/live-test-stream.mjs
//   실행: node demo/record-demo.mjs        → out/frames/*.jpg + out/frames.txt (ffmpeg concat 목록)
//   이어서: node demo/make-video.mjs       → StreamWave-demo.mp4 / .gif
// Playwright recordVideo 대신 CDP screencast 프레임을 직접 모은다 (화질·브라우저 다운로드 문제 없음).
// 화면 위에 단계 자막과 마우스 커서를 그려서 소리 없이 봐도 무엇을 하는지 알 수 있게 한다.
import { chromium } from 'playwright-core';
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = join(here, 'out');
const FRAMES = join(OUT, 'frames');
rmSync(FRAMES, { recursive: true, force: true });
mkdirSync(FRAMES, { recursive: true });

const APP = process.env.APP_URL || 'http://localhost:3000';
const state = JSON.parse(readFileSync(join(OUT, 'demo_state.json'), 'utf8'));
const VIEW = { width: 1280, height: 720 };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const executablePath = [process.env.BROWSER_PATH, 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe', '/usr/bin/chromium'].find((p) => p && existsSync(p));
const browser = await chromium.launch({ executablePath, headless: true, args: ['--autoplay-policy=no-user-gesture-required'] });

// ── 화면 위 연출: 자막 · 커서 · 클릭 파문 · 타이틀 카드 (페이지가 바뀌어도 다시 붙는다) ──
const OVERLAY = () => {
  const css = `
    #demo-cursor{position:fixed;z-index:2147483647;width:22px;height:22px;margin:-3px 0 0 -3px;pointer-events:none;
      background:no-repeat center/contain url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M3 2l7 19 2.5-7.5L20 11z' fill='%23111' stroke='white' stroke-width='1.5'/%3E%3C/svg%3E");
      transition:transform .08s}
    #demo-cursor.down{transform:scale(.8)}
    #demo-ripple{position:fixed;z-index:2147483646;width:38px;height:38px;margin:-19px 0 0 -19px;border-radius:50%;
      border:3px solid #ff2f7d;pointer-events:none;opacity:0}
    #demo-ripple.on{animation:demo-r .5s ease-out}
    @keyframes demo-r{from{opacity:.95;transform:scale(.3)}to{opacity:0;transform:scale(1.5)}}
    #demo-caption{position:fixed;z-index:2147483645;left:50%;bottom:104px;transform:translateX(-50%);max-width:90%;
      padding:11px 24px;border-radius:999px;background:rgba(14,8,18,.9);border:1px solid rgba(255,47,125,.45);color:#fff;
      font:600 20px/1.35 'Pretendard','Malgun Gothic',sans-serif;box-shadow:0 8px 30px rgba(0,0,0,.4);pointer-events:none;
      transition:opacity .3s;white-space:nowrap}
    #demo-caption b{color:#ff5c9a;margin-right:10px}
    @media (max-width:600px){#demo-caption{bottom:142px;font-size:15px;white-space:normal;text-align:center;border-radius:16px;padding:9px 14px}}
    #demo-title{position:fixed;inset:0;z-index:2147483644;display:flex;flex-direction:column;align-items:center;justify-content:center;
      gap:14px;background:radial-gradient(circle at 20% 10%,rgba(255,47,125,.55),transparent 55%),
      radial-gradient(circle at 90% 90%,rgba(124,92,255,.55),transparent 55%),#140a19;color:#fff;
      font-family:'Pretendard','Malgun Gothic',sans-serif;transition:opacity .6s}
    body:has(#demo-title) #demo-cursor, body:has(#demo-title) #demo-caption{display:none}
    #demo-title .notes{font-size:40px;letter-spacing:18px;color:#ff9ec4}
    #demo-title h1{margin:0;font-size:68px;letter-spacing:-1px}
    #demo-title p{margin:0;font-size:25px;opacity:.92}
    #demo-title small{margin-top:16px;font-size:17px;opacity:.7}`;
  const mount = () => {
    if (document.getElementById('demo-cursor')) return;
    const style = document.createElement('style'); style.textContent = css; document.head.appendChild(style);
    for (const id of ['demo-cursor', 'demo-ripple', 'demo-caption']) {
      const el = document.createElement('div'); el.id = id; document.body.appendChild(el);
    }
    const saved = JSON.parse(sessionStorage.getItem('demo-state') || '{}');
    const cursor = document.getElementById('demo-cursor');
    cursor.style.left = (saved.x ?? 640) + 'px'; cursor.style.top = (saved.y ?? 360) + 'px';
    const cap = document.getElementById('demo-caption');
    if (saved.caption) cap.innerHTML = saved.caption; else cap.style.opacity = '0';
    document.addEventListener('mousemove', (e) => {
      cursor.style.left = e.clientX + 'px'; cursor.style.top = e.clientY + 'px';
      const s = JSON.parse(sessionStorage.getItem('demo-state') || '{}'); s.x = e.clientX; s.y = e.clientY;
      sessionStorage.setItem('demo-state', JSON.stringify(s));
    }, true);
    document.addEventListener('mousedown', (e) => {
      cursor.classList.add('down');
      const r = document.getElementById('demo-ripple'); r.style.left = e.clientX + 'px'; r.style.top = e.clientY + 'px';
      r.classList.remove('on'); void r.offsetWidth; r.classList.add('on');
    }, true);
    document.addEventListener('mouseup', () => cursor.classList.remove('down'), true);
  };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', mount); else mount();
};

async function newContext(account, viewport = VIEW, extra = {}) {
  const ctx = await browser.newContext({ viewport, locale: 'ko-KR', ...extra });
  await ctx.addInitScript((a) => {
    if (!a) return;
    localStorage.setItem('accessToken', a.token);
    localStorage.setItem('user', JSON.stringify({ id: a.id, email: a.email, nickname: a.nickname, role: a.role }));
  }, account);
  await ctx.addInitScript(OVERLAY);
  return ctx;
}

// ── CDP screencast 로 프레임 수집 ──
const frames = [];
let frameNo = 0;
async function startCast(page) {
  const cdp = await page.context().newCDPSession(page);
  cdp.on('Page.screencastFrame', async (f) => {
    const file = `frames/${String(frameNo++).padStart(5, '0')}.jpg`;
    writeFileSync(join(OUT, file), Buffer.from(f.data, 'base64'));
    frames.push({ file, t: f.metadata.timestamp });
    try { await cdp.send('Page.screencastFrameAck', { sessionId: f.sessionId }); } catch {}
  });
  await cdp.send('Page.startScreencast', { format: 'jpeg', quality: 88, maxWidth: 1280, maxHeight: 844, everyNthFrame: 2 });
  return cdp;
}

// ── 연출 도우미 ──
async function caption(page, html) {
  await page.evaluate((h) => {
    const cap = document.getElementById('demo-caption');
    if (cap) { cap.innerHTML = h; cap.style.opacity = h ? '1' : '0'; }
    const s = JSON.parse(sessionStorage.getItem('demo-state') || '{}'); s.caption = h;
    sessionStorage.setItem('demo-state', JSON.stringify(s));
  }, html);
}
async function moveTo(page, locator, { click = true, steps = 22 } = {}) {
  const el = locator.first();
  await el.scrollIntoViewIfNeeded().catch(() => {});
  const box = await el.boundingBox();
  if (!box) throw new Error('요소를 찾을 수 없음: ' + locator);
  const x = box.x + box.width / 2; const y = box.y + Math.min(box.height / 2, 40);
  await page.mouse.move(x, y, { steps });
  await sleep(220);
  if (click) { await page.mouse.down(); await sleep(90); await page.mouse.up(); }
  await sleep(350);
}
async function type(page, locator, text) {
  await moveTo(page, locator);
  await locator.first().pressSequentially(text, { delay: 70 });
}
async function scrollMain(page, dy, ms = 1400) {
  await page.evaluate(async ([d, t]) => {
    const el = document.querySelector('.m-main') || document.querySelector('.main-content') || document.scrollingElement;
    const start = el.scrollTop; const t0 = performance.now();
    await new Promise((res) => {
      const step = (now) => { const p = Math.min(1, (now - t0) / t); el.scrollTop = start + d * (1 - Math.cos(Math.PI * p)) / 2; p < 1 ? requestAnimationFrame(step) : res(); };
      requestAnimationFrame(step);
    });
  }, [dy, ms]);
}
async function titleCard(page, show, { h1 = 'StreamWave', p = '', small = '' } = {}) {
  await page.evaluate(([s, h, pp, sm]) => {
    let el = document.getElementById('demo-title');
    if (s) {
      if (!el) { el = document.createElement('div'); el.id = 'demo-title'; document.body.appendChild(el); }
      el.innerHTML = `<div class="notes">♪ ♫ ♬</div><h1>${h}</h1><p>${pp}</p><small>${sm}</small>`;
      el.style.opacity = '1';
    } else if (el) { el.style.opacity = '0'; setTimeout(() => el.remove(), 650); }
  }, [show, h1, p, small]);
}
async function say(page, text) {
  await page.fill('.lc-input input', text);
  await page.press('.lc-input input', 'Enter');
}

// ── 방송자·다른 시청자 (녹화하지 않는 창, 채팅/투표용) ──
const live = `/live/${state.broadcastId}`;
const streamerCtx = await newContext(state.streamer, { width: 1100, height: 760 });
const streamerPage = await streamerCtx.newPage();
await streamerPage.goto(APP + live, { waitUntil: 'load' });
const otherCtx = await newContext(state.admin, { width: 1100, height: 760 });
const otherPage = await otherCtx.newPage();
await otherPage.goto(APP + live, { waitUntil: 'load' });

// ── 녹화 시작 ──
const ctx = await newContext(state.viewer);
const page = await ctx.newPage();
await page.goto(APP + '/', { waitUntil: 'load' });
await sleep(2500);
let cast = await startCast(page);

await titleCard(page, true, {
  p: 'YouTube 음악 스트리밍 + 라이브 방송 플랫폼',
  small: '듣고 · 함께 듣고 · 음표로 응원하고 · 음반까지',
});
await sleep(3200);
await titleCard(page, false);
await sleep(700);

// 01 메인
await caption(page, '<b>01</b>실시간 인기 급상승 곡 · 장르별 최신곡');
await scrollMain(page, 420);
await sleep(900);
await scrollMain(page, -420, 1000);
await sleep(500);

// 02 검색 → 재생
await caption(page, '<b>02</b>띄어쓰기와 상관없이 곡·아티스트 검색 → 바로 재생');
await type(page, page.locator('.search-bar input'), '블랙핑크 kill this');
await sleep(1800);
const killCard = page.locator('.music-card:has-text("Kill This Love")');
if (await killCard.count()) {
  await moveTo(page, killCard);
} else {
  await page.fill('.search-bar input', 'BLACKPINK');
  await sleep(1500);
  await moveTo(page, page.locator('.music-card:has-text("Kill This")'));
}
await sleep(3500);

// 03 관련 상품
await caption(page, '<b>03</b>지금 듣는 곡의 음반·굿즈를 플레이어 바에서 바로');
await moveTo(page, page.locator('.related-btn'));
await sleep(1600);
// 재생 중에는 오른쪽 아래 YouTube 플레이어 창이 떠서 그 아래 버튼을 가리므로, 쇼핑 단계 동안 잠시 멈춘다
await moveTo(page, page.locator('.btn-play-main'));
await sleep(400);
await moveTo(page, page.locator('.related-btn'));
await sleep(900);
await moveTo(page, page.locator('.related-item:has-text("KILL THIS LOVE")'));
await sleep(1800);

// 04 주문 → 토스 결제창
await caption(page, '<b>04</b>수량 · 배송지 입력 → 토스페이먼츠 결제 (금액은 서버가 계산)');
await moveTo(page, page.locator('.qty button:has-text("+")'));
await moveTo(page, page.locator('.product-buy-btn'));
await sleep(500);
await type(page, page.locator('input[placeholder="받는 분"]'), '음악러버');
await type(page, page.locator('input[placeholder^="연락처"]'), '010-1234-5678');
await type(page, page.locator('input[placeholder="우편번호"]'), '06236');
await type(page, page.locator('input[placeholder="주소"]'), '서울시 강남구 테헤란로 1');
await moveTo(page, page.locator('.ship-form .product-buy-btn'));
await page.waitForSelector('.toss-modal', { timeout: 10000 }).catch(() => {});
await sleep(4200);
await moveTo(page, page.locator('.toss-modal-close'));
await sleep(600);

// 05 차트
await caption(page, '<b>05</b>실시간 TOP 100 — 최근 24시간 → 누적 청취 순');
await moveTo(page, page.locator('.sidebar .nav-item:has-text("TOP 100")'));
await sleep(1200);
await scrollMain(page, 500, 1600);
await sleep(800);

// 06 라이브
await caption(page, '<b>06</b>OBS 로 송출하는 라이브 방송 (SRS · HLS)');
await moveTo(page, page.locator('.sidebar .nav-item:has-text("실시간 라이브")'));
await sleep(2200);
await moveTo(page, page.locator('.live-card'));
await sleep(3500);

// 07 신청곡 투표
await caption(page, '<b>07</b>신청곡 투표 — 버튼이나 채팅 "투표1" 로 1인 1표');
await say(streamerPage, '어서오세요! 오늘 다음 곡은 투표로 정해요 🎵');
await sleep(1200);
await moveTo(page, page.locator('.lp-opt').first());
await sleep(900);
await say(otherPage, '투표2');
await sleep(1300);
await say(otherPage, '브루노 마스 너무 좋아요');
await sleep(1500);

// 08 채팅
await caption(page, '<b>08</b>실시간 채팅 — 프리미엄 회원은 ♪ PREMIUM 배지 (서버가 판정)');
await type(page, page.locator('.lc-input input'), '킬디스러브 가자~!');
await page.keyboard.press('Enter');
await sleep(1200);
await say(streamerPage, '1번이 앞서고 있네요 😆');
await sleep(2000);

// 09 음표
await caption(page, '<b>09</b>♪ 음표로 방송자 응원 — 1음표 = 1원, 메시지와 함께');
await moveTo(page, page.locator('.note-open-btn'));
await sleep(900);
await moveTo(page, page.locator('.note-preset:has-text("10,000")'));
await type(page, page.locator('.note-message'), '오늘 선곡 최고예요!');
await sleep(500);
await moveTo(page, page.locator('.note-send'));
await page.waitForSelector('.toss-modal', { timeout: 10000 }).catch(() => {});
await sleep(3800);
await moveTo(page, page.locator('.toss-modal-close'));
await sleep(500);

// 10 보관함 · 플레이리스트
await caption(page, '<b>10</b>좋아요 · 최근 들은 곡 · 직접 만드는 플레이리스트');
await moveTo(page, page.locator('.sidebar .nav-item:has-text("내 보관함")'));
await sleep(1200);
await moveTo(page, page.locator('.lib-tab:has-text("최근 들은 곡")'));
await sleep(1300);
await moveTo(page, page.locator('.lib-tab:has-text("플레이리스트")'));
await sleep(1000);
await moveTo(page, page.locator('.pl-card:has-text("드라이브")'));
await sleep(1200);
await moveTo(page, page.locator('.pl-detail-actions button:has-text("전체 재생")'));
await sleep(2200);

// 11 이용권 등급
await caption(page, '<b>11</b>이용권 등급 — 라이트 · 스탠다드 · 프리미엄, 기능은 서버가 판정');
await moveTo(page, page.locator('.sidebar .nav-item:has-text("이용권")'));
await sleep(1500);
await moveTo(page, page.locator('.plan-card:has-text("스탠다드")'));
await sleep(900);
await moveTo(page, page.locator('.plan-card:has-text("프리미엄 이용권")').first());
await sleep(1000);
await scrollMain(page, 420, 1500);
await sleep(2600);

// 12 관리자 — 스토어 관리 (관리자 계정 창으로 전환해서 녹화)
await caption(page, '');
await cast.send('Page.stopScreencast');
const actx = await newContext(state.admin);
const a = await actx.newPage();
await a.goto(APP + '/shop', { waitUntil: 'load' });
await sleep(2200);
const acast = await startCast(a);
await caption(a, '<b>12</b>관리자: 상품 등록 · 곡 연결 · 주문 배송/환불 관리');
await sleep(900);
await moveTo(a, a.locator('.user-nickname'));
await sleep(900);
await moveTo(a, a.locator('button:has-text("스토어 관리")'));
await sleep(1500);
await moveTo(a, a.locator('.admin-product button:has-text("수정")'));
await sleep(2400);
await acast.send('Page.stopScreencast');

// 12 모바일
const mctx = await newContext(state.viewer, { width: 390, height: 844 }, { isMobile: true, hasTouch: true, deviceScaleFactor: 1 });
const m = await mctx.newPage();
await m.goto(APP + '/', { waitUntil: 'load' });
await sleep(2200);
const mcast = await startCast(m);
await caption(m, '<b>13</b>모바일 전용 화면 — 하단 탭바');
await sleep(1200);
await scrollMain(m, 420, 1300);
await sleep(700);
await moveTo(m, m.locator('.m-tab:has-text("라이브")'));
await sleep(1500);
await caption(m, '<b>13</b>모바일 라이브 — 영상 아래 채팅 · 신청곡 투표 탭');
await moveTo(m, m.locator('.m-live-item'));
await sleep(2200);
await say(streamerPage, '모바일 시청자분도 반가워요!');
await sleep(1600);
await moveTo(m, m.locator('.mld-tabs button:has-text("신청곡 투표")'));
await sleep(1800);
await caption(m, '<b>12</b>미니 플레이어 → 전체화면 플레이어');
await moveTo(m, m.locator('.m-tab:has-text("차트")'));
await sleep(1300);
await moveTo(m, m.locator('.m-row').nth(2));
await sleep(2200);
await moveTo(m, m.locator('.m-mini-text'));
await sleep(2600);
await mcast.send('Page.stopScreencast');

// 끝 카드
cast = await startCast(page);
await caption(page, '');
await titleCard(page, true, {
  p: '듣고, 함께 듣고, 응원하세요',
  small: 'github.com/cjsrudgh98-crypto/-MUSIC-main2',
});
await sleep(3500);
await cast.send('Page.stopScreencast');
await sleep(300);

// ── ffmpeg concat 목록 (프레임 사이 실제 시간 간격을 그대로 사용) ──
const lines = [];
for (let i = 0; i < frames.length; i++) {
  const dur = i + 1 < frames.length ? Math.min(Math.max(frames[i + 1].t - frames[i].t, 0.001), 4) : 1.5;
  lines.push(`file '${frames[i].file}'`, `duration ${dur.toFixed(3)}`);
}
lines.push(`file '${frames[frames.length - 1].file}'`);
writeFileSync(join(OUT, 'frames.txt'), lines.join('\n'));
const total = frames.length > 1 ? frames[frames.length - 1].t - frames[0].t : 0;
console.log(`frames: ${frames.length}, 길이 약 ${total.toFixed(1)}초`);

await browser.close();
