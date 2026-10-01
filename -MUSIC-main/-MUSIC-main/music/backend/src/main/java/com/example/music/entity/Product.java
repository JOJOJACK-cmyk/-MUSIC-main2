package com.example.music.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * 스토어 상품 (음반 / 굿즈).
 * 곡(music) 또는 아티스트(artist)와 연결해 두면 그 곡을 듣는 중에 "관련 상품"으로 노출된다.
 * 삭제 대신 active=false 로 내린다 (지난 주문 내역이 상품을 참조하므로).
 */
@Entity
@Table(name = "product", indexes = @Index(name = "idx_product_active", columnList = "active"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    public static final String ALBUM = "ALBUM";
    public static final String MERCH = "MERCH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false, length = 20)
    private String category;

    // 관련 아티스트 (이 아티스트의 곡을 들을 때 노출)
    @Column(length = 100)
    private String artist;

    // 관련 곡 (선택). 곡이 지워지면 연결만 끊긴다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "music_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Music music;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Product(String name, String description, int price, int stock, String category,
                   String artist, Music music, String imageUrl) {
        update(name, description, price, stock, category, artist, music, imageUrl);
        this.active = true;
        this.createdAt = LocalDateTime.now();
    }

    public void update(String name, String description, int price, int stock, String category,
                       String artist, Music music, String imageUrl) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.category = category;
        this.artist = artist;
        this.music = music;
        this.imageUrl = imageUrl;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
