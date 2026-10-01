package com.example.music.service;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 스트리밍 이용권 요금제 카탈로그. 금액 · 기간 · 포함 기능을 서버가 단일 관리한다.
 * (프론트가 보낸 금액을 그대로 믿지 않고 planId 로 대조 검증하고, 기능 허용도 여기 적힌 것만)
 *
 *  라이트 30곡  4,900원 / 1개월  — 30곡 전곡 재생
 *  스탠다드      7,900원 / 1개월  — 전곡 무제한 + 플레이리스트
 *  프리미엄     10,900원 / 1개월  — 스탠다드 + 라이브 채팅 프리미엄 배지 + 스토어 10% 할인
 *  프리미엄 연간 109,000원 / 12개월 — 프리미엄 12개월 (2개월 무료 효과)
 */
public enum PricingPlan {

    LIGHT("light", "라이트 30곡 이용권", 4_900, 1, 30, 0,
            EnumSet.of(PassFeature.LIMITED_PLAY),
            List.of("한 달 동안 30곡 전곡 재생", "같은 곡은 다시 들어도 차감 없음")),
    STANDARD("standard", "스탠다드 이용권", 7_900, 1, 0, 0,
            EnumSet.of(PassFeature.UNLIMITED_PLAY, PassFeature.PLAYLIST),
            List.of("모든 곡 전곡 무제한 재생", "내 플레이리스트 만들기")),
    PREMIUM("premium", "프리미엄 이용권", 10_900, 1, 0, 10,
            EnumSet.of(PassFeature.UNLIMITED_PLAY, PassFeature.PLAYLIST,
                    PassFeature.CHAT_BADGE, PassFeature.STORE_DISCOUNT),
            List.of("모든 곡 전곡 무제한 재생", "내 플레이리스트 만들기", "라이브 채팅 프리미엄 ♪ 배지", "스토어 음반·굿즈 10% 할인")),
    PREMIUM_ANNUAL("premium_annual", "프리미엄 연간 이용권", 109_000, 12, 0, 10,
            EnumSet.of(PassFeature.UNLIMITED_PLAY, PassFeature.PLAYLIST,
                    PassFeature.CHAT_BADGE, PassFeature.STORE_DISCOUNT),
            List.of("프리미엄 혜택 12개월", "월 결제보다 2개월분 저렴 (약 17% 할인)"));

    private final String planId;
    private final String passName;
    private final int amount;
    private final int months;
    private final int songLimit;        // LIMITED_PLAY 요금제의 곡 수 (그 외 0)
    private final int storeDiscountPct; // STORE_DISCOUNT 요금제의 할인율 (그 외 0)
    private final Set<PassFeature> features;
    private final List<String> highlights; // 결제 화면에 그대로 보여 줄 기능 설명

    PricingPlan(String planId, String passName, int amount, int months, int songLimit, int storeDiscountPct,
                Set<PassFeature> features, List<String> highlights) {
        this.planId = planId;
        this.passName = passName;
        this.amount = amount;
        this.months = months;
        this.songLimit = songLimit;
        this.storeDiscountPct = storeDiscountPct;
        this.features = features;
        this.highlights = highlights;
    }

    public String getPlanId() { return planId; }
    public String getPassName() { return passName; }
    public int getAmount() { return amount; }
    public int getMonths() { return months; }
    public int getSongLimit() { return songLimit; }
    public int getStoreDiscountPct() { return storeDiscountPct; }
    public Set<PassFeature> getFeatures() { return features; }
    public List<String> getHighlights() { return highlights; }

    public boolean has(PassFeature f) { return features.contains(f); }

    public static Optional<PricingPlan> fromPlanId(String planId) {
        if (planId == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(p -> p.planId.equalsIgnoreCase(planId))
                .findFirst();
    }

    /**
     * 이용권 행의 요금제. plan_id 가 없는 예전 이용권은 이름으로 대응시킨다
     * (예전 "무제한 스트리밍 정기"/"연간" = 전곡 무제한 → 스탠다드, "30일 30곡 제한" → 라이트).
     */
    public static Optional<PricingPlan> forPass(String planId, String passName) {
        Optional<PricingPlan> byId = fromPlanId(planId);
        if (byId.isPresent()) return byId;
        if (passName == null) return Optional.empty();
        if (passName.contains("30곡")) return Optional.of(LIGHT);
        if (passName.contains("무제한") || passName.contains("연간")) return Optional.of(STANDARD);
        return Arrays.stream(values()).filter(p -> p.passName.equals(passName)).findFirst();
    }
}
