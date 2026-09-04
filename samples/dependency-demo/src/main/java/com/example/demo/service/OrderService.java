package com.example.demo.service;

import com.example.demo.invoice.InvoiceService;

public class OrderService {
    private final InvoiceService invoiceService;

    public OrderService(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    public void placeOrder() {
        invoiceService.invoice();
    }
}
