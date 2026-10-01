package com.example.music.repository;

import com.example.music.entity.ShopOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShopOrderRepository extends JpaRepository<ShopOrder, Long> {

    @EntityGraph(attributePaths = {"user", "product"})
    Optional<ShopOrder> findByOrderId(String orderId);

    List<ShopOrder> findByUser_IdAndStatusNotOrderByCreatedAtDesc(Long userId, String excludedStatus);

    @EntityGraph(attributePaths = "user")
    List<ShopOrder> findTop200ByStatusInOrderByCreatedAtDesc(Collection<String> statuses);

    /** 결제창에서 그만둔 오래된 결제 대기 주문 정리 */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("delete from ShopOrder o where o.status = 'PENDING' and o.createdAt < :before")
    int deletePendingBefore(@org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before);
}
