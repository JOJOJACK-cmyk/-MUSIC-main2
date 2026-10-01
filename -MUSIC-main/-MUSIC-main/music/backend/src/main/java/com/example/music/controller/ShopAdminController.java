package com.example.music.controller;

import com.example.music.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.example.music.controller.ShopController.str;
import static com.example.music.controller.ShopController.toInt;
import static com.example.music.controller.ShopController.toLong;

/** 스토어 관리 — 관리자 + 부 관리자 (SecurityConfig 에서도 /api/shop/admin/** 로 제한) */
@Tag(name = "Shop Admin API", description = "[관리자] 상품 등록/수정, 주문 상태 관리")
@RestController
@RequestMapping("/api/shop/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUB_ADMIN')")
public class ShopAdminController {

    private final ShopService shopService;

    @Operation(summary = "[관리자] 전체 상품 (판매 중지 포함)")
    @GetMapping("/products")
    public List<Map<String, Object>> products() {
        return shopService.adminProducts();
    }

    @Operation(summary = "[관리자] 상품 등록",
            description = "body: {name, description, price, stock, category(ALBUM|MERCH), artist, musicId, imageUrl}")
    @PostMapping("/products")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        return shopService.createProduct(form(body));
    }

    @Operation(summary = "[관리자] 상품 수정")
    @PutMapping("/products/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return shopService.updateProduct(id, form(body));
    }

    @Operation(summary = "[관리자] 판매 시작/중지", description = "body: {active: true|false}")
    @PatchMapping("/products/{id}/active")
    public Map<String, Object> setActive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return shopService.setProductActive(id, Boolean.parseBoolean(String.valueOf(body.get("active"))));
    }

    @Operation(summary = "[관리자] 주문 목록", description = "status=PAID|SHIPPING|DELIVERED|CANCELLED (없으면 결제 완료 이후 전체)")
    @GetMapping("/orders")
    public List<Map<String, Object>> orders(@RequestParam(required = false) String status) {
        return shopService.adminOrders(status);
    }

    @Operation(summary = "[관리자] 주문 상태 변경", description = "body: {status: SHIPPING|DELIVERED|CANCELLED}. CANCELLED 는 결제 환불 + 재고 복구")
    @PatchMapping("/orders/{orderId}/status")
    public Map<String, Object> updateStatus(@PathVariable String orderId, @RequestBody Map<String, Object> body) {
        return shopService.adminUpdateStatus(orderId, str(body, "status"));
    }

    private static ShopService.ProductForm form(Map<String, Object> b) {
        return new ShopService.ProductForm(str(b, "name"), str(b, "description"), toInt(b.get("price")),
                toInt(b.get("stock")), str(b, "category"), str(b, "artist"), toLong(b.get("musicId")), str(b, "imageUrl"));
    }
}
