package com.example.demo.invoice;

import com.example.demo.service.PaymentService;

public class InvoiceService {
    private final PaymentService paymentService;

    public InvoiceService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public void invoice() {
        paymentService.process(null);
    }
}
