package com.example.music.repository;

import com.example.music.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 특정 유저의 결제 내역 조회
    List<Payment> findByUser_Id(Long userId);

    // 결제 내역 화면 - 최신순
    List<Payment> findByUser_IdOrderByPaidAtDesc(Long userId);

    // 주문 번호로 결제 내역 단건 조회
    Optional<Payment> findByOrderId(String orderId);
}