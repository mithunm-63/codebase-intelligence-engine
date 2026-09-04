package com.example.demo.api;

import com.example.demo.repository.PaymentRepository;
import com.example.demo.service.PaymentService;
import com.example.demo.user.UserService;
import com.example.demo.service.OrderService;

public class PaymentController extends PaymentService {
    public PaymentController(PaymentRepository repository, OrderService orderService) {
        super(repository, orderService);
    }

    public void submit(UserService userService) {
        process(userService);
    }
}
