package com.example.music.repository;

import com.example.music.entity.Donation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DonationRepository extends JpaRepository<Donation, Long> {

    @EntityGraph(attributePaths = {"donor", "broadcast"})
    Optional<Donation> findByOrderId(String orderId);

    @EntityGraph(attributePaths = "donor")
    List<Donation> findTop50ByRecipient_IdAndStatusOrderByPaidAtDesc(Long recipientId, String status);

    @Query("select coalesce(sum(d.amount), 0) from Donation d where d.recipient.id = :recipientId and d.status = 'DONE'")
    long sumReceived(@Param("recipientId") Long recipientId);
}
