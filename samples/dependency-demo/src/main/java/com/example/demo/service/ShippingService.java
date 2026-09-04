package com.example.demo.service;

import com.example.demo.service.PaymentService;

public class ShippingService {
    private final PaymentService paymentService;

    public ShippingService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }
}
