package com.example.music.service;

import com.example.music.entity.Music;
import com.example.music.entity.Product;
import com.example.music.entity.ShopOrder;
import com.example.music.entity.User;
import com.example.music.repository.MusicRepository;
import com.example.music.repository.ProductRepository;
import com.example.music.repository.ShopOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 스토어 (음반 / 굿즈).
 * 주문 흐름: prepareOrder(PENDING, 서버가 금액 계산) → 토스 결제창 → confirmOrder(금액 검증 + 재고 차감 + 토스 승인)
 * 취소(환불): 토스 결제 취소 + 재고 복구 + CANCELLED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShopService {

    public static final int MAX_QUANTITY = 10;
    private static final Pattern PHONE = Pattern.compile("^0\\d{1,2}-?\\d{3,4}-?\\d{4}$");
    private static final Pattern ZIPCODE = Pattern.compile("^\\d{5}$");
    private static final Set<String> CATEGORIES = Set.of(Product.ALBUM, Product.MERCH);

    private final ProductRepository productRepository;
    private final ShopOrderRepository shopOrderRepository;
    private final MusicRepository musicRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PassEntitlementService entitlementService;

    /** 상품 등록/수정 입력값 */
    public record ProductForm(String name, String description, Integer price, Integer stock,
                              String category, String artist, Long musicId, String imageUrl) {}

    /** 배송지 입력값 */
    public record ShippingForm(String recipientName, String phone, String zipcode,
                               String address, String addressDetail, String memo) {}

    public record ConfirmResult(ShopOrder order, boolean newlyPaid) {}

    // ───────────────────────── 상품 조회 ─────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listProducts(String category) {
        return productRepository.findByActiveTrueOrderByIdDesc().stream()
                .filter(p -> category == null || category.isBlank() || p.getCategory().equalsIgnoreCase(category))
                .map(ShopService::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProduct(Long id) {
        Product p = productRepository.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> new IllegalArgumentException("판매 중인 상품이 아닙니다."));
        return toDto(p);
    }

    /**
     * 지금 듣는 곡의 관련 상품: 그 곡에 직접 연결된 상품 + 그 아티스트 상품.
     * 아티스트는 곡의 아티스트(채널명)와 제목 양쪽에서 찾는다 — 유튜브 MV 는 채널명이 레이블인 경우가 많아서.
     * 상품의 artist 칸에 "르세라핌, LE SSERAFIM" 처럼 콤마로 별칭을 여러 개 적을 수 있다.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> relatedProducts(Long musicId) {
        Music music = musicRepository.findById(musicId).orElse(null);
        if (music == null) return List.of();
        String haystack = YouTubeApiService.normalizeArtistForDedupe(music.getArtist())
                + "|" + YouTubeApiService.normalizeArtistForDedupe(music.getTitle());

        List<Map<String, Object>> direct = new ArrayList<>();
        List<Map<String, Object>> byArtist = new ArrayList<>();
        for (Product p : productRepository.findByActiveTrueOrderByIdDesc()) {
            if (p.getMusic() != null && p.getMusic().getId().equals(musicId)) {
                direct.add(toDto(p));
            } else if (artistMatches(p.getArtist(), haystack)) {
                byArtist.add(toDto(p));
            }
        }
        direct.addAll(byArtist); // 곡에 직접 연결된 상품이 먼저
        return direct.size() > 12 ? direct.subList(0, 12) : direct;
    }

    private static boolean artistMatches(String productArtist, String haystack) {
        if (productArtist == null || productArtist.isBlank()) return false;
        for (String alias : productArtist.split(",")) {
            String a = YouTubeApiService.normalizeArtistForDedupe(alias);
            if (a.length() >= 2 && haystack.contains(a)) return true;
        }
        return false;
    }

    // ───────────────────────── 주문 (사용자) ─────────────────────────

    @Transactional
    public Map<String, Object> prepareOrder(User user, Long productId, Integer quantity, ShippingForm ship) {
        if (productId == null) throw new IllegalArgumentException("상품을 선택해 주세요.");
        int qty = quantity == null ? 1 : quantity;
        if (qty < 1 || qty > MAX_QUANTITY) {
            throw new IllegalArgumentException("수량은 1~" + MAX_QUANTITY + "개까지 주문할 수 있습니다.");
        }
        Product product = productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new IllegalArgumentException("판매 중인 상품이 아닙니다."));
        if (product.getStock() < qty) {
            throw new IllegalArgumentException(product.getStock() == 0 ? "품절된 상품입니다." : "재고가 부족합니다. (남은 수량 " + product.getStock() + "개)");
        }
        ShippingForm s = validateShipping(ship);

        String orderId = "shop_" + UUID.randomUUID().toString().replace("-", "");
        ShopOrder order = shopOrderRepository.save(new ShopOrder(orderId, user, product, qty,
                s.recipientName(), s.phone(), s.zipcode(), s.address(), s.addressDetail(), s.memo(),
                entitlementService.of(user).storeDiscountPct()));

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("amount", order.getTotalAmount());
        body.put("discountPct", order.getDiscountPct());
        body.put("orderName", qty > 1 ? product.getName() + " × " + qty + "개" : product.getName());
        return body;
    }

    @Transactional
    public ConfirmResult confirmOrder(User user, String paymentKey, String orderId, Integer amount) {
        if (paymentKey == null || orderId == null || amount == null) {
            throw new IllegalArgumentException("결제 정보가 올바르지 않습니다.");
        }
        ShopOrder order = ownedOrder(user, orderId);
        if (!ShopOrder.PENDING.equals(order.getStatus())) {
            return new ConfirmResult(order, false); // 새로고침 등 중복 승인
        }
        if (order.getTotalAmount() != amount) {
            throw new IllegalArgumentException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }
        // 재고를 먼저 차감하고 토스 승인 — 승인이 실패하면 예외로 트랜잭션이 롤백되어 재고도 원복된다
        if (productRepository.decreaseStock(order.getProduct().getId(), order.getQuantity()) == 0) {
            throw new IllegalArgumentException("결제 사이에 재고가 소진되었습니다. 결제는 진행되지 않았습니다.");
        }
        tossPaymentClient.confirm(paymentKey, orderId, order.getTotalAmount());
        order.markPaid(paymentKey);
        log.info("[Shop] 주문 결제 완료 orderId={} user={} product={} qty={} amount={}",
                orderId, user.getId(), order.getProduct().getId(), order.getQuantity(), order.getTotalAmount());
        return new ConfirmResult(order, true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myOrders(User user) {
        return shopOrderRepository.findByUser_IdAndStatusNotOrderByCreatedAtDesc(user.getId(), ShopOrder.PENDING)
                .stream()
                .map(o -> toOrderDto(o, false))
                .toList();
    }

    /** 사용자 본인 취소 — 배송 시작 전(PAID)까지만 */
    @Transactional
    public Map<String, Object> cancelMyOrder(User user, String orderId) {
        ShopOrder order = ownedOrder(user, orderId);
        if (!ShopOrder.PAID.equals(order.getStatus())) {
            throw new IllegalArgumentException("배송이 시작된 주문은 직접 취소할 수 없습니다. 고객센터로 문의해 주세요.");
        }
        refund(order, "구매자 요청 취소");
        return toOrderDto(order, false);
    }

    // ───────────────────────── 관리자 ─────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminProducts() {
        return productRepository.findAllByOrderByIdDesc().stream().map(ShopService::toDto).toList();
    }

    @Transactional
    public Map<String, Object> createProduct(ProductForm form) {
        ProductForm f = validateProduct(form);
        Product p = productRepository.save(new Product(f.name(), f.description(), f.price(), f.stock(),
                f.category(), f.artist(), findMusic(f.musicId()), f.imageUrl()));
        return toDto(p);
    }

    @Transactional
    public Map<String, Object> updateProduct(Long id, ProductForm form) {
        ProductForm f = validateProduct(form);
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        p.update(f.name(), f.description(), f.price(), f.stock(), f.category(), f.artist(),
                findMusic(f.musicId()), f.imageUrl());
        return toDto(p);
    }

    @Transactional
    public Map<String, Object> setProductActive(Long id, boolean active) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));
        p.setActive(active);
        return toDto(p);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> adminOrders(String status) {
        Collection<String> statuses = (status == null || status.isBlank())
                ? List.of(ShopOrder.PAID, ShopOrder.SHIPPING, ShopOrder.DELIVERED, ShopOrder.CANCELLED)
                : List.of(status.toUpperCase());
        return shopOrderRepository.findTop200ByStatusInOrderByCreatedAtDesc(statuses).stream()
                .map(o -> toOrderDto(o, true))
                .toList();
    }

    /** 관리자 상태 변경 (배송 시작/완료/취소). 취소면 결제 환불 + 재고 복구 */
    @Transactional
    public Map<String, Object> adminUpdateStatus(String orderId, String nextStatus) {
        ShopOrder order = shopOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        String next = nextStatus == null ? "" : nextStatus.toUpperCase();
        if (ShopOrder.CANCELLED.equals(next)) {
            if (!order.canMoveTo(ShopOrder.CANCELLED)) {
                throw new IllegalArgumentException("이 주문은 취소할 수 없는 상태입니다.");
            }
            refund(order, "판매자 취소");
        } else {
            order.moveTo(next);
        }
        return toOrderDto(order, true);
    }

    // ───────────────────────── 내부 ─────────────────────────

    private void refund(ShopOrder order, String reason) {
        tossPaymentClient.cancel(order.getPaymentKey(), reason); // 실패하면 예외 → 상태·재고 변경 없음
        productRepository.increaseStock(order.getProduct().getId(), order.getQuantity());
        order.moveTo(ShopOrder.CANCELLED);
        log.info("[Shop] 주문 취소/환불 orderId={} reason={}", order.getOrderId(), reason);
    }

    private ShopOrder ownedOrder(User user, String orderId) {
        ShopOrder order = shopOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        if (!order.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("본인 주문만 처리할 수 있습니다.");
        }
        return order;
    }

    private Music findMusic(Long musicId) {
        if (musicId == null) return null;
        return musicRepository.findById(musicId)
                .orElseThrow(() -> new IllegalArgumentException("연결할 곡을 찾을 수 없습니다. (id=" + musicId + ")"));
    }

    private static ProductForm validateProduct(ProductForm f) {
        if (f == null) throw new IllegalArgumentException("상품 정보가 없습니다.");
        String name = trimToNull(f.name());
        if (name == null || name.length() > 100) throw new IllegalArgumentException("상품명은 1~100자로 입력해 주세요.");
        if (f.price() == null || f.price() < 100 || f.price() > 10_000_000) {
            throw new IllegalArgumentException("가격은 100원 ~ 10,000,000원 사이로 입력해 주세요.");
        }
        if (f.stock() == null || f.stock() < 0 || f.stock() > 100_000) {
            throw new IllegalArgumentException("재고는 0 ~ 100,000 사이로 입력해 주세요.");
        }
        String category = f.category() == null ? Product.ALBUM : f.category().toUpperCase();
        if (!CATEGORIES.contains(category)) throw new IllegalArgumentException("분류는 ALBUM(음반) 또는 MERCH(굿즈)입니다.");
        String description = trimToNull(f.description());
        if (description != null && description.length() > 1000) description = description.substring(0, 1000);
        String artist = trimToNull(f.artist());
        if (artist != null && artist.length() > 100) artist = artist.substring(0, 100);
        String imageUrl = trimToNull(f.imageUrl());
        if (imageUrl != null && !(imageUrl.startsWith("https://") || imageUrl.startsWith("http://"))) {
            throw new IllegalArgumentException("이미지 주소는 http(s):// 로 시작해야 합니다.");
        }
        return new ProductForm(name, description, f.price(), f.stock(), category, artist, f.musicId(), imageUrl);
    }

    private static ShippingForm validateShipping(ShippingForm s) {
        if (s == null) throw new IllegalArgumentException("배송지 정보를 입력해 주세요.");
        String name = trimToNull(s.recipientName());
        String phone = trimToNull(s.phone());
        String zipcode = trimToNull(s.zipcode());
        String address = trimToNull(s.address());
        if (name == null || name.length() > 30) throw new IllegalArgumentException("받는 분 이름을 입력해 주세요.");
        if (phone == null || !PHONE.matcher(phone).matches()) throw new IllegalArgumentException("연락처 형식이 올바르지 않습니다. (예: 010-1234-5678)");
        if (zipcode == null || !ZIPCODE.matcher(zipcode).matches()) throw new IllegalArgumentException("우편번호 5자리를 입력해 주세요.");
        if (address == null || address.length() > 200) throw new IllegalArgumentException("주소를 입력해 주세요.");
        String detail = trimToNull(s.addressDetail());
        String memo = trimToNull(s.memo());
        if (detail != null && detail.length() > 100) detail = detail.substring(0, 100);
        if (memo != null && memo.length() > 100) memo = memo.substring(0, 100);
        return new ShippingForm(name, phone, zipcode, address, detail, memo);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    static Map<String, Object> toDto(Product p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("price", p.getPrice());
        m.put("stock", p.getStock());
        m.put("category", p.getCategory());
        m.put("artist", p.getArtist());
        m.put("imageUrl", p.getImageUrl());
        m.put("active", p.isActive());
        if (p.getMusic() != null) {
            m.put("musicId", p.getMusic().getId());
            m.put("musicTitle", p.getMusic().getTitle());
        }
        return m;
    }

    private static Map<String, Object> toOrderDto(ShopOrder o, boolean includeBuyer) {
        Map<String, Object> m = new HashMap<>();
        m.put("orderId", o.getOrderId());
        m.put("productId", o.getProduct().getId());
        m.put("productName", o.getProductName());
        m.put("unitPrice", o.getUnitPrice());
        m.put("quantity", o.getQuantity());
        m.put("totalAmount", o.getTotalAmount());
        m.put("discountPct", o.getDiscountPct() == null ? 0 : o.getDiscountPct());
        m.put("status", o.getStatus());
        m.put("recipientName", o.getRecipientName());
        m.put("phone", o.getPhone());
        m.put("zipcode", o.getZipcode());
        m.put("address", o.getAddress());
        m.put("addressDetail", o.getAddressDetail());
        m.put("memo", o.getMemo());
        m.put("createdAt", o.getCreatedAt());
        m.put("paidAt", o.getPaidAt());
        m.put("cancelledAt", o.getCancelledAt());
        if (includeBuyer) m.put("buyerEmail", o.getUser().getEmail());
        return m;
    }
}
