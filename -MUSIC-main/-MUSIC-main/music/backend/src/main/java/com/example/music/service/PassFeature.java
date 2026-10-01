package com.example.music.service;

/**
 * 이용권으로 여는 기능. 요금제(PricingPlan)마다 이 중 일부를 포함하고,
 * 관리자·부관리자는 이용권 없이 전부 쓸 수 있다.
 */
public enum PassFeature {
    /** 모든 곡 전곡 무제한 재생 (무료는 하루 누적 1분 미리듣기) */
    UNLIMITED_PLAY("전곡 무제한 재생"),
    /** 이용권 기간 동안 정해진 곡 수만큼 전곡 재생 (같은 곡 다시 듣기는 차감 없음) */
    LIMITED_PLAY("정해진 곡 수만큼 전곡 재생"),
    /** 내 플레이리스트 만들기 · 곡 담기 */
    PLAYLIST("플레이리스트 만들기"),
    /** 라이브 채팅에서 닉네임 옆 프리미엄 ♪ 배지 (서버가 메시지마다 붙인다) */
    CHAT_BADGE("라이브 채팅 프리미엄 배지"),
    /** 스토어 상품 할인 */
    STORE_DISCOUNT("스토어 할인");

    private final String label;

    PassFeature(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
