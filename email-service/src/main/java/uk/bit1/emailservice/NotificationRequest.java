package uk.bit1.emailservice;

public record NotificationRequest(
        Long orderId,
        String recipientEmail,
        String product,
        int quantity
) {
}
