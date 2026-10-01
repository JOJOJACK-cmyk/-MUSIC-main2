package com.example.music.repository;

import com.example.music.entity.Product;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "music")
    List<Product> findByActiveTrueOrderByIdDesc();

    @EntityGraph(attributePaths = "music")
    List<Product> findAllByOrderByIdDesc();

    /** 재고 차감 (재고가 충분할 때만). 반영된 행 수가 0 이면 품절. */
    @Modifying // clearAutomatically 를 쓰면 같은 트랜잭션의 주문 엔티티가 분리돼 상태 변경이 저장되지 않는다
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
    int decreaseStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying // clearAutomatically 를 쓰면 같은 트랜잭션의 주문 엔티티가 분리돼 상태 변경이 저장되지 않는다
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int increaseStock(@Param("id") Long id, @Param("qty") int qty);
}
