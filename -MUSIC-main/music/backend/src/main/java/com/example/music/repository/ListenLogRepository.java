package com.example.music.repository;

import com.example.music.entity.ListenLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListenLogRepository extends JpaRepository<ListenLog, Long> {

}