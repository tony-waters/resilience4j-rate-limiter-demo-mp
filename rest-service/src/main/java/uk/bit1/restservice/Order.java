package uk.bit1.restservice;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String product;

    private int quantity;

    private String customerEmail;

    @Enumerated(EnumType.STRING)
    private NotificationOutcome notificationOutcome;

    private Instant createdAt;

    protected Order() {
    }

    public Order(String product, int quantity, String customerEmail) {
        this.product = product;
        this.quantity = quantity;
        this.customerEmail = customerEmail;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public NotificationOutcome getNotificationOutcome() {
        return notificationOutcome;
    }

    public void setNotificationOutcome(NotificationOutcome notificationOutcome) {
        this.notificationOutcome = notificationOutcome;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
