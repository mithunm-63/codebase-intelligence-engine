# Dependency Demo Repository

Small Java project for exercising Codebase Intelligence Engine Phase 4.

Expected dependency patterns include:

- `PaymentController` extends `PaymentService`
- `PaymentService` has a field dependency on `PaymentRepository`
- `PaymentService` receives and calls `UserService`
- `OrderService` depends on `InvoiceService`
- `InvoiceService` depends on `PaymentService`
- repeated dependencies are aggregated with occurrence counts

Upload the ZIP of this directory or publish it as a public GitHub repository and analyze it from the application.
