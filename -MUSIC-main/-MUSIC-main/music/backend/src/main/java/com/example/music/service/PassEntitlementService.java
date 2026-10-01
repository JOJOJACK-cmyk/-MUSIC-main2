package com.example.music.service;

import com.example.music.entity.Pass;
import com.example.music.entity.PassSongClaim;
import com.example.music.entity.User;
import com.example.music.repository.PassRepository;
import com.example.music.repository.PassSongClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 이용권 → 기능 허용 판정의 단일 소스.
 *  - 지금 쓰는 이용권(시작일 ≤ 지금 < 만료일)들의 요금제 기능을 합친 것이 그 회원이 쓸 수 있는 기능이다.
 *  - 관리자 · 부관리자는 이용권 없이 모든 기능.
 *  - 곡 수 제한 이용권(라이트)은 전곡 재생한 곡을 tb_pass_song 에 기록해 한도를 센다.
 */
@Service
@RequiredArgsConstructor
public class PassEntitlementService {

    private final PassRepository passRepository;
    private final PassSongClaimRepository claimRepository;

    /** 이용권이 필요한 기능을 쓰려 할 때 (→ 403 PASS_REQUIRED) */
    public static class PassRequiredException extends RuntimeException {
        private final PassFeature feature;

        public PassRequiredException(PassFeature feature) {
            super(feature.getLabel() + "은(는) " + plansWith(feature) + "에 포함된 기능이에요.");
            this.feature = feature;
        }

        public PassFeature getFeature() { return feature; }
    }

    /** 곡 수 제한 이용권 차감 결과 */
    public record ClaimResult(boolean allowed, boolean unlimited, int used, int limit) {}

    /** 한 회원의 현재 이용 권한 */
    public record Entitlement(
            boolean staff,
            Set<PassFeature> features,
            List<Pass> current,      // 지금 쓰는 이용권
            List<Pass> upcoming,     // 연장 결제로 예약된 이용권
            Pass limitedPass,        // 곡 수 제한 이용권 (있으면)
            int songLimit,
            int storeDiscountPct
    ) {
        public boolean has(PassFeature f) { return staff || features.contains(f); }
    }

    public static boolean isStaff(User user) {
        if (user == null || user.getRole() == null) return false;
        String r = user.getRole().toUpperCase();
        return r.equals("ROLE_ADMIN") || r.equals("ADMIN") || r.equals("ROLE_SUB_ADMIN") || r.equals("SUB_ADMIN");
    }

    public static Optional<PricingPlan> planOf(Pass pass) {
        return PricingPlan.forPass(pass.getPlanId(), pass.getPassName());
    }

    @Transactional(readOnly = true)
    public Entitlement of(User user) {
        if (user == null) {
            return new Entitlement(false, EnumSet.noneOf(PassFeature.class), List.of(), List.of(), null, 0, 0);
        }
        LocalDateTime now = LocalDateTime.now();
        List<Pass> passes = passRepository.findByUser_IdAndIsActiveTrueAndExpireDateAfterOrderByStartDateAsc(user.getId(), now);
        List<Pass> current = passes.stream().filter(p -> !p.getStartDate().isAfter(now)).toList();
        List<Pass> upcoming = passes.stream().filter(p -> p.getStartDate().isAfter(now)).toList();

        Set<PassFeature> features = EnumSet.noneOf(PassFeature.class);
        int discount = 0;
        Pass limited = null;
        int songLimit = 0;
        for (Pass p : current) {
            Optional<PricingPlan> plan = planOf(p);
            if (plan.isEmpty()) continue;
            features.addAll(plan.get().getFeatures());
            discount = Math.max(discount, plan.get().getStoreDiscountPct());
            if (limited == null && plan.get().has(PassFeature.LIMITED_PLAY)) {
                limited = p;
                songLimit = plan.get().getSongLimit();
            }
        }

        boolean staff = isStaff(user);
        if (staff) {
            features = EnumSet.allOf(PassFeature.class);
            discount = Arrays.stream(PricingPlan.values()).mapToInt(PricingPlan::getStoreDiscountPct).max().orElse(0);
        }
        return new Entitlement(staff, features, current, upcoming, limited, songLimit, discount);
    }

    public boolean has(User user, PassFeature feature) {
        return of(user).has(feature);
    }

    /** 기능이 없으면 PassRequiredException (403) */
    public void require(User user, PassFeature feature) {
        if (!has(user, feature)) throw new PassRequiredException(feature);
    }

    /** 전곡 무제한이 아니면, 곡 수 제한 이용권에서 이미 차감된 곡 목록 */
    @Transactional(readOnly = true)
    public List<Long> claimedSongIds(Entitlement e) {
        return e.limitedPass() == null ? List.of() : claimRepository.findMusicIdsByPassId(e.limitedPass().getId());
    }

    /**
     * 이 곡을 전곡 재생해도 되는지 확인하고, 곡 수 제한 이용권이면 1곡 차감한다.
     * 같은 이용권에서 이미 들은 곡은 다시 차감하지 않는다.
     */
    @Transactional
    public ClaimResult claimSong(User user, Long musicId) {
        Entitlement e = of(user);
        if (e.has(PassFeature.UNLIMITED_PLAY)) return new ClaimResult(true, true, 0, 0);
        Pass pass = e.limitedPass();
        if (pass == null || musicId == null) return new ClaimResult(false, false, 0, 0);

        passRepository.lockById(pass.getId()); // 같은 이용권의 차감을 한 번에 하나씩
        int limit = e.songLimit();
        int used = (int) claimRepository.countByPassId(pass.getId());
        if (claimRepository.existsByPassIdAndMusicId(pass.getId(), musicId)) {
            return new ClaimResult(true, false, used, limit);
        }
        if (used >= limit) return new ClaimResult(false, false, used, limit);
        claimRepository.save(new PassSongClaim(pass.getId(), musicId));
        return new ClaimResult(true, false, used + 1, limit);
    }

    /** 청취 기록 · 차트에 반영할 전곡 재생인지 (무제한이거나, 곡 수 제한 이용권에서 차감된 곡) */
    @Transactional(readOnly = true)
    public boolean isFullPlay(User user, Long musicId) {
        Entitlement e = of(user);
        if (e.has(PassFeature.UNLIMITED_PLAY)) return true;
        return e.limitedPass() != null && musicId != null
                && claimRepository.existsByPassIdAndMusicId(e.limitedPass().getId(), musicId);
    }

    /** 이 기능이 들어 있는 요금제 이름들 (안내 문구용) */
    public static String plansWith(PassFeature feature) {
        return Arrays.stream(PricingPlan.values())
                .filter(p -> p.has(feature) && p != PricingPlan.PREMIUM_ANNUAL)
                .map(p -> p.getPassName().replace(" 이용권", ""))
                .collect(Collectors.joining(" · ")) + " 이용권";
    }
}
