package uk.bit1.restservice;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final EmailNotificationClient emailNotificationClient;

    public OrderController(OrderRepository orderRepository, EmailNotificationClient emailNotificationClient) {
        this.orderRepository = orderRepository;
        this.emailNotificationClient = emailNotificationClient;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request) {
        Order order = new Order(request.product(), request.quantity(), request.customerEmail());
        order = orderRepository.save(order);

        NotificationOutcome outcome = emailNotificationClient.notify(order);
        order.setNotificationOutcome(outcome);
        order = orderRepository.save(order);

        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<Order> listOrders() {
        return orderRepository.findAll();
    }
}
