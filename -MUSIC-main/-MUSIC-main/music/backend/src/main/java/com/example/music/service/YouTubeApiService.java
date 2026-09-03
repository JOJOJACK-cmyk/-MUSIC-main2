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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${youtube.api.key}")
    private String apiKey;

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
            "grammy museum", "sing-along", "sing along"
    };

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
    public boolean isNonMusicTitle(String title) {
        if (title == null) return true;
        String lower = title.toLowerCase();
        for (String keyword : NON_MUSIC_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
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
                    + "?part=snippet,contentDetails,status,statistics"
                    + "&id=" + videoId
                    + "&key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.get("items");

            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("YouTube 영상을 찾을 수 없습니다: " + videoId);
            }

            Music savedMusic = upsertFromVideoItem(items.get(0), null)
                    .orElseThrow(() -> new IllegalArgumentException("단곡 음악 조건을 만족하지 않는 영상입니다: " + videoId));
            return toDto(savedMusic);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("YouTube API 호출 중 오류 발생", e);
            throw new RuntimeException("YouTube API 연동 실패: " + e.getMessage());
        }
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

        if (title.isEmpty() || BLOCKED_CATEGORY_IDS.contains(categoryId) || isNonMusicTitle(title)) {
            return Optional.empty();
        }
        if (contentDetails == null || !contentDetails.has("duration")) {
            return Optional.empty();
        }
        long seconds = parseYouTubeDuration(contentDetails.get("duration").asText());
        if (!isValidSongDuration(seconds)) {
            return Optional.empty();
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
                        + "&key=" + apiKey;
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
                    + "&key=" + apiKey;
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
                        + "&key=" + apiKey;

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
                        + "&key=" + apiKey;
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
                        musicRepository.delete(m);
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

                    if (seconds == 0L || isNonMusicTitle(m.getTitle()) || !isValidSongDuration(seconds)) {
                        musicRepository.delete(m);
                        deleted++;
                    } else {
                        m.updateDuration(seconds);
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
            // order=relevance(기본): 키워드에 가장 잘 맞는(대개 인기 있는) 곡을 우선.
            // videoDuration 미지정: getVideoInfo 의 90~480초 게이트로 최종 검증.
            String url = "https://www.googleapis.com/youtube/v3/search"
                    + "?part=snippet"
                    + "&type=video"
                    + "&videoEmbeddable=true"
                    + "&maxResults=" + maxResults
                    + "&q=" + encodedKeyword
                    + "&key=" + apiKey;

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.get("items");

            if (root.has("error")) {
                int status = response.statusCode();
                String reason = root.path("error").path("errors").path(0).path("reason").asText("");
                log.warn("YouTube 검색 API 오류 (keyword='{}', status={}, reason={})", keyword, status, reason);
                // 할당량/레이트리밋 초과 시 남은 검색을 중단해 추가 소진을 막는다
                if (status == 429 || status == 403) {
                    throw new QuotaExceededException("YouTube 검색 할당량 초과: " + reason);
                }
                return syncedVideos;
            } else if (items == null || items.isEmpty()) {
                log.warn("YouTube 검색 결과 0건 (keyword='{}', status={})", keyword, response.statusCode());
            }

            if (items != null && items.isArray()) {
                for (JsonNode item : items) {
                    JsonNode idNode = item.get("id");
                    if (idNode != null && idNode.has("videoId")) {
                        String videoId = idNode.get("videoId").asText();
                        try {
                            YouTubeVideoDto videoDto = getVideoInfo(videoId);

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
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = Map.of(
            "KPOP", List.of("kpop 신곡 mv", "아이돌 타이틀곡 mv", "kpop 인기곡", "kpop title track", "한국 발라드 신곡"),
            "JPOP", List.of("j-pop 新曲 mv", "jpop hits", "アニメ 主題歌 mv", "邦楽 話題曲", "日本 人気曲 mv"),
            "VTUBER", List.of("버추얼 아이돌 오리지널곡", "버튜버 신곡", "hololive original song", "홀로라이브 오리지널곡",
                    "이세계아이돌", "니지산지 music", "스텔라이브 원곡", "kamitsubaki record"),
            "POP", List.of("new pop song official mv", "latest pop hits mv", "trending pop music video",
                    "billboard hot 100 new", "official music video 2026")
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
