package com.example.music.service;

import com.example.music.dto.YouTubeVideoDto;
import com.example.music.entity.Music;
import com.example.music.repository.MusicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeApiService {

    private final MusicRepository musicRepository;
    private final NotificationService notificationService;
    private final com.example.music.repository.ListenLogRepository listenLogRepository;
    private final com.example.music.repository.LikedMusicRepository likedMusicRepository;
    private final MusicRemover musicRemover;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // 여러 개의 키를 콤마로 나열 (youtube.api.keys), 없으면 단일 youtube.api.key 사용
    @Value("${youtube.api.keys:${youtube.api.key:}}")
    private String rawApiKeys;

    private volatile java.util.List<String> apiKeys = java.util.List.of();
    private final java.util.concurrent.atomic.AtomicInteger keyIndex = new java.util.concurrent.atomic.AtomicInteger(0);
    // 키별 소진 해제 시각 (403/429 발생 시 다음 태평양 자정까지 봉인)
    private final java.util.Map<String, java.time.Instant> keyExhaustedUntil = new java.util.concurrent.ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    void initApiKeys() {
        apiKeys = java.util.Arrays.stream(rawApiKeys.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (apiKeys.isEmpty()) {
            log.warn("[YouTube] API 키가 설정되지 않았습니다. (youtube.api.key 또는 youtube.api.keys)");
        } else {
            log.info("[YouTube] API 키 {}개 로드", apiKeys.size());
        }
    }

    /** 지금 사용 가능한(소진되지 않은) 키. 전부 소진 상태면 QuotaExceededException. */
    private synchronized String currentApiKey() {
        if (apiKeys.isEmpty()) throw new QuotaExceededException("YouTube API 키가 없습니다.");
        java.time.Instant now = java.time.Instant.now();
        for (int i = 0; i < apiKeys.size(); i++) {
            int idx = (keyIndex.get() + i) % apiKeys.size();
            String k = apiKeys.get(idx);
            java.time.Instant until = keyExhaustedUntil.get(k);
            if (until == null || now.isAfter(until)) {
                keyIndex.set(idx);
                return k;
            }
        }
        throw new QuotaExceededException("모든 YouTube API 키의 할당량이 소진되었습니다.");
    }

    /**
     * 키를 "소진" 처리해야 하는 오류인가. 429 는 항상, 403 은 할당량 계열 reason 일 때만.
     * (키 오류·API 미활성화·영상 접근 금지 같은 403 까지 소진 처리하면 멀쩡한 키가 하루 동안 봉인된다)
     */
    private static boolean isQuotaError(int status, String reason) {
        if (status == 429) return true;
        if (status != 403) return false;
        return switch (reason == null ? "" : reason) {
            case "quotaExceeded", "dailyLimitExceeded", "rateLimitExceeded", "userRateLimitExceeded" -> true;
            default -> false;
        };
    }

    /** 방금 쓴 키를 소진 처리하고 다음 키로 넘어간다. */
    private synchronized void markKeyExhausted(String key) {
        keyExhaustedUntil.put(key, nextPacificMidnight());
        keyIndex.updateAndGet(v -> (v + 1) % Math.max(1, apiKeys.size()));
        String tail = key != null && key.length() > 6 ? key.substring(key.length() - 6) : "?";
        int alive = (int) apiKeys.stream()
                .filter(k -> { var u = keyExhaustedUntil.get(k); return u == null || java.time.Instant.now().isAfter(u); })
                .count();
        log.warn("[YouTube] 키 소진(…{}) → 다음 키로 전환. 남은 사용가능 키 {}개", tail, alive);
    }

    /** YouTube 일일 할당량 리셋 = 태평양 자정. 그때까지 봉인. */
    private java.time.Instant nextPacificMidnight() {
        java.time.ZoneId pt = java.time.ZoneId.of("America/Los_Angeles");
        java.time.ZonedDateTime nowPt = java.time.ZonedDateTime.now(pt);
        return nowPt.toLocalDate().plusDays(1).atStartOfDay(pt).toInstant();
    }

    private static final Pattern HANGUL_PATTERN = Pattern.compile("[\\uAC00-\\uD7A3\\u3130-\\u318F]");
    private static final Pattern KANA_PATTERN = Pattern.compile("[\\u3040-\\u309F\\u30A0-\\u30FF]");

    // 💡 로마자 표기라 문자로는 국적을 못 가리는 주요 아티스트 → 장르 매핑 (유튜브 뮤직 기준).
    //    "- Topic" 자동생성 채널 등 한글/가나가 없는 케이스 보정용. (lisa/eve 등 모호어는 제외)
    private static final Map<String, String> KNOWN_ARTIST_GENRE = Map.ofEntries(
            // ── K-POP ──
            Map.entry("bts", "KPOP"), Map.entry("bangtan", "KPOP"), Map.entry("blackpink", "KPOP"),
            Map.entry("twice", "KPOP"), Map.entry("seventeen", "KPOP"), Map.entry("stray kids", "KPOP"),
            Map.entry("straykids", "KPOP"), Map.entry("ateez", "KPOP"), Map.entry("enhypen", "KPOP"),
            Map.entry("tomorrow x together", "KPOP"), Map.entry("txt", "KPOP"), Map.entry("nct", "KPOP"),
            Map.entry("exo", "KPOP"), Map.entry("red velvet", "KPOP"), Map.entry("aespa", "KPOP"),
            Map.entry("itzy", "KPOP"), Map.entry("le sserafim", "KPOP"), Map.entry("newjeans", "KPOP"),
            Map.entry("new jeans", "KPOP"), Map.entry("illit", "KPOP"), Map.entry("riize", "KPOP"),
            Map.entry("zerobaseone", "KPOP"), Map.entry("boynextdoor", "KPOP"), Map.entry("kiss of life", "KPOP"),
            Map.entry("girls' generation", "KPOP"), Map.entry("girls generation", "KPOP"), Map.entry("snsd", "KPOP"),
            Map.entry("taeyeon", "KPOP"), Map.entry("taemin", "KPOP"), Map.entry("chung ha", "KPOP"),
            Map.entry("chungha", "KPOP"), Map.entry("sunmi", "KPOP"), Map.entry("mamamoo", "KPOP"),
            Map.entry("hwasa", "KPOP"), Map.entry("zico", "KPOP"), Map.entry("psy", "KPOP"),
            Map.entry("bigbang", "KPOP"), Map.entry("g-dragon", "KPOP"), Map.entry("gdragon", "KPOP"),
            Map.entry("jennie", "KPOP"), Map.entry("jisoo", "KPOP"), Map.entry("jungkook", "KPOP"),
            Map.entry("jimin", "KPOP"), Map.entry("meovv", "KPOP"), Map.entry("izna", "KPOP"),
            Map.entry("hearts2hearts", "KPOP"), Map.entry("baekhyun", "KPOP"), Map.entry("nmixx", "KPOP"),
            Map.entry("kep1er", "KPOP"), Map.entry("(g)i-dle", "KPOP"), Map.entry("gidle", "KPOP"),
            Map.entry("infinite", "KPOP"), Map.entry("the rose", "KPOP"), Map.entry("day6", "KPOP"),
            Map.entry("kyoungseo", "KPOP"), Map.entry("kim gyeol", "KPOP"), Map.entry("nct 127", "KPOP"),
            Map.entry("nct dream", "KPOP"), Map.entry("wayv", "KPOP"), Map.entry("exo-cbx", "KPOP"),
            Map.entry("gaho", "KPOP"), Map.entry("10cm", "KPOP"), Map.entry("akmu", "KPOP"),
            Map.entry("heize", "KPOP"), Map.entry("younha", "KPOP"),
            Map.entry("paul kim", "KPOP"), Map.entry("crush", "KPOP"), Map.entry("dean", "KPOP"),
            Map.entry("colde", "KPOP"), Map.entry("lee mujin", "KPOP"), Map.entry("doyoung", "KPOP"),
            Map.entry("wonwoo", "KPOP"), Map.entry("mingyu", "KPOP"), Map.entry("cortis", "KPOP"),
            Map.entry("allday project", "KPOP"), Map.entry("qwer", "KPOP"), Map.entry("woodz", "KPOP"),
            Map.entry("katseye", "KPOP"), Map.entry("tws", "KPOP"), Map.entry("xikers", "KPOP"),
            Map.entry("&team", "KPOP"), Map.entry("triples", "KPOP"), Map.entry("viviz", "KPOP"),
            Map.entry("stayc", "KPOP"), Map.entry("billlie", "KPOP"), Map.entry("fifty fifty", "KPOP"),
            Map.entry("babymonster", "KPOP"), Map.entry("artms", "KPOP"),
            Map.entry("kwon eunbi", "KPOP"), Map.entry("jo yuri", "KPOP"), Map.entry("hyolyn", "KPOP"),
            Map.entry("taeyang", "KPOP"), Map.entry("cignature", "KPOP"),
            Map.entry("close your eyes", "KPOP"),
            Map.entry("n.flying", "KPOP"), Map.entry("cravity", "KPOP"), Map.entry("p1harmony", "KPOP"),
            Map.entry("evnne", "KPOP"), Map.entry("nexz", "KPOP"),
            Map.entry("lee suhyun", "KPOP"), Map.entry("tuide", "KPOP"), Map.entry("so soo bin", "KPOP"),
            Map.entry("hitgs", "KPOP"), Map.entry("kimmuseum", "KPOP"), Map.entry("lee changsub", "KPOP"),
            Map.entry("lim ga young", "KPOP"), Map.entry("nell", "KPOP"), Map.entry("urban zakapa", "KPOP"),
            Map.entry("standing egg", "KPOP"), Map.entry("melomance", "KPOP"), Map.entry("sondia", "KPOP"),
            Map.entry("jannabi", "KPOP"), Map.entry("lucy band", "KPOP"),
            // 현행 아이돌
            Map.entry("plave", "KPOP"), Map.entry("ampers", "KPOP"), Map.entry("kickflip", "KPOP"),
            Map.entry("all(h)ours", "KPOP"), Map.entry("nowadays", "KPOP"), Map.entry("say my name", "KPOP"),
            Map.entry("unis", "KPOP"), Map.entry("badvillain", "KPOP"), Map.entry("ifeye", "KPOP"),
            Map.entry("candy shop", "KPOP"), Map.entry("woo!ah!", "KPOP"), Map.entry("tri.be", "KPOP"),
            Map.entry("mimiirose", "KPOP"), Map.entry("epex", "KPOP"), Map.entry("tempest", "KPOP"),
            Map.entry("younite", "KPOP"), Map.entry("ntx", "KPOP"), Map.entry("blitzers", "KPOP"),
            Map.entry("drippin", "KPOP"), Map.entry("ciipher", "KPOP"), Map.entry("omega x", "KPOP"),
            Map.entry("bae173", "KPOP"), Map.entry("dkz", "KPOP"), Map.entry("mcnd", "KPOP"),
            Map.entry("verivery", "KPOP"), Map.entry("onewe", "KPOP"), Map.entry("onf", "KPOP"),
            Map.entry("golden child", "KPOP"), Map.entry("the boyz", "KPOP"), Map.entry("victon", "KPOP"),
            Map.entry("ab6ix", "KPOP"), Map.entry("kingdom", "KPOP"), Map.entry("purple kiss", "KPOP"),
            Map.entry("lightsum", "KPOP"), Map.entry("cherry bullet", "KPOP"), Map.entry("weeekly", "KPOP"),
            Map.entry("rocket punch", "KPOP"), Map.entry("dreamcatcher", "KPOP"), Map.entry("everglow", "KPOP"),
            Map.entry("gfriend", "KPOP"), Map.entry("oh my girl", "KPOP"), Map.entry("apink", "KPOP"),
            Map.entry("wjsn", "KPOP"), Map.entry("loona", "KPOP"),
            Map.entry("hong jin young", "KPOP"), Map.entry("young tak", "KPOP"), Map.entry("lee chan won", "KPOP"),
            Map.entry("jang min ho", "KPOP"), Map.entry("kim ho joong", "KPOP"), Map.entry("na yoon kwon", "KPOP"),
            // 솔로/보컬/힙합/인디
            Map.entry("baekho", "KPOP"), Map.entry("kim jae hwan", "KPOP"), Map.entry("hwang chi yeul", "KPOP"),
            Map.entry("kyuhyun", "KPOP"), Map.entry("onew", "KPOP"), Map.entry("key", "KPOP"),
            Map.entry("kai", "KPOP"), Map.entry("sehun", "KPOP"), Map.entry("chen", "KPOP"),
            Map.entry("d.o.", "KPOP"), Map.entry("suho", "KPOP"), Map.entry("mark", "KPOP"),
            Map.entry("ten", "KPOP"), Map.entry("hendery", "KPOP"), Map.entry("winter", "KPOP"),
            Map.entry("karina", "KPOP"), Map.entry("giselle", "KPOP"), Map.entry("ningning", "KPOP"),
            Map.entry("wendy", "KPOP"), Map.entry("seulgi", "KPOP"), Map.entry("joy", "KPOP"),
            Map.entry("irene", "KPOP"), Map.entry("yeri", "KPOP"), Map.entry("solar", "KPOP"),
            Map.entry("moonbyul", "KPOP"), Map.entry("wheein", "KPOP"),
            Map.entry("kwon eun bi", "KPOP"), Map.entry("yuqi", "KPOP"), Map.entry("minnie", "KPOP"),
            Map.entry("soyeon", "KPOP"), Map.entry("miyeon", "KPOP"), Map.entry("bibi", "KPOP"),
            Map.entry("lee young ji", "KPOP"), Map.entry("beenzino", "KPOP"),
            Map.entry("changmo", "KPOP"), Map.entry("the quiett", "KPOP"), Map.entry("dok2", "KPOP"),
            Map.entry("simon dominic", "KPOP"), Map.entry("gray", "KPOP"), Map.entry("loco", "KPOP"),
            Map.entry("mino", "KPOP"), Map.entry("bobby", "KPOP"), Map.entry("song mino", "KPOP"),
            Map.entry("zior park", "KPOP"), Map.entry("balming tiger", "KPOP"), Map.entry("hoody", "KPOP"),
            Map.entry("offonoff", "KPOP"), Map.entry("wonstein", "KPOP"),
            Map.entry("sumin", "KPOP"), Map.entry("primary", "KPOP"),
            Map.entry("dynamicduo", "KPOP"), Map.entry("epik high", "KPOP"),
            Map.entry("leellamarz", "KPOP"), Map.entry("bloo", "KPOP"), Map.entry("khundi panda", "KPOP"),
            Map.entry("blase", "KPOP"), Map.entry("owen", "KPOP"), Map.entry("sokodomo", "KPOP"),
            Map.entry("mudd the student", "KPOP"), Map.entry("polodabang", "KPOP"),
            // J-POP 추가
            Map.entry("tuki", "JPOP"), Map.entry("yuuri", "JPOP"),
            Map.entry("saucy dog", "JPOP"), Map.entry("hitsujibungaku", "JPOP"),
            Map.entry("wednesday campanella", "JPOP"),
            Map.entry("da-ice", "JPOP"), Map.entry("tani yuuki", "JPOP"), Map.entry("omoinotake", "JPOP"),
            Map.entry("kanaria", "JPOP"), Map.entry("chanmina", "JPOP"),
            Map.entry("m!lk", "JPOP"), Map.entry("timelesz", "JPOP"),
            Map.entry("travis japan", "JPOP"),
            // ── J-POP ──
            Map.entry("king & prince", "JPOP"), Map.entry("king and prince", "JPOP"), Map.entry("snow man", "JPOP"),
            Map.entry("naniwa danshi", "JPOP"), Map.entry("sixtones", "JPOP"), Map.entry("king gnu", "JPOP"),
            Map.entry("official hige dandism", "JPOP"), Map.entry("higedan", "JPOP"), Map.entry("yoasobi", "JPOP"),
            Map.entry("ado", "JPOP"), Map.entry("yorushika", "JPOP"), Map.entry("vaundy", "JPOP"),
            Map.entry("kenshi yonezu", "JPOP"), Map.entry("mrs. green apple", "JPOP"), Map.entry("mrs green apple", "JPOP"),
            Map.entry("aimyon", "JPOP"), Map.entry("fujii kaze", "JPOP"), Map.entry("one ok rock", "JPOP"),
            Map.entry("radwimps", "JPOP"), Map.entry("back number", "JPOP"), Map.entry("sekai no owari", "JPOP"),
            Map.entry("utada hikaru", "JPOP"), Map.entry("hikaru utada", "JPOP"), Map.entry("milet", "JPOP"),
            Map.entry("number_i", "JPOP"), Map.entry("creepy nuts", "JPOP"), Map.entry("be:first", "JPOP"),
            Map.entry("atarashii gakko", "JPOP"), Map.entry("=love", "JPOP"), Map.entry("hinatazaka46", "JPOP"),
            Map.entry("nogizaka46", "JPOP"), Map.entry("sakurazaka46", "JPOP"), Map.entry("akb48", "JPOP"),
            Map.entry("tuki.", "JPOP"), Map.entry("imase", "JPOP"), Map.entry("reol", "JPOP"),
            Map.entry("zutomayo", "JPOP"), Map.entry("kessoku band", "JPOP"), Map.entry("fictionjunction", "JPOP"),
            Map.entry("do as infinity", "JPOP"), Map.entry("jo1", "JPOP"), Map.entry("ini", "JPOP")
    );

    // 💡 K-POP / J-POP 공식 레이블·유통 채널명 (channelTitle 소문자 부분일치). 지역 힌트보다 신뢰도 높음.
    private static final Map<String, String> LABEL_CHANNEL_GENRE = Map.ofEntries(
            Map.entry("hybe labels", "KPOP"), Map.entry("smtown", "KPOP"), Map.entry("jyp entertainment", "KPOP"),
            Map.entry("yg entertainment", "KPOP"), Map.entry("stone music", "KPOP"), Map.entry("1thek", "KPOP"),
            Map.entry("mydol", "KPOP"), Map.entry("starship", "KPOP"), Map.entry("pledis", "KPOP"),
            Map.entry("kozentertainment", "KPOP"), Map.entry("koz ", "KPOP"), Map.entry("rbw", "KPOP"),
            Map.entry("cube entertainment", "KPOP"), Map.entry("wm entertainment", "KPOP"), Map.entry("fnc entertainment", "KPOP"),
            Map.entry("jellyfish entertainment", "KPOP"), Map.entry("ador", "KPOP"), Map.entry("belift lab", "KPOP"),
            Map.entry("source music", "KPOP"), Map.entry("wakeone", "KPOP"), Map.entry("ist entertainment", "KPOP"),
            Map.entry("antenna", "KPOP"), Map.entry("mnh entertainment", "KPOP"), Map.entry("p nation", "KPOP"),
            Map.entry("aomg", "KPOP"), Map.entry("modhaus", "KPOP"), Map.entry("attrakt", "KPOP"),
            // J-POP
            Map.entry("sony music (japan)", "JPOP"), Map.entry("universal music japan", "JPOP"),
            Map.entry("avex", "JPOP"), Map.entry("toy's factory", "JPOP"), Map.entry("being channel", "JPOP"),
            Map.entry("victor entertainment", "JPOP"), Map.entry("pony canyon", "JPOP"), Map.entry("lantis", "JPOP"),
            Map.entry("flyingdog", "JPOP"), Map.entry("johnny", "JPOP"), Map.entry("j storm", "JPOP"),
            Map.entry("stardust", "JPOP"), Map.entry("ldh", "JPOP")
    );

    // 💡 버튜버 / 버추얼 아이돌 식별 마커 (제목·채널명 소문자 비교). 언어(한/일)보다 우선.
    private static final String[] VTUBER_MARKERS = {
            // 그룹/소속사/일반어
            "이세계아이돌", "이세계 아이돌", "isegye idol", "isegye", "isekai idol",
            "waktaverse", "왁타버스", "우왁굳", "irisé", "아이리제", "아오쿠모", "aokumo",
            "스텔라이브", "stellive", "플레이브", "plave", "버튜버", "vtuber", "v-tuber",
            "버추얼 아이돌", "버추얼아이돌", "virtual idol", "virtual singer", "vsinger",
            "hololive", "홀로라이브", "hololiveen", "hololive english", "hololive indonesia",
            "nijisanji", "니지산지", "にじさんじ", "vspo", "ぶいすぽ", "홀로라이브",
            "phase connect", "idol corp", "prism project", "kamitsubaki", "神椿", "vestia",
            "아이리 칸나", "iri canna", "칸나", "시라유키 히나", "텐코 시부키", "아카네 리제",
            "유즈하 리코", "네네코 마시로", "아라하시 타비", "마스가키", "린 (스텔라이브)",
            // 홀로라이브 멤버
            "gawr gura", "mori calliope", "calliope mori", "takanashi kiara", "ninomae ina",
            "watson amelia", "hakos baelz", "ceres fauna", "ouro kronii", "nanashi mumei",
            "irys", "shiori novella", "koseki bijou", "nerissa ravencroft", "fuwamoco",
            "fuwawa", "mococo", "elizabeth rose bloodflame", "gigi murin", "cecilia immergreen",
            "raora panthera", "hoshimachi suisei", "hoshimachi", "houshou marine", "宝鐘マリン",
            "usada pekora", "shirakami fubuki", "sakura miko", "minato aqua", "nekomata okayu",
            "inugami korone", "shishiro botan", "omaru polka", "tokoyami towa", "amane kanata",
            "tsunomaki watame", "yukihana lamy", "hakui koyori", "kazama iroha", "azki",
            "ninomae ina'nis", "vestia zeta", "kobo kanaeru", "kaela kovalskia", "moona hoshinova",
            "airani iofifteen", "ninomae", "shirogane noel", "shiranui flare", "kiryu coco",
            // 니지산지 멤버
            "kuzuha", "kanae", "tsukino mito", "higuchi kaede", "yashiro kizuku",
            "elira pendora", "pomu rainpuff", "finana ryugu", "selen tatsuki", "enna alouette",
            "millie parfait", "reimu endou", "petra gurin", "rosemi lovelock", "ike eveland",
            // 국내 개인 버튜버
            "주르르", "고세구", "비챤", "릴파", "아이네", "징버거", "칸나 (이세계아이돌)"
    };

    // 💡 단곡 음악으로 인정하는 재생 시간 범위 (초)
    //  - 90초 미만  : 쇼츠 / 티저 / 인트로
    //  - 420초 초과 : 라이브 / 무대영상 모음 / 앨범 전체 / 강연
    private static final long MIN_SONG_SECONDS = 90;
    private static final long MAX_SONG_SECONDS = 480;

    // 💡 음악과 명백히 무관한 유튜브 videoCategoryId (게임 20 / 뉴스 25 / 스포츠 17 /
    //    교육 27 / 과학기술 28 / 사람&블로그 22 는 제외 - 음악 MV도 많이 올라옴)
    private static final Set<String> BLOCKED_CATEGORY_IDS = Set.of(
            "20", "25", "17", "27", "28", "19", "26", "29", "15"
    );

    // 💡 "음악이 아닌 영상" 판별 키워드 (제목 기준, 소문자 비교)
    private static final String[] NON_MUSIC_KEYWORDS = {
            "토크", "잡담", "리뷰", "클립", "뉴스", "vlog", "브이로그", "비하인드", "리액션",
            "생방송", "다시보기", "라디오", "직캠", "인터뷰", "티저", "예고", "메이킹", "making",
            "shorts", "쇼츠", "#shorts", "short", "가사", "해석", "발음", "lyrics", "lyric",
            "special clip", "스페셜 클립", "스페셜클립", "spcial clip", "teaser", "예고편",
            "playlist", "플레이리스트", "모음", "메들리", "메드레이", "medley", "メドレー", "mix", "믹스", "반주기",
            "노래방", "mr", "instrumental", "1시간", "1 hour", "loop", "asmr", "커버", "cover",
            "월드컵", "난이도", "박자", "강좌", "튜토리얼", "tutorial", "리허설", "practice",
            "연습", "쇼케이스", "showcase", "unboxing", "언박싱", "trailer", "announcement",
            "라이브", "(live", "live ver", "live at", "live from", "live performance", "live stage",
            "live tour", "arena tour", "라이브투어", "ライブ", "ライヴ", "tour 20", "cdtv",
            "live 20", "digest", "다이제스트", "コール動画", "メガパック", "megapack", "mega pack",
            "anniversary live", "the live 20",
            "페스티벌", "festival", "vlf", "인기곡", "人気曲", "ランキング", "best of", "top 10", "top10",
            "top 20", "top 40", "top40", "concert", "콘서트", "compilation", "컴필레이션",
            "greatest hits", "greatest pop", "pop hits", "trending pop", "spotify hits",
            "chart hits", "billboard top", "billboard songs", "billboard hot", "billboard hits",
            "hits 20", "mega hits", "hits mix", "hit songs", "히트곡", "메가히트", "the icon sessions",
            "grammy museum", "sing-along", "sing along",
            // 아티스트명 직접 검색 시 섞여 들어오는 비음악 콘텐츠
            "podcast", "팟캐스트", "다큐멘터리", "documentary", "ep.", "episode",
            "댄스 프랙티스", "dance practice", "안무 영상", "choreography", "무대영상", "무대 영상",
            "겟레디", "get ready with", "grwm", "먹방", "mukbang",
            "출근길", "퇴근길", "공항패션", "촬영 현장", "촬영현장",
            "live clip", "라이브 클립", "라이브클립", "댄스 챌린지", "dance challenge",
            "reaction video", "리액션 영상",
            // 시상식/무대 라이브 (음원 아님)
            "live @", " @ ", "mnet", "kcon", "mama 20", "mma 20", "gaon", "골든디스크", "golden disc",
            "시상식", "awards 20", "music awards", "music bank", "뮤직뱅크", "music core", "inkigayo",
            "인기가요", "쇼챔피언", "the show", "엠카운트다운", "mcountdown", "comeback stage",
            "컴백무대", "커버 무대", "교차편집", "stage mix", "무대교차편집",
            // ── 라이브/실황 영상 (음원 아님) ──
            "live映像", "live 映像", "実況", "3d live", "【3d live", "official live", "live video",
            "live music video", "live session", "라이브 세션", "live in studio", "studio live",
            "live at ", "live in ", "live by ", "라이브 by", "unplugged", "언플러그드",
            "play color live", "color live", "special stage",
            // ── 비하인드/스포일러/예고/요약 ──
            "behind the scene", "behind the scenes", "behind-the-scene", "b-side film",
            "spoiler", "스포일러", "album spoiler", "recap", "리캡", "highlight medley", "하이라이트 메들리",
            "mv preview", "album preview", "comeback preview", "프리뷰 영상", "sneak peek", "coming soon",
            // ── 인터뷰/토크/Q&A/다큐 ──
            "interview", "questions with", "50 questions", "20 questions",
            "docuseries", "docu-series", "the making of",
            // ── 안무/댄스/커버 실연 ──
            "performance video", "dance performance", "안무 영상", "안무영상", "안무 시안",
            "시안 비교", "비교영상", "비교 영상", "comparison", "랜덤 플레이 댄스", "random play dance",
            "random dance", "랜덤플레이댄스", "play dance", "full performance", "performs ",
            "perform \"", "covered by", "커버 by",
            // ── 매쉬업/틱톡/편집/인스트 ──
            "mashup", "mash-up", "매쉬업", "tiktok", "틱톡", "tik tok", "(inst", " inst.",
            "인스트", "instrumental ver", "acapella", "아카펠라", "8d audio", "sped up", "slowed",
            "nightcore", "reverb", "id clip", "최종화",
            // ── 자막 리업로드(비공식) ──
            "vietsub", "sub indo", "legendado", "eng sub", "sub esp", "sub español",
            "türkçe çeviri", "terjemahan", "русский перевод", "가사 번역",
            // ── 다이제스트/특전영상/영화(일본) ──
            "ダイジェスト", "特別映像", "特典映像", "特別 映像", "映画「", "special video",
            "official dance video", " - live ", "제작기", "메이킹 필름", "making film",
            // ── 직캠/팬캠/음악방송 풀캠 (그 아티스트 원곡 아님) ──
            "직캠", "fancam", "fan cam", "팬캠", "풀캠", "full cam", "세로캠", "세로직캠",
            "포커스캠", "focus cam", "focus)", "1인샷", "음중", "음방", "음악중심", "쇼음악중심",
            "the k-pop", "kpop on", "kbs kpop", "sbs inkigayo", "mbc kpop",
            // ── 커버/다른 버전 (원곡 아님) ──
            "트로트버전", "트로트 버전", "trot ver", "trot version", "발라드버전", "발라드 버전",
            "댄스버전", "댄스 버전", "rock ver", "acoustic ver", "피아노버전", "piano ver",
            "리메이크", "remake", "불러봤", "부르기", "노래방 ver", "커버 곡", "cover song",
            "cover)", "따라불러",
            "jazz ver", "band ver", "orchestra ver", "strings ver", "orchestral ver",
            "shuffle version", "reggae version", "ska reggae", "sped-up", "(remix", "(리믹스",
            " remix)", "리믹스)", "sm jazz", "sm station", "sm classics", "jazz trio", "big band",
            // ── 리액션/현장/인사/축제/버라이어티 (원곡 아님) ──
            "반응", "reaction", "리액션", "감상하는", "감상 하는", "우왁굳", "우왁굳님",
            "오픈 인사", "오픈인사", "인사 영상", "인사영상", "지니램프", "지니 램프",
            "축제", "대학축제", "대학 축제", "동국대", "축제 무대", "페스티벌 직캠",
            "풀버전", "[풀버전]", "풀 버전", "(풀버전", "full ver.", "full version",
            "| show", "쇼! 음악중심", "쇼 음악중심", "musiccore", "music core", "인기가요",
            "엠카운트다운", "m countdown", "더 쇼 무대", "쇼챔피언", "쇼! 챔피언",
            // ── 듀엣/합창/데뷔인사/세션 (원곡 아님) ──
            "불법 듀엣", "불법듀엣", "듀엣", "duet", "합창", "같이 부른", "같이불러",
            "데뷔 인사", "데뷔인사", "another session", "special version", "스페셜 버전",
            "방송]", " 방송분", "불후의 명곡", "immortal songs", "올댓뮤직", "all that music",
            "studio choom", "스튜디오 춤", "온더스팟", "the first take",
            "박소현의 러브게임", "album sampler", "앨범 샘플러"
    };

    // 방송사/공연 채널의 6자리 방송일자 표기 (예: "MBC260321방송", "MBC 201226 방송")
    private static final Pattern BROADCAST_DATE = Pattern.compile("(?i)(MBC|KBS|SBS|Mnet|MBN|JTBC)\\s?\\d{6}");

    // 비공식 리업로드/팬 채널 + 방송사 공연·직캠 채널 ("- Topic" 은 공식이므로 제외)
    private static final String[] NON_MUSIC_CHANNEL_HINTS = {
            "lyrics", "lyric video", " amv", "compilation", "vietsub", "sub español", "sub indo",
            "legendado", "karaoke", "cover nation", "nightcore", "sped up", "slowed", "8d ",
            "reaction", "리액션", "clips", "tributo", "fan cam", "팬캠", "노래모음", "playlist",
            // ── 방송사 K-POP / 라디오 / 댄스퍼포먼스 채널 (원곡 아님) ──
            "kbs kpop", "kbs k-pop", "kbs 레전드", "레전드 케이팝", "mbckpop", "mbc kpop",
            "sbs kpop", "sbs k-pop", "sbs radio", "에라오", "studio choom", "스튜디오 춤",
            "1thek originals", "원더케이 오리지널", "the first take", "it's live", "잇츠라이브",
            "dingo music", "딩고 뮤직", "불후의 명곡", "kbs world tv"
    };

    public boolean isNonMusicChannel(String channelTitle) {
        if (channelTitle == null) return false;
        String c = channelTitle.toLowerCase();
        if (c.endsWith("- topic")) return false; // 유튜브 자동 생성 = 공식 음원
        for (String h : NON_MUSIC_CHANNEL_HINTS) if (c.contains(h)) return true;
        return false;
    }

    // "아이네 X 릴파 - 괴수의 꽃노래" 처럼 [이름] X [이름] - [곡] 형태의 합작 커버
    private static final Pattern COLLAB_COVER = Pattern.compile("^\\s*\\S.{0,24}?\\s[Xx×]\\s.{0,24}?\\s[-–—]\\s\\S");
    private static final String[] OFFICIAL_MARKERS = {
            "official", "officiel", "oficial", "officiell", "m/v", "mv)", "[mv]", "[m/v]",
            "audio", "visualizer", "lyric", "공식", "官方", "官方", "オフィシャル"
    };

    /**
     * 채널이 원작자가 아닌데 제목이 "A x B - 곡" 형태이고 공식 표기도 없으면 합작 커버로 본다.
     * (공식 피처링 곡 "IU - Palette (Feat. G-DRAGON)" 등은 " - " 앞이 단일 아티스트라 걸리지 않음)
     */
    public boolean looksLikeCollabCover(String title, String channelTitle) {
        if (title == null) return false;
        String lower = title.toLowerCase();
        for (String mk : OFFICIAL_MARKERS) if (lower.contains(mk)) return false;
        if (!COLLAB_COVER.matcher(title).find()) return false;
        // 채널명이 제목에 등장하면 그 아티스트 본인 채널일 가능성 → 유지
        if (channelTitle != null && !channelTitle.isBlank()) {
            String ch = channelTitle.toLowerCase().replace(" - topic", "").trim();
            if (ch.length() >= 2 && lower.contains(ch)) return false;
        }
        return true;
    }

    /** 제목/아티스트만으로 장르 판별 (언어 정보·지역 힌트 없음) */
    public String determineGenre(String title, String artist) {
        return resolveGenre(title, artist, null, null);
    }

    /**
     * 💡 유튜브 뮤직식 카테고리 판별.
     *    우선순위: (1) 버튜버 마커 → (2) 음원 언어(defaultAudioLanguage) → (3) 제목/채널 문자(한글·가나)
     *             → (4) 지역 차트 힌트 → (5) 기본 POP
     *    "- Topic" 자동생성 채널 접미사는 판별 전에 제거한다.
     *
     * @param audioLang  snippet.defaultAudioLanguage / defaultLanguage (예: "ko", "ja", "en-US") - null 가능
     * @param regionHint 지역 인기차트 출처 힌트 ("KR"/"JP"/"US") - null 가능
     */
    public String resolveGenre(String title, String artist, String audioLang, String regionHint) {
        String rawT = title != null ? title : "";
        String rawA = artist != null ? artist : "";
        // 자동생성 채널 접미사 제거 ("BIGBANG - Topic" → "BIGBANG")
        String cleanA = rawA.replaceAll("(?i)\\s*-\\s*topic\\s*$", "");
        String t = rawT.toLowerCase();
        String a = cleanA.toLowerCase();
        String lang = audioLang != null ? audioLang.toLowerCase() : "";

        // (1) 버튜버 우선
        for (String marker : VTUBER_MARKERS) {
            if (t.contains(marker) || a.contains(marker)) return "VTUBER";
        }

        // (2) 음원 언어
        if (lang.startsWith("ko")) return "KPOP";
        if (lang.startsWith("ja")) return "JPOP";

        // (3) 문자(스크립트) 기반
        boolean hasHangul = HANGUL_PATTERN.matcher(rawT).find() || HANGUL_PATTERN.matcher(cleanA).find();
        boolean hasKana = KANA_PATTERN.matcher(rawT).find() || KANA_PATTERN.matcher(cleanA).find();
        if (hasHangul && !hasKana) return "KPOP";
        if (hasKana && !hasHangul) return "JPOP";
        if (hasHangul && hasKana) return "KPOP"; // 한글이 있으면 한국 음원(일본어 부제)일 확률이 높음

        // (3-b) 로마자 표기 주요 아티스트 매핑 (Topic 채널 등)
        for (Map.Entry<String, String> e : KNOWN_ARTIST_GENRE.entrySet()) {
            String key = e.getKey();
            if (a.equals(key) || a.startsWith(key + " ") || a.startsWith(key + "-")
                    || a.startsWith(key + ",") || a.startsWith(key + " x ")
                    || t.startsWith(key + " -") || t.startsWith(key + " –") || t.startsWith(key + ": ")) {
                return e.getValue();
            }
        }

        // (3-c) 공식 레이블/유통 채널명으로 판별 (예: "HYBE LABELS", "SMTOWN")
        String rawALower = rawA.toLowerCase();
        for (Map.Entry<String, String> e : LABEL_CHANNEL_GENRE.entrySet()) {
            if (rawALower.contains(e.getKey())) return e.getValue();
        }

        // (4) 위 신호(문자/아티스트/레이블)가 하나도 없으면 KPOP/JPOP 로 넣지 않는다.
        //     "KR 차트에 있으니 KPOP" 같은 추측은 서양 팝을 K-POP 에 섞어 넣으므로 제거.
        //     regionHint 는 참고만 하고, 확실한 로컬 신호가 없으면 POP 로 분류.
        return "POP";
    }

    /**
     * 💡 제목만으로 "음악이 아닌 영상"을 판별한다. (쇼츠/토크/플레이리스트/커버 등)
     */
    // 제목 맨 앞의 날짜 표기: "260902", "20230218", "[2023/2/18]", "23.02.18", "2024-01-05" 등
    private static final Pattern DATE_PREFIX = Pattern.compile(
            "^\\s*[\\[(]?\\s*("
            + "(19|20)?\\d{2}[.\\-/]\\d{1,2}[.\\-/]\\d{1,2}"   // 구분자 있는 날짜 (2023/2/18)
            + "|(19|20|2[1-9])\\d{4}"                            // 붙여쓴 날짜 (260902 / 20230218)
            + ")\\b");

    public boolean isNonMusicTitle(String title) {
        if (title == null) return true;
        String lower = title.toLowerCase();
        for (String keyword : NON_MUSIC_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        // 제목이 6자리 날짜(YYMMDD / YYYYMM)로 시작 → 직캠/현장 영상 관행
        if (DATE_PREFIX.matcher(title).find()) return true;
        // "MBC260321방송" 처럼 방송사 + 방송일자 → 음악방송/공연 영상
        if (BROADCAST_DATE.matcher(title).find()) return true;
        // 해시태그 3개 이상 → 태그 스팸/쇼츠성 업로드로 간주
        if (title.chars().filter(c -> c == '#').count() >= 3) {
            return true;
        }
        // 【アニメ】/[anime] 촌극 시리즈(홀로라이브 등) - "MV/song/오리지널곡" 표기가 없으면 음악 아님
        boolean animeSkit = (title.contains("【アニメ】") || title.contains("【anime】") || lower.contains("[anime]"))
                && !(lower.contains("mv") || lower.contains("song") || lower.contains("music")
                     || title.contains("オリジナル曲") || title.contains("楽曲") || lower.contains("original"));
        if (animeSkit) return true;
        return false;
    }

    /**
     * 💡 재생 시간이 단곡 음악 범위(90초 ~ 420초)인지 확인한다.
     *    0 이하(정보 없음)이면 신뢰할 수 없으므로 음악이 아닌 것으로 간주한다.
     */
    public boolean isValidSongDuration(long seconds) {
        return seconds >= MIN_SONG_SECONDS && seconds <= MAX_SONG_SECONDS;
    }

    private long parseYouTubeDuration(String isoDuration) {
        try {
            Duration duration = Duration.parse(isoDuration);
            return duration.getSeconds();
        } catch (Exception e) {
            return 0L;
        }
    }

    @Transactional
    public YouTubeVideoDto getVideoInfo(String videoId) {
        return getVideoInfo(videoId, false);
    }

    /**
     * @param strict true 면 "진짜 음원(곡)"만 통과시키는 강한 게이트 적용
     *               (키워드 검색으로 들어온 결과처럼 비음악이 섞이기 쉬운 경로에서 사용)
     */
    @Transactional
    public YouTubeVideoDto getVideoInfo(String videoId, boolean strict) {
        Optional<Music> cachedMusic = musicRepository.findByYoutubeVideoId(videoId);
        if (cachedMusic.isPresent()) {
            Music music = cachedMusic.get();
            String properGenre = determineGenre(music.getTitle(), music.getArtist());
            if (!properGenre.equals(music.getGenre())) {
                music.update(music.getTitle(), music.getArtist(), music.getThumbnailUrl(), properGenre);
            }
            return toDto(music);
        }

        try {
            String url = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=snippet,contentDetails,status,statistics,topicDetails"
                    + "&id=" + videoId
                    + "&key=" + currentApiKey();

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.get("items");

            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("YouTube 영상을 찾을 수 없습니다: " + videoId);
            }

            Music savedMusic = upsertFromVideoItem(items.get(0), null, strict)
                    .orElseThrow(() -> new IllegalArgumentException("단곡 음악 조건을 만족하지 않는 영상입니다: " + videoId));
            return toDto(savedMusic);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("YouTube API 호출 중 오류 발생", e);
            throw new RuntimeException("YouTube API 연동 실패: " + e.getMessage());
        }
    }

    /**
     * 💡 [관리자 수동 등록 전용] 단곡 자동필터(재생시간 90~480초·비음악 키워드·임베드 가능·카테고리)를
     *    전부 건너뛰고 유튜브 메타데이터만 가져와 저장한다. 관리자가 직접 고른 영상이므로 신뢰한다.
     *    - 영상이 실제로 존재하지 않으면 IllegalArgumentException (→ 400)
     *    - 모든 API 키 할당량 소진 시 QuotaExceededException (→ 429 로 매핑)
     */
    @Transactional
    public YouTubeVideoDto getVideoInfoLenient(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            throw new IllegalArgumentException("YouTube 동영상 ID가 비어 있습니다.");
        }

        Optional<Music> cachedMusic = musicRepository.findByYoutubeVideoId(videoId);
        if (cachedMusic.isPresent()) {
            Music music = cachedMusic.get();
            music.markManualAdd(); // 관리자가 명시적으로 등록 → 자동 정리(삭제) 대상에서 제외
            String properGenre = determineGenre(music.getTitle(), music.getArtist());
            if (!properGenre.equals(music.getGenre())) {
                music.update(music.getTitle(), music.getArtist(), music.getThumbnailUrl(), properGenre);
            }
            return toDto(music);
        }

        JsonNode item = null;
        for (int attempt = 0; attempt < Math.max(1, apiKeys.size()); attempt++) {
            String key = currentApiKey(); // 전부 소진 시 QuotaExceededException
            try {
                String url = "https://www.googleapis.com/youtube/v3/videos"
                        + "?part=snippet,contentDetails,status,statistics"
                        + "&id=" + URLEncoder.encode(videoId, StandardCharsets.UTF_8)
                        + "&key=" + key;
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode root = objectMapper.readTree(response.body());

                if (root.has("error")) {
                    int status = response.statusCode();
                    String reason = root.path("error").path("errors").path(0).path("reason").asText("");
                    if (isQuotaError(status, reason)) {
                        markKeyExhausted(key);
                        continue; // 다음 키로 재시도
                    }
                    throw new IllegalArgumentException(
                            "YouTube API 오류: " + root.path("error").path("message").asText(reason));
                }

                JsonNode items = root.get("items");
                if (items == null || items.isEmpty()) {
                    throw new IllegalArgumentException("YouTube 영상을 찾을 수 없습니다 (비공개/삭제/잘못된 ID): " + videoId);
                }
                item = items.get(0);
                break;
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.error("YouTube API 호출 중 오류 발생 (lenient, videoId={})", videoId, e);
                throw new RuntimeException("YouTube API 연동 실패: " + e.getMessage());
            }
        }

        if (item == null) {
            throw new QuotaExceededException("모든 YouTube API 키의 할당량이 소진되었습니다. 잠시 후 다시 시도해 주세요.");
        }

        JsonNode snippet = item.get("snippet");
        if (snippet == null || snippet.path("title").asText("").isEmpty()) {
            throw new IllegalArgumentException("YouTube 영상 정보를 읽을 수 없습니다: " + videoId);
        }
        JsonNode contentDetails = item.get("contentDetails");

        String title = snippet.path("title").asText("");
        String artist = snippet.path("channelTitle").asText("");
        String audioLang = snippet.path("defaultAudioLanguage").asText(
                snippet.path("defaultLanguage").asText(""));
        long seconds = (contentDetails != null && contentDetails.has("duration"))
                ? parseYouTubeDuration(contentDetails.get("duration").asText())
                : 0L;
        LocalDateTime publishedAt = parsePublishedAt(snippet.path("publishedAt").asText(""));

        Long viewCount = null;
        JsonNode stats = item.get("statistics");
        if (stats != null && stats.has("viewCount")) {
            try { viewCount = Long.parseLong(stats.get("viewCount").asText("0")); } catch (Exception ignore) {}
        }

        JsonNode thumbnails = snippet.get("thumbnails");
        String thumbnailUrl = "";
        if (thumbnails != null) {
            if (thumbnails.has("high")) thumbnailUrl = thumbnails.get("high").path("url").asText("");
            else if (thumbnails.has("medium")) thumbnailUrl = thumbnails.get("medium").path("url").asText("");
            else if (thumbnails.has("default")) thumbnailUrl = thumbnails.get("default").path("url").asText("");
        }

        final String fTitle = title;
        final String fArtist = artist;
        final String fThumb = thumbnailUrl;
        final String fGenre = resolveGenre(title, artist, audioLang, null);
        final Long fSeconds = seconds > 0 ? seconds : null;
        final LocalDateTime fPublished = publishedAt;
        final Long fViews = viewCount;

        Music music = musicRepository.findByYoutubeVideoId(videoId)
                .map(m -> {
                    m.update(fTitle, fArtist, fThumb, fGenre);
                    m.markManualAdd();
                    if (fSeconds != null) m.updateDuration(fSeconds);
                    if (fPublished != null) m.updatePublishedAt(fPublished);
                    if (fViews != null) m.updateViewCount(fViews);
                    return m;
                })
                .orElseGet(() -> musicRepository.save(Music.builder()
                        .youtubeVideoId(videoId)
                        .title(fTitle)
                        .artist(fArtist)
                        .thumbnailUrl(fThumb)
                        .genre(fGenre)
                        .durationSeconds(fSeconds)
                        .publishedAt(fPublished)
                        .viewCount(fViews)
                        .manualAdd(true)
                        .build()));
        return toDto(music);
    }

    /** 이 영상이 "진짜 곡(음원/공식 MV)"으로 볼 만한 신호가 있는가 (strict 게이트용) */
    private boolean looksLikeRealSong(JsonNode item, String title, String artist) {
        String a = artist == null ? "" : artist.toLowerCase();
        // 1) "<아티스트> - Topic" 채널 = 유튜브가 공식 유통 음원을 자동 업로드하는 채널 → 항상 곡
        if (a.matches(".*\\s-\\s*topic\\s*$")) return true;

        // 2) topicDetails 에 음악 주제(위키 URL) 가 있으면 음악 영상
        //    music / *_music(장르) / *-pop(k-pop,j-pop..) / hip hop / song
        JsonNode topics = item.path("topicDetails").path("topicCategories");
        if (topics.isArray()) {
            for (JsonNode t : topics) {
                String u = t.asText("").toLowerCase();
                if (u.contains("music") || u.contains("-pop") || u.contains("hip_hop")
                        || u.contains("hip-hop") || u.endsWith("/wiki/song")) return true;
            }
        }

        // 3) 제목에 명확한 음원/MV 마커
        String lt = title == null ? "" : title.toLowerCase();
        String[] markers = {"m/v", "official m/v", "mv)", "(mv", " mv ", "music video", "official video",
                "official audio", "audio)", "(audio", "lyric video", "オリジナル", "오리지널", "원곡",
                "official mv", "prod.", "feat.", "ft.", " x ", "vevo"};
        for (String m : markers) if (lt.contains(m)) return true;
        if (lt.endsWith(" mv") || lt.endsWith("(official)")) return true;

        // 4) 채널이 VEVO 면 곡
        if (a.contains("vevo")) return true;

        return false;
    }

    private LocalDateTime parsePublishedAt(String iso) {
        try {
            if (iso == null || iso.isEmpty()) return null;
            return OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 💡 videos 엔드포인트가 돌려준 item(JSON) 하나를 "단곡 음악" 게이트에 통과시키고
     *    통과하면 DB에 저장/갱신한다. 통과 못 하면 Optional.empty().
     *    게이트: 임베드 가능 · 비음악 카테고리/제목 아님 · 재생시간 90~480초
     *    ("최신곡만" 노출은 프론트 카테고리 섹션에서 publishedAt 으로 거른다 - 여기선 저장만)
     *    (part=snippet,contentDetails[,status] 필요)
     *
     * @param regionHint 지역 인기차트 출처 힌트("KR"/"JP"/"US"), 장르 판별 4순위. null 가능
     */
    private Optional<Music> upsertFromVideoItem(JsonNode item, String regionHint) {
        return upsertFromVideoItem(item, regionHint, false);
    }

    private Optional<Music> upsertFromVideoItem(JsonNode item, String regionHint, boolean strict) {
        JsonNode snippet = item.get("snippet");
        JsonNode contentDetails = item.get("contentDetails");
        JsonNode status = item.get("status");
        if (snippet == null) return Optional.empty();

        String videoId = item.path("id").isTextual()
                ? item.get("id").asText()
                : item.path("id").path("videoId").asText("");
        if (videoId.isEmpty()) return Optional.empty();

        if (status != null && status.has("embeddable") && !status.get("embeddable").asBoolean(true)) {
            return Optional.empty();
        }

        String title = snippet.path("title").asText("");
        String artist = snippet.path("channelTitle").asText("");
        String categoryId = snippet.path("categoryId").asText("");
        String audioLang = snippet.path("defaultAudioLanguage").asText(
                snippet.path("defaultLanguage").asText(""));

        if (title.isEmpty() || BLOCKED_CATEGORY_IDS.contains(categoryId)
                || isNonMusicTitle(title) || isNonMusicChannel(artist)
                || looksLikeCollabCover(title, artist)) {
            return Optional.empty();
        }
        if (contentDetails == null || !contentDetails.has("duration")) {
            return Optional.empty();
        }
        long seconds = parseYouTubeDuration(contentDetails.get("duration").asText());
        if (!isValidSongDuration(seconds)) {
            return Optional.empty();
        }

        if (strict) {
            // 키워드 검색 경로: 유튜브 "음악(10)" 카테고리만 저장 (인터뷰·엔터·블로그 등 컷)
            if (!"10".equals(categoryId)) return Optional.empty();
        }

        LocalDateTime publishedAt = parsePublishedAt(snippet.path("publishedAt").asText(""));

        Long viewCount = null;
        JsonNode stats = item.get("statistics");
        if (stats != null && stats.has("viewCount")) {
            try { viewCount = Long.parseLong(stats.get("viewCount").asText("0")); } catch (Exception ignore) {}
        }

        JsonNode thumbnails = snippet.get("thumbnails");
        String thumbnailUrl = "";
        if (thumbnails != null) {
            if (thumbnails.has("high")) thumbnailUrl = thumbnails.get("high").path("url").asText("");
            else if (thumbnails.has("medium")) thumbnailUrl = thumbnails.get("medium").path("url").asText("");
            else if (thumbnails.has("default")) thumbnailUrl = thumbnails.get("default").path("url").asText("");
        }

        String genre = resolveGenre(title, artist, audioLang, regionHint);

        final String fThumb = thumbnailUrl;
        final long fSeconds = seconds;
        final String fGenre = genre;
        final LocalDateTime fPublished = publishedAt;
        final Long fViews = viewCount;
        Music music = musicRepository.findByYoutubeVideoId(videoId)
                .map(m -> {
                    m.update(title, artist, fThumb, fGenre);
                    m.updateDuration(fSeconds);
                    if (fPublished != null) m.updatePublishedAt(fPublished);
                    if (fViews != null) m.updateViewCount(fViews);
                    return m;
                })
                // 다른 videoId 로 올라온 "같은 곡"(레이블/업로더만 다름)이 이미 있으면, 새로
                // 중복 행을 만들지 않고 그 기존 곡의 조회수만 최신화한다. (관리자가 지워도
                // 다음 동기화 때 다시 들어오던 문제의 근본 원인 — 정리는 사후 조치일 뿐이었음)
                .or(() -> findExistingSameRecording(title, fSeconds).map(m -> {
                    if (fViews != null && (m.getViewCount() == null || fViews > m.getViewCount())) {
                        m.updateViewCount(fViews);
                    }
                    return m;
                }))
                .orElseGet(() -> musicRepository.save(Music.builder()
                        .youtubeVideoId(videoId)
                        .title(title)
                        .artist(artist)
                        .thumbnailUrl(fThumb)
                        .genre(fGenre)
                        .durationSeconds(fSeconds)
                        .publishedAt(fPublished)
                        .viewCount(fViews)
                        .build()));
        return Optional.of(music);
    }

    /** 제목(정규화) + 재생시간(오차 허용)으로 이미 카탈로그에 있는 "같은 녹음"을 찾는다. */
    private Optional<Music> findExistingSameRecording(String title, long seconds) {
        if (seconds <= 0) return Optional.empty();
        String normTitle = normalizeTitleForDedupe(title);
        if (normTitle.isEmpty()) return Optional.empty();
        List<Music> candidates = musicRepository.findByDurationSecondsBetween(
                seconds - SAME_RECORDING_DURATION_TOLERANCE_SEC,
                seconds + SAME_RECORDING_DURATION_TOLERANCE_SEC);
        return candidates.stream()
                .filter(m -> normTitle.equals(normalizeTitleForDedupe(m.getTitle())))
                .findFirst();
    }

    /**
     * 💡 지역별 인기 음악 차트 동기화 (videos.list?chart=mostPopular).
     *    search(100유닛)와 달리 호출당 1유닛이라 매우 저렴 → 서버 기동 시마다 최신곡 갱신 가능.
     *    장르는 곡 자체(언어/문자)로 판별하고, 판별 불가 시 지역 힌트(KR→KPOP, JP→JPOP, US→POP)를 쓴다.
     *    업로드일이 maxAgeDays 를 넘은 곡은 저장하지 않는다("최신곡만").
     */
    @Transactional
    public Map<String, Integer> syncTrendingMusic() {
        // videos.list?chart=mostPopular 는 호출당 ~9유닛으로 저렴 → 여러 지역에서 인기곡을 폭넓게 확보.
        // KR/JP 는 K-POP/J-POP, 나머지는 POP·글로벌곡 위주로 들어온다. (장르는 곡 자체로 판별)
        List<String> regions = List.of("KR", "JP", "US", "GB", "CA", "AU", "DE", "FR", "BR", "MX", "ID", "IN", "TH", "VN", "PH");
        Map<String, Integer> result = new LinkedHashMap<>();
        List<Music> newlyAdded = new ArrayList<>();
        // 이번 동기화에서 차트에 오른 곡 → 순위 (지역별 순위 중 가장 높은 값 유지)
        java.util.LinkedHashMap<Music, Integer> chartRanks = new java.util.LinkedHashMap<>();

        for (String region : regions) {
            int saved = 0;
            try {
                String url = "https://www.googleapis.com/youtube/v3/videos"
                        + "?part=snippet,contentDetails,status,statistics"
                        + "&chart=mostPopular"
                        + "&videoCategoryId=10"
                        + "&regionCode=" + region
                        + "&maxResults=50"
                        + "&key=" + currentApiKey();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode root = objectMapper.readTree(response.body());

                if (root.has("error")) {
                    log.warn("인기 음악 차트 오류 (region={}): {}", region, response.body());
                } else {
                    int pos = 0;
                    for (JsonNode item : root.path("items")) {
                        pos++;
                        String vid = item.path("id").asText("");
                        boolean existed = !vid.isEmpty() && musicRepository.existsByYoutubeVideoId(vid);
                        Optional<Music> saved0 = upsertFromVideoItem(item, region);
                        if (saved0.isPresent()) {
                            saved++;
                            Music m = saved0.get();
                            chartRanks.merge(m, pos, Math::min);
                            if (!existed) newlyAdded.add(m);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("인기 음악 차트 동기화 실패 (region=" + region + ")", e);
            }
            result.put(region, saved);
            log.info("🔥 [{}] 인기 음악 차트 {}건 동기화", region, saved);
        }

        // 차트 결과가 충분하면(부분 실패 방지) 이전 순위를 비우고 이번 순위로 갱신
        if (chartRanks.size() >= 20) {
            // 1) 이전 순위 제거 (관리 엔티티에서 직접 null 처리 → 확실히 flush 됨)
            for (Music m : musicRepository.findByTrendingRankIsNotNull()) {
                m.updateTrendingRank(null);
            }
            // 2) 이번 차트 순위 반영
            chartRanks.forEach((m, r) -> m.updateTrendingRank(r));
            musicRepository.flush();
            log.info("📊 [스케줄러] 트렌딩 순위 {}곡 갱신", chartRanks.size());
        } else {
            log.warn("📊 트렌딩 결과 부족({}건) - 기존 순위 유지", chartRanks.size());
        }

        // 신규로 차트에 진입한 인기곡이 있으면 앱 내 알림 발행
        if (!newlyAdded.isEmpty()) {
            Music head = newlyAdded.get(0);
            String title = newlyAdded.size() == 1
                    ? "새 인기곡: " + head.getTitle()
                    : "새 인기곡 " + newlyAdded.size() + "곡이 차트에 진입했어요";
            String msg = newlyAdded.size() == 1
                    ? head.getArtist()
                    : head.getTitle() + " 외 " + (newlyAdded.size() - 1) + "곡";
            try {
                notificationService.publish("NEW_HOT_SONG", title, msg, "/charts");
            } catch (Exception e) {
                log.warn("새 인기곡 알림 발행 실패: {}", e.getMessage());
            }
        }

        return result;
    }

    private YouTubeVideoDto toDto(Music music) {
        return YouTubeVideoDto.builder()
                .id(music.getId())
                .youtubeVideoId(music.getYoutubeVideoId())
                .title(music.getTitle())
                .artist(music.getArtist())
                .thumbnailUrl(music.getThumbnailUrl())
                .durationSeconds(music.getDurationSeconds())
                .build();
    }

    @Transactional
    public List<YouTubeVideoDto> syncPlaylist(String playlistId) {
        return syncPlaylist(playlistId, Integer.MAX_VALUE);
    }

    /**
     * 💡 유튜브 핸들(@THEFIRSTTAKE 등)로 채널의 "업로드" 재생목록을 찾아 동기화한다.
     *    channels.list?forHandle = ~3유닛으로 저렴. 채널ID를 몰라도 핸들만 알면 됨.
     */
    @Transactional
    public List<YouTubeVideoDto> syncChannelByHandle(String handle, int maxItems) {
        String h = handle.startsWith("@") ? handle : "@" + handle;
        try {
            String url = "https://www.googleapis.com/youtube/v3/channels"
                    + "?part=contentDetails"
                    + "&forHandle=" + URLEncoder.encode(h, StandardCharsets.UTF_8)
                    + "&key=" + currentApiKey();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(res.body());
            if (root.has("error")) {
                log.warn("채널 핸들 조회 오류 ({}): {}", h, res.body());
                return new ArrayList<>();
            }
            JsonNode items = root.path("items");
            if (items.isEmpty()) {
                log.warn("채널 핸들을 찾을 수 없음: {}", h);
                return new ArrayList<>();
            }
            String uploads = items.get(0).path("contentDetails").path("relatedPlaylists").path("uploads").asText("");
            if (uploads.isEmpty()) {
                log.warn("채널 업로드 재생목록 없음: {}", h);
                return new ArrayList<>();
            }
            return syncPlaylist(uploads, maxItems);
        } catch (Exception e) {
            log.warn("채널({}) 동기화 실패: {}", h, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * @param maxItems 최대 처리할 플레이리스트 항목 수 (채널 업로드 재생목록처럼 매우 긴 목록 방어)
     */
    @Transactional
    public List<YouTubeVideoDto> syncPlaylist(String playlistId, int maxItems) {
        List<YouTubeVideoDto> syncedVideos = new ArrayList<>();
        String nextPageToken = null;
        int processed = 0;

        try {
            do {
                String url = "https://www.googleapis.com/youtube/v3/playlistItems"
                        + "?part=snippet"
                        + "&maxResults=50"
                        + "&playlistId=" + playlistId
                        + "&key=" + currentApiKey();

                if (nextPageToken != null && !nextPageToken.isEmpty()) {
                    url += "&pageToken=" + nextPageToken;
                }

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = root.get("items");

                if (items != null && items.isArray()) {
                    for (JsonNode item : items) {
                        JsonNode snippet = item.get("snippet");
                        if (snippet != null && snippet.has("resourceId")) {
                            JsonNode resourceId = snippet.get("resourceId");
                            if (resourceId.has("videoId")) {
                                String videoId = resourceId.get("videoId").asText();
                                processed++;
                                try {
                                    syncedVideos.add(getVideoInfo(videoId));
                                } catch (Exception e) {
                                    log.debug("플레이리스트 항목 스킵: {} ({})", videoId, e.getMessage());
                                }
                            }
                        }
                    }
                }

                JsonNode nextTokenNode = root.get("nextPageToken");
                nextPageToken = (nextTokenNode != null) ? nextTokenNode.asText() : null;

            } while (nextPageToken != null && !nextPageToken.isEmpty() && processed < maxItems);
        } catch (Exception e) {
            log.error("YouTube 플레이리스트 연동 실패", e);
        }

        return syncedVideos;
    }

    /**
     * 💡 기존 음원의 메타데이터(재생시간·업로드일·장르)를 videos.list 배치 호출(50건/1유닛)로 보정한다.
     *    삭제 조건: 영상 소멸 · 쇼츠/장편(90~480초 밖) · 비음악 제목
     *    (오래된 곡은 삭제하지 않는다 - "실시간 인기 급상승" 풀 유지. 최신곡 노출은 프론트 카테고리에서 처리)
     *    search API 와 달리 매우 저렴하므로 서버 기동 시마다 호출해도 안전하다.
     */
    @Transactional
    public Map<String, Integer> backfillAndPruneMetadata() {
        List<Music> targets = musicRepository.findByDurationSecondsIsNullOrPublishedAtIsNullOrViewCountIsNull();
        int updated = 0, deleted = 0;

        for (int i = 0; i < targets.size(); i += 50) {
            List<Music> batch = targets.subList(i, Math.min(i + 50, targets.size()));
            StringBuilder ids = new StringBuilder();
            for (Music m : batch) {
                if (ids.length() > 0) ids.append(',');
                ids.append(m.getYoutubeVideoId());
            }

            try {
                String url = "https://www.googleapis.com/youtube/v3/videos"
                        + "?part=contentDetails,snippet,statistics"
                        + "&id=" + ids
                        + "&key=" + currentApiKey();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode root = objectMapper.readTree(response.body());

                if (root.has("error")) {
                    log.warn("메타데이터 백필 오류: {}", response.body());
                    break;
                }

                Map<String, JsonNode> itemById = new java.util.HashMap<>();
                for (JsonNode item : root.path("items")) {
                    itemById.put(item.path("id").asText(), item);
                }

                for (Music m : batch) {
                    JsonNode item = itemById.get(m.getYoutubeVideoId());
                    if (item == null) {   // 영상이 삭제/비공개됨
                        musicRemover.remove(m);
                        deleted++;
                        continue;
                    }
                    JsonNode snippet = item.path("snippet");
                    long seconds = parseYouTubeDuration(item.path("contentDetails").path("duration").asText(""));
                    LocalDateTime publishedAt = parsePublishedAt(snippet.path("publishedAt").asText(""));
                    String audioLang = snippet.path("defaultAudioLanguage").asText(
                            snippet.path("defaultLanguage").asText(""));
                    Long views = null;
                    try {
                        String v = item.path("statistics").path("viewCount").asText("");
                        if (!v.isEmpty()) views = Long.parseLong(v);
                    } catch (Exception ignore) {}

                    boolean failsSongGate = seconds == 0L || isNonMusicTitle(m.getTitle()) || !isValidSongDuration(seconds);
                    if (failsSongGate && !Boolean.TRUE.equals(m.getManualAdd())) {
                        musicRemover.remove(m);
                        deleted++;
                    } else {
                        if (seconds > 0L) m.updateDuration(seconds);
                        if (publishedAt != null) m.updatePublishedAt(publishedAt);
                        if (views != null) m.updateViewCount(views);
                        String g = resolveGenre(m.getTitle(), m.getArtist(), audioLang, null);
                        // POP(판별 실패 기본값)으로는 기존 KPOP/JPOP/VTUBER 를 덮지 않는다
                        if (!"POP".equals(g) && !g.equals(m.getGenre())) {
                            m.update(m.getTitle(), m.getArtist(), m.getThumbnailUrl(), g);
                        }
                        updated++;
                    }
                }
            } catch (Exception e) {
                log.error("메타데이터 백필 배치 실패", e);
            }
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("updated", updated);
        result.put("deleted", deleted);
        log.info("🕒 메타데이터 백필 완료: 갱신 {}건 / 삭제 {}건", updated, deleted);
        return result;
    }

    /**
     * 카탈로그 정리용 판정 — 보수적. 명백한 비음악만 삭제하고, 유튜브 "음악" 카테고리는 신뢰한다.
     * 삭제 대상: 비음악 제목 키워드 / 차단 카테고리(게임·뉴스·스포츠 등) / 길이 벗어남(정보 있을 때만)
     *          / 음악 외 카테고리인데 곡 신호도 없음
     */
    private boolean keepsAsSong(JsonNode item) {
        JsonNode snippet = item.path("snippet");
        String title = snippet.path("title").asText("");
        String artist = snippet.path("channelTitle").asText("");
        String categoryId = snippet.path("categoryId").asText("");
        if (title.isEmpty()) return false;
        if (isNonMusicTitle(title)) return false;
        if (isNonMusicChannel(artist)) return false;
        if (looksLikeCollabCover(title, artist)) return false;
        if (BLOCKED_CATEGORY_IDS.contains(categoryId)) return false;

        long seconds = parseYouTubeDuration(item.path("contentDetails").path("duration").asText(""));
        if (seconds > 0 && !isValidSongDuration(seconds)) return false; // 0 = 정보 없음 → 유지

        if ("10".equals(categoryId)) return true;              // 유튜브 음악 카테고리 = 곡으로 신뢰
        return looksLikeRealSong(item, title, artist);         // 그 외 카테고리는 곡 신호 필요
    }

    /**
     * 💡 [관리자] DB의 모든 곡을 videos.list 로 재검증(part 에 topicDetails 포함, 배치 50건/1유닛)해서
     *    "곡"으로 볼 수 없는 항목(인터뷰·라이브클립·쇼츠·삭제된 영상 등)을 삭제한다.
     *    (트랜잭션 밖 — 각 삭제를 독립 실행해 참조 무결성 오류가 전체를 롤백하지 않도록)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public Map<String, Integer> pruneNonMusicCatalog() {
        List<Music> all = new ArrayList<>(musicRepository.findAll());
        int checked = 0, removed = 0, failed = 0, apiError = 0;
        List<String> sample = new ArrayList<>();

        log.info("🧹 [정리] 시작 - 카탈로그 {}곡", all.size());

        // ── 1단계: API 없이 제목·채널·재생시간만으로 명백한 비음악 제거 (할당량 무관) ──
        int stage1 = 0;
        java.util.Iterator<Music> it0 = all.iterator();
        while (it0.hasNext()) {
            Music m = it0.next();
            if (Boolean.TRUE.equals(m.getManualAdd())) { it0.remove(); continue; } // 관리자 직접 등록 → 정리 제외
            String artist = m.getArtist() == null ? "" : m.getArtist();
            boolean junk = isNonMusicTitle(m.getTitle())
                    || isNonMusicChannel(artist)
                    || looksLikeCollabCover(m.getTitle(), artist)
                    || artist.toLowerCase().contains("cut")
                    || (m.getDurationSeconds() != null && !isValidSongDuration(m.getDurationSeconds()));
            if (junk) {
                checked++;
                if (deleteMusicSafely(m)) {
                    removed++; stage1++;
                    if (sample.size() < 20) sample.add(artist + " - " + m.getTitle());
                } else failed++;
                it0.remove();
            }
        }
        log.info("🧹 [정리] 1단계(로컬) 삭제 {}곡, 남은 {}곡", stage1, all.size());

        // ── 2단계: 남은 곡을 videos.list 로 카테고리/삭제여부 확인 ──
        for (int i = 0; i < all.size(); i += 50) {
            List<Music> batch = all.subList(i, Math.min(i + 50, all.size()));
            String ids = batch.stream().map(Music::getYoutubeVideoId)
                    .collect(java.util.stream.Collectors.joining(","));

            Map<String, JsonNode> byId = new java.util.HashMap<>();
            try {
                String url = "https://www.googleapis.com/youtube/v3/videos"
                        + "?part=snippet,contentDetails,status,statistics,topicDetails"
                        + "&id=" + ids
                        + "&key=" + currentApiKey();
                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("error")) {
                    int st = response.statusCode();
                    log.warn("[정리] videos.list {} - {}", st, root.path("error").path("message").asText(""));
                    String reason = root.path("error").path("errors").path(0).path("reason").asText("");
                    if (isQuotaError(st, reason)) { markKeyExhausted(currentApiKeyRaw()); apiError++; continue; }
                    apiError++;
                    continue;
                }
                for (JsonNode it : root.path("items")) byId.put(it.path("id").asText(), it);
            } catch (QuotaExceededException qe) {
                log.warn("[정리] 할당량 소진 - 중단");
                break;
            } catch (Exception e) {
                log.error("[정리] 배치 조회 실패", e);
                apiError++;
                continue;
            }

            for (Music m : batch) {
                JsonNode item = byId.get(m.getYoutubeVideoId());
                if (item == null) continue;   // 이번 응답에 없음(일시적일 수 있음) → 삭제하지 않음
                checked++;
                if (keepsAsSong(item)) continue;
                if (deleteMusicSafely(m)) {
                    removed++;
                    if (sample.size() < 20) sample.add(m.getArtist() + " - " + m.getTitle());
                } else failed++;
            }
        }

        log.info("🧹 카탈로그 정리: 검사 {} / 삭제 {} / 실패 {} / API오류 {}", checked, removed, failed, apiError);
        if (!sample.isEmpty()) log.info("🧹 삭제 예시: {}", sample);

        // ── 3단계: 유사 중복(다른 videoId·비슷한 제목) 정리 ──
        int deduped = dedupeCatalog();

        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("checked", checked);
        r.put("removed", removed);
        r.put("failed", failed);
        r.put("apiError", apiError);
        r.put("deduped", deduped);
        return r;
    }

    // =========================================================================
    // 유사 중복 곡 정리 (제목이 조금만 달라도 같은 곡으로 묶어 대표 1곡만 유지)
    // =========================================================================

    /** 중복 판별용 제목 정규화 — 괄호/피처링/버전·리마스터 표기 제거 후 영숫자·한글·가나만 남긴다. */
    public static String normalizeTitleForDedupe(String s) {
        if (s == null) return "";
        String x = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC).toLowerCase();
        x = x.replaceAll("\\s*-\\s*topic\\s*$", "");
        x = x.replaceAll("\\(.*?\\)|\\[.*?\\]|【.*?】|「.*?」", " ");
        x = x.replaceAll("feat\\.?.*|ft\\.?.*|with\\s.*", " ");
        x = x.replaceAll("official|m/v|mv|music video|lyric video|lyrics|visualizer|audio|performance video|color coded", " ");
        // 리마스터/기념반/디럭스 등 같은 곡의 다른 버전 표기 제거
        x = x.replaceAll("\\d{4}\\s*remaster(ed)?|remaster(ed)?|\\d+(th)?\\s*anniversary|\\d+주년"
                + "|deluxe|special edition|bonus track|album version|single version|radio edit", " ");
        x = x.replaceAll("^the\\s+", "");
        x = x.replaceAll("[^a-z0-9\\uac00-\\ud7a3\\u3040-\\u30ff]", "");
        return x.trim();
    }

    /** 중복 판별용 아티스트 정규화 — "- topic"·"VEVO" 접미사 제거. */
    public static String normalizeArtistForDedupe(String s) {
        if (s == null) return "";
        String x = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC).toLowerCase();
        x = x.replaceAll("\\s*-\\s*topic\\s*$", "");
        x = x.replaceAll("(vevo|official)\\s*$", "");
        x = x.replaceAll("[^a-z0-9\\uac00-\\ud7a3\\u3040-\\u30ff]", "");
        return x.trim();
    }

    // 같은 녹음으로 볼 재생시간 오차 허용치(초). 서로 다른 업로더가 인코딩한 동일 곡은
    // 보통 이 안쪽에서 길이가 거의 일치한다.
    private static final long SAME_RECORDING_DURATION_TOLERANCE_SEC = 5;

    /**
     * 두 곡이 "같은 녹음"인지 판단한다. 제목은 이미 같은 그룹(정규화 제목 일치)이라고 가정하고,
     * 서로 다른 채널(레이블/업로더)이 올린 동일 곡까지 잡아내기 위해 재생시간을 1차 기준으로 쓴다.
     * 재생시간 정보가 둘 다 없으면 아티스트(채널) 정규화 일치로 폴백한다.
     */
    public static boolean isSameRecording(Music a, Music b) {
        Long da = a.getDurationSeconds();
        Long db = b.getDurationSeconds();
        if (da != null && db != null && da > 0 && db > 0) {
            return Math.abs(da - db) <= SAME_RECORDING_DURATION_TOLERANCE_SEC;
        }
        String na = normalizeArtistForDedupe(a.getArtist());
        String nb = normalizeArtistForDedupe(b.getArtist());
        return !na.isEmpty() && na.equals(nb);
    }

    /** candidate 가 current 보다 대표곡으로 더 적합하면 true (관리자 수동등록 > 조회수 높음). */
    public static boolean isBetterRepresentative(Music candidate, Music current) {
        boolean cManual = Boolean.TRUE.equals(candidate.getManualAdd());
        boolean curManual = Boolean.TRUE.equals(current.getManualAdd());
        if (cManual != curManual) return cManual;
        long a = candidate.getViewCount() == null ? 0 : candidate.getViewCount();
        long b = current.getViewCount() == null ? 0 : current.getViewCount();
        return a > b;
    }

    /**
     * DB 전체를 정규화 제목으로 묶고, 같은 제목 그룹 안에서 재생시간(또는 아티스트)이 같은 것끼리만
     * "같은 녹음"으로 보아 대표 1곡만 남기고 나머지를 삭제한다.
     * (예전엔 아티스트까지 정확히 같아야 병합했는데, 서로 다른 레이블/업로더 채널이 올린 동일 곡은
     *  아티스트 문자열이 크게 달라 걸러지지 않는 문제가 있었다.)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public int dedupeCatalog() {
        List<Music> all = musicRepository.findAll();
        Map<String, List<Music>> groups = new java.util.LinkedHashMap<>();
        for (Music m : all) {
            String t = normalizeTitleForDedupe(m.getTitle());
            if (t.isEmpty()) continue; // 정규화 후 비면 병합 위험 → 건드리지 않음
            groups.computeIfAbsent(t, k -> new ArrayList<>()).add(m);
        }

        int removed = 0;
        List<String> sample = new ArrayList<>();
        for (List<Music> g : groups.values()) {
            if (g.size() < 2) continue;
            List<Music> kept = new ArrayList<>();
            for (Music m : g) {
                Music match = null;
                for (Music k : kept) {
                    if (isSameRecording(k, m)) { match = k; break; }
                }
                if (match == null) {
                    kept.add(m);
                    continue;
                }
                Music toDelete = m;
                if (isBetterRepresentative(m, match)) {
                    kept.set(kept.indexOf(match), m);
                    toDelete = match;
                }
                if (deleteMusicSafely(toDelete)) {
                    removed++;
                    if (sample.size() < 20) sample.add(toDelete.getArtist() + " - " + toDelete.getTitle());
                }
            }
        }
        log.info("🧹 [중복정리] 유사 중복 {}곡 삭제 (그룹 {}개)", removed, groups.size());
        if (!sample.isEmpty()) log.info("🧹 [중복정리] 삭제 예시: {}", sample);
        return removed;
    }

    /** 현재 인덱스의 키를 markKeyExhausted 에 넘기기 위한 raw 접근 (throw 안 함) */
    private String currentApiKeyRaw() {
        return apiKeys.isEmpty() ? null : apiKeys.get(keyIndex.get() % apiKeys.size());
    }

    /** 참조(listen_log·liked_music) 정리 후 음원 삭제. 성공 true. */
    private boolean deleteMusicSafely(Music m) {
        try {
            musicRemover.remove(m);
            return true;
        } catch (Exception e) {
            log.warn("[정리] 삭제 실패: {} - {} ({})", m.getId(), m.getTitle(), e.getMessage());
            return false;
        }
    }

    /**
     * 💡 카테고리별 키워드로 최신 음악을 검색해 DB에 동기화한다.
     *    - search API 는 videoCategoryId=10(음악) + order=date(최신순) 로 1차 필터
     *    - 각 결과는 getVideoInfo() 의 엄격한 단곡 음악 게이트를 통과해야만 저장됨
     *    - forcedGenre 가 있으면 장르를 강제로 지정한다 (카테고리 정합성 보장)
     */
    @Transactional
    public List<YouTubeVideoDto> syncLatestMusicByKeyword(String keyword, int maxResults, String forcedGenre) {
        List<YouTubeVideoDto> syncedVideos = new ArrayList<>();

        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);

            JsonNode root = null;
            JsonNode items = null;
            // 키가 여러 개면, 소진(403/429)된 키는 건너뛰고 다음 키로 재시도한다.
            for (int attempt = 0; attempt < Math.max(1, apiKeys.size()); attempt++) {
                String key = currentApiKey(); // 전부 소진 시 QuotaExceededException
                String url = "https://www.googleapis.com/youtube/v3/search"
                        + "?part=snippet"
                        + "&type=video"
                        + "&videoEmbeddable=true"
                        + "&videoCategoryId=10"   // 유튜브 "음악" 카테고리만 (비음악 영상 대폭 감소)
                        + "&maxResults=" + maxResults
                        + "&q=" + encodedKeyword
                        + "&key=" + key;

                HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                root = objectMapper.readTree(response.body());
                items = root.get("items");

                if (root.has("error")) {
                    int status = response.statusCode();
                    String reason = root.path("error").path("errors").path(0).path("reason").asText("");
                    if (isQuotaError(status, reason)) {
                        markKeyExhausted(key);
                        root = null;
                        continue; // 다음 키로
                    }
                    log.warn("YouTube 검색 API 오류 (keyword='{}', status={}, reason={})", keyword, status, reason);
                    return syncedVideos;
                }
                break; // 성공
            }

            if (root == null) {
                throw new QuotaExceededException("YouTube 검색 할당량 초과 (모든 키 소진)");
            }
            if (items == null || items.isEmpty()) {
                log.warn("YouTube 검색 결과 0건 (keyword='{}')", keyword);
            }

            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    JsonNode idNode = item.get("id");
                    if (idNode != null && idNode.has("videoId")) {
                        String videoId = idNode.get("videoId").asText();
                        try {
                            YouTubeVideoDto videoDto = getVideoInfo(videoId, true); // strict: 진짜 곡만

                            // 강제 장르 지정 (검색 키워드가 곧 카테고리이므로 신뢰)
                            if (forcedGenre != null && !forcedGenre.isBlank()) {
                                musicRepository.findByYoutubeVideoId(videoId).ifPresent(m -> {
                                    if (!forcedGenre.equalsIgnoreCase(m.getGenre())) {
                                        m.update(m.getTitle(), m.getArtist(), m.getThumbnailUrl(), forcedGenre.toUpperCase());
                                    }
                                });
                            }
                            syncedVideos.add(videoDto);
                        } catch (Exception e) {
                            log.debug("검색 결과 스킵: {} ({})", videoId, e.getMessage());
                        }
                    }
                }
            }
        } catch (QuotaExceededException qe) {
            throw qe;
        } catch (Exception e) {
            log.error("YouTube 검색 API 호출 중 오류 발생", e);
        }

        return syncedVideos;
    }

    /** 하위 호환용 오버로드 (장르 강제 없음) */
    @Transactional
    public List<YouTubeVideoDto> syncLatestMusicByKeyword(String keyword, int maxResults) {
        return syncLatestMusicByKeyword(keyword, maxResults, null);
    }

    // 카테고리별 검색 키워드 (곡 수를 늘리기 위한 다중 키워드)
    // 검색당 100유닛이라 카테고리별 키워드는 3개로 제한 (격일 실행 기준 ~1,200유닛)
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "KPOP", List.of("kpop 신곡 mv", "아이돌 타이틀곡 mv", "kpop 인기곡"),
            "JPOP", List.of("j-pop 新曲 mv", "アニメ 主題歌 mv", "日本 人気曲 mv"),
            "VTUBER", List.of("버추얼 아이돌 오리지널곡", "hololive original song", "니지산지 music"),
            "POP", List.of("new pop song official mv", "latest pop hits mv", "billboard hot 100 new")
    );

    /**
     * 💡 카테고리(KPOP / JPOP / VTUBER / POP)별 다중 키워드 검색으로 곡을 대량 확보한다.
     *    search.list = 호출당 100유닛 (일일 10,000 = 100회). 할당량 초과(429/403) 시 즉시 중단.
     */
    @Transactional
    public Map<String, Integer> syncLatestMusicForAllCategories(int perKeyword) {
        Map<String, Integer> result = new LinkedHashMap<>();

        outer:
        for (Map.Entry<String, List<String>> e : CATEGORY_KEYWORDS.entrySet()) {
            String genre = e.getKey();
            int count = 0;
            for (String kw : e.getValue()) {
                try {
                    count += syncLatestMusicByKeyword(kw, perKeyword, genre).size();
                } catch (QuotaExceededException qe) {
                    log.warn("⚠️ YouTube 검색 할당량 초과 - 카테고리 검색 중단 ({})", qe.getMessage());
                    result.put(genre, count);
                    break outer;
                }
            }
            result.put(genre, count);
            log.info("🎵 [{}] 카테고리 검색 {}건 동기화", genre, count);
        }
        return result;
    }

    /**
     * 💡 각 장르 곡 수가 목표치에 못 미칠 때만 검색으로 보충한다 (서버 재시작마다 호출해도 낭비 없음).
     */
    @Transactional
    public Map<String, Integer> ensureCatalogDepth(Map<String, Integer> targetPerGenre, int perKeyword) {
        Map<String, Integer> added = new LinkedHashMap<>();

        outer:
        for (Map.Entry<String, Integer> t : targetPerGenre.entrySet()) {
            String genre = t.getKey();
            long have = musicRepository.countByGenre(genre);
            if (have >= t.getValue()) {
                log.info("📚 [{}] {}곡 보유 (목표 {}) - 검색 생략", genre, have, t.getValue());
                continue;
            }
            int cnt = 0;
            for (String kw : CATEGORY_KEYWORDS.getOrDefault(genre, List.of())) {
                if (musicRepository.countByGenre(genre) >= t.getValue()) break;
                try {
                    cnt += syncLatestMusicByKeyword(kw, perKeyword, genre).size();
                } catch (QuotaExceededException qe) {
                    log.warn("⚠️ 카탈로그 보충 중 할당량 초과 - 중단 ({})", qe.getMessage());
                    added.put(genre, cnt);
                    break outer;
                }
            }
            added.put(genre, cnt);
            log.info("📚 [{}] 카탈로그 보충 {}건 (현재 {}곡)", genre, cnt, musicRepository.countByGenre(genre));
        }
        return added;
    }

    /** YouTube API 검색 할당량/레이트리밋 초과 시 던지는 마커 예외 */
    public static class QuotaExceededException extends RuntimeException {
        public QuotaExceededException(String message) {
            super(message);
        }
    }
}
