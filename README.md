# my-enterprise-spring-proj-one
Mono repo for my learning

## Gateway Service

The Gateway Service acts as the single entry point for all client requests to the platform. It is responsible for routing incoming API requests to the appropriate microservices while providing cross-cutting capabilities such as authentication, authorization, request logging, rate limiting, distributed tracing, and load balancing. By centralizing these concerns, the gateway simplifies client interactions and shields internal services from direct external access, improving both security and maintainability.

## Discovery Service

The Discovery Service provides dynamic service registration and discovery within the microservice ecosystem. Each service automatically registers itself on startup and periodically sends heartbeat information to indicate its availability. Other services use the discovery registry to locate instances without relying on hardcoded IP addresses or hostnames, enabling seamless scaling, fault tolerance, and dynamic deployment in cloud and Kubernetes environments.

## Customer Service

The Customer Service manages all customer-related information, including personal details, contact information, delivery addresses, preferences, and loyalty data. It serves as the authoritative source of customer information for the platform while exposing REST APIs for customer management. This service owns its database and ensures customer data remains isolated from other business domains.

## Product Service

The Product Service maintains the product catalog and provides detailed information about products, including descriptions, pricing, categories, specifications, availability, and images. Since product information is frequently accessed, this service can leverage caching mechanisms such as Redis to improve performance and reduce database load. It acts as the central source of truth for all product-related information.

## Order Service

The Order Service orchestrates the complete order lifecycle, from order creation to completion or cancellation. It coordinates interactions with inventory, payment, shipping, and notification services while maintaining the current status of every order. As the core business service of the platform, it ensures that customer orders are processed reliably through a combination of synchronous APIs and asynchronous event-driven communication.

## Inventory Service

The Inventory Service manages product stock across warehouses and ensures inventory consistency throughout the order lifecycle. It reserves stock during order placement, releases stock for failed or cancelled orders, and updates inventory after successful purchases. By isolating inventory management into its own service, the platform can independently scale and optimize stock operations without impacting other business functions.

## Payment Service

The Payment Service handles payment processing for customer orders by integrating with external payment providers. It manages payment transactions, records transaction status, and publishes payment events that trigger downstream business processes. The service stores transaction metadata while ensuring that sensitive payment information is never persisted within the platform, following industry security best practices.

## Shipping Service

The Shipping Service is responsible for managing shipment creation, courier assignment, package tracking, and delivery status updates. Once an order has been successfully paid, the service initiates the shipping process and maintains the shipment lifecycle until delivery. It provides tracking information that can be consumed by both customers and internal business services.

## Notification Service

The Notification Service provides centralized communication capabilities for the platform by delivering emails, SMS messages, push notifications, or other communication channels. Instead of being tightly coupled with business services, it subscribes to business events such as order creation, payment confirmation, or shipment updates, allowing notifications to be processed asynchronously and independently.

## Analytics Service

The Analytics Service collects and processes business events generated across the platform to produce operational dashboards, reports, and analytical insights. By consuming events from Kafka, it enables real-time business intelligence without impacting transactional workloads. The processed data can later be integrated with modern analytics platforms such as Databricks for advanced reporting, machine learning, and predictive analytics.


## Overall Architecture

```

                    Web / Mobile App
                           │
                           ▼
                  Spring Cloud Gateway
                           │
         ┌─────────────────┼──────────────────┐
         ▼                 ▼                  ▼
   Customer Service   Product Service    Order Service
                                               │
                         ┌─────────────────────┴─────────────────────┐
                         ▼                     ▼                     ▼
                Inventory Service     Payment Service      Shipping Service
                         │                     │                     │
                         └──────────────Kafka Events─────────────────┘
                                              │
                                 ┌────────────┴────────────┐
                                 ▼                         ▼
                      Notification Service      Analytics Service

```