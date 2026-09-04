package com.example.demo.service;

import com.example.demo.repository.PaymentRepository;
import com.example.demo.user.UserService;

public class PaymentService {
    private final PaymentRepository repository;
    private final OrderService orderService;

    public PaymentService(PaymentRepository repository, OrderService orderService) {
        this.repository = repository;
        this.orderService = orderService;
    }

    public void process(UserService userService) {
        userService.load();
        repository.save();
        repository.save();
        orderService.placeOrder();
    }
}
