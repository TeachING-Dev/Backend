package com.teaching.backend.tag.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Builder(access = AccessLevel.PRIVATE)
    private Tag(String name) {
        this.name = name;
    }

    public static Tag create(String name) {
        return Tag.builder()
                .name(name)
                .build();
    }
}
