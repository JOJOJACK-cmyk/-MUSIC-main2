package com.example.music.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * 스트리밍 이용권 요금제 카탈로그. 금액/기간을 서버가 단일 관리한다.
 * (프론트가 보낸 금액을 그대로 믿지 않고 planId 로 대조 검증)
 */
public enum PricingPlan {

    MONTHLY("monthly", "무제한 스트리밍 정기 이용권", 11000, 1),
    ANNUAL("annual", "연간 이용권", 105600, 12),
    TICKET30("ticket30", "30일 30곡 제한 이용권", 5500, 1);

    private final String planId;
    private final String passName;
    private final int amount;
    private final int months;

    PricingPlan(String planId, String passName, int amount, int months) {
        this.planId = planId;
        this.passName = passName;
        this.amount = amount;
        this.months = months;
    }

    public String getPlanId() { return planId; }
    public String getPassName() { return passName; }
    public int getAmount() { return amount; }
    public int getMonths() { return months; }

    public static Optional<PricingPlan> fromPlanId(String planId) {
        return Arrays.stream(values())
                .filter(p -> p.planId.equalsIgnoreCase(planId))
                .findFirst();
    }
}
