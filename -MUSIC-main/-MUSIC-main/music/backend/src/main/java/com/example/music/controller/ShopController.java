package com.example.music.controller;

import com.example.music.entity.User;
import com.example.music.security.AuthenticatedUserResolver;
import com.example.music.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Shop API", description = "스토어 — 음반/굿즈 조회 및 주문")
@RestController
@RequestMapping("/api/shop")
@RequiredArgsConstructor
public class ShopController {

    private final ShopService shopService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    @Operation(summary = "판매 중인 상품 목록", description = "category=ALBUM|MERCH (없으면 전체)")
    @GetMapping("/products")
    public List<Map<String, Object>> products(@RequestParam(required = false) String category) {
        return shopService.listProducts(category);
    }

    @Operation(summary = "곡 관련 상품", description = "그 곡에 연결된 상품 + 같은 아티스트 상품 (최대 12개)")
    @GetMapping("/products/related")
    public List<Map<String, Object>> related(@RequestParam Long musicId) {
        return shopService.relatedProducts(musicId);
    }

    @Operation(summary = "상품 상세")
    @GetMapping("/products/{id}")
    public Map<String, Object> product(@PathVariable Long id) {
        return shopService.getProduct(id);
    }

    @Operation(summary = "주문 만들기 (결제 전)",
            description = "body: {productId, quantity, recipientName, phone, zipcode, address, addressDetail, memo}")
    @PostMapping("/orders")
    public Map<String, Object> prepareOrder(@RequestBody Map<String, Object> body, Authentication authentication) {
        User me = authenticatedUserResolver.resolveRequiredUser(authentication);
        return shopService.prepareOrder(me, toLong(body.get("productId")), toInt(body.get("quantity")),
                new ShopService.ShippingForm(str(body, "recipientName"), str(body, "phone"), str(body, "zipcode"),
                        str(body, "address"), str(body, "addressDetail"), str(body, "memo")));
    }

    @Operation(summary = "주문 결제 승인", description = "토스 결제 성공 후 {paymentKey, orderId, amount}")
    @PostMapping("/orders/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, Object> body, Authentication authentication) {
        User me = authenticatedUserResolver.resolveRequiredUser(authentication);
        ShopService.ConfirmResult r = shopService.confirmOrder(me, str(body, "paymentKey"), str(body, "orderId"),
                toInt(body.get("amount")));
        return Map.of("status", "SUCCESS", "orderId", r.order().getOrderId(),
                "productName", r.order().getProductName(), "totalAmount", r.order().getTotalAmount());
    }

    @Operation(summary = "내 주문 내역")
    @GetMapping("/orders/mine")
    public List<Map<String, Object>> myOrders(Authentication authentication) {
        return shopService.myOrders(authenticatedUserResolver.resolveRequiredUser(authentication));
    }

    @Operation(summary = "주문 취소(환불)", description = "배송 시작 전(PAID)까지만 가능")
    @PostMapping("/orders/{orderId}/cancel")
    public Map<String, Object> cancel(@PathVariable String orderId, Authentication authentication) {
        return shopService.cancelMyOrder(authenticatedUserResolver.resolveRequiredUser(authentication), orderId);
    }

    static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v);
    }

    static Long toLong(Object o) {
        try { return o == null || String.valueOf(o).isBlank() ? null : Long.valueOf(String.valueOf(o)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("숫자 형식이 올바르지 않습니다."); }
    }

    static Integer toInt(Object o) {
        try { return o == null || String.valueOf(o).isBlank() ? null : Integer.valueOf(String.valueOf(o)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("숫자 형식이 올바르지 않습니다."); }
    }
}
