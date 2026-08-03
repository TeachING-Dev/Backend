package com.teaching.backend.domain.payment.entity;

import com.teaching.backend.domain.payment.enums.PaymentStatus;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "payments")
public class Payment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 카카오페이 partner_order_id 로 함께 넘기는 자체 주문 식별자 (success/cancel/fail 콜백은 이 값만 받는다) */
    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    /** 결제 준비(ready) 응답으로 받는 카카오페이 거래 고유번호. approve 호출 시 필요 */
    @Column(name = "tid", length = 64)
    private String tid;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Builder(access = AccessLevel.PRIVATE)
    private Payment(User user, String orderId, String tid, Integer amount, PaymentStatus status) {
        this.user = user;
        this.orderId = orderId;
        this.tid = tid;
        this.amount = amount;
        this.status = status;
    }

    public static Payment ready(User user, String orderId, String tid, Integer amount) {
        return Payment.builder()
                .user(user)
                .orderId(orderId)
                .tid(tid)
                .amount(amount)
                .status(PaymentStatus.READY)
                .build();
    }

    public void approve() {
        this.status = PaymentStatus.APPROVED;
    }

    public void cancel() {
        this.status = PaymentStatus.CANCELED;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }
}
