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
}
