package com.bentork.ev_system.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.bentork.ev_system.dto.request.CreateOrderDTO;
import com.bentork.ev_system.dto.request.UpdateProductionStatusDTO;
import com.bentork.ev_system.dto.request.UpdateScmDetailsDTO;
import com.bentork.ev_system.dto.response.OrderResponse;
import com.bentork.ev_system.enums.OrderStatus;
import com.bentork.ev_system.enums.ProductionStatus;
import com.bentork.ev_system.model.Order;
import com.bentork.ev_system.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    // ==================== SALES ADMIN METHODS ====================

    /**
     * Create a new order (Sales Admin only).
     */
    public OrderResponse createOrder(CreateOrderDTO dto, String salesAdminEmail) {
        // Parse and validate expected delivery date
        LocalDate deliveryDate;
        try {
            deliveryDate = LocalDate.parse(dto.getExpectedDeliveryDate());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format for expectedDeliveryDate. Use yyyy-MM-dd format.");
        }

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomerName(dto.getCustomerName());
        order.setPiNumber(dto.getPiNumber());
        order.setProductDetails(dto.getProductDetails());
        order.setMobileNumber(dto.getMobileNumber());
        order.setExpectedDeliveryDate(deliveryDate);
        order.setPaymentStatus(dto.getPaymentStatus());
        order.setPriority(dto.getPriority());
        order.setOrderStatus(OrderStatus.SALES_REGISTERED.getValue());
        order.setProductionStatus(ProductionStatus.PENDING.getValue());
        order.setCreatedByAdminEmail(salesAdminEmail);

        Order saved = orderRepository.save(order);
        log.info("Order {} created by Sales Admin {}", saved.getOrderNumber(), salesAdminEmail);

        return mapToResponse(saved);
    }

    /**
     * Get orders created by the current Sales Admin.
     */
    public List<OrderResponse> getSalesAdminOrders(String salesAdminEmail) {
        return orderRepository.findByCreatedByAdminEmailOrderByCreatedAtDesc(salesAdminEmail).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific order detail for Sales Admin (must be the creator).
     */
    public OrderResponse getSalesAdminOrderDetail(Long orderId, String salesAdminEmail) {
        Order order = findOrderById(orderId);
        if (!order.getCreatedByAdminEmail().equals(salesAdminEmail)) {
            throw new IllegalArgumentException("You do not have access to this order");
        }
        return mapToResponse(order);
    }

    /**
     * Update sales-stage fields on an order (Sales Admin only, must be the creator).
     */
    public OrderResponse updateSalesOrder(Long orderId, CreateOrderDTO dto, String salesAdminEmail) {
        Order order = findOrderById(orderId);

        if (!order.getCreatedByAdminEmail().equals(salesAdminEmail)) {
            throw new IllegalArgumentException("You can only edit orders you created");
        }

        // Only allow editing if the order is still in SALES_REGISTERED status
        OrderStatus currentStatus = OrderStatus.fromString(order.getOrderStatus());
        if (currentStatus != OrderStatus.SALES_REGISTERED) {
            throw new IllegalArgumentException("Order can only be edited while in SALES_REGISTERED status. Current status: " + order.getOrderStatus());
        }

        // Parse and validate expected delivery date
        LocalDate deliveryDate;
        try {
            deliveryDate = LocalDate.parse(dto.getExpectedDeliveryDate());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format for expectedDeliveryDate. Use yyyy-MM-dd format.");
        }

        order.setCustomerName(dto.getCustomerName());
        order.setPiNumber(dto.getPiNumber());
        order.setProductDetails(dto.getProductDetails());
        order.setMobileNumber(dto.getMobileNumber());
        order.setExpectedDeliveryDate(deliveryDate);
        order.setPaymentStatus(dto.getPaymentStatus());
        order.setPriority(dto.getPriority());

        Order saved = orderRepository.save(order);
        log.info("Order {} updated by Sales Admin {}", saved.getOrderNumber(), salesAdminEmail);

        return mapToResponse(saved);
    }

    // ==================== PRODUCTION ADMIN METHODS ====================

    /**
     * Get all orders that are in production pipeline (pending or in_progress).
     */
    public List<OrderResponse> getProductionOrders() {
        List<String> productionStatuses = Arrays.asList(
                ProductionStatus.PENDING.getValue(),
                ProductionStatus.IN_PROGRESS.getValue()
        );
        return orderRepository.findByProductionStatusInOrderByCreatedAtDesc(productionStatuses).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific order detail for Production Admin.
     */
    public OrderResponse getProductionOrderDetail(Long orderId) {
        Order order = findOrderById(orderId);
        return mapToResponse(order);
    }

    /**
     * Update production status only (Production Admin only).
     * Enforces valid transitions: PENDING -> IN_PROGRESS -> COMPLETED.
     * When COMPLETED, automatically sets orderStatus to PRODUCTION_COMPLETE.
     */
    public OrderResponse updateProductionStatus(Long orderId, UpdateProductionStatusDTO dto, String productionAdminEmail) {
        Order order = findOrderById(orderId);

        ProductionStatus currentProdStatus = ProductionStatus.fromString(order.getProductionStatus());
        ProductionStatus targetProdStatus = ProductionStatus.fromString(dto.getProductionStatus());

        if (targetProdStatus == null) {
            throw new IllegalArgumentException("Unknown production status: " + dto.getProductionStatus());
        }

        if (!ProductionStatus.isValidTransition(currentProdStatus, targetProdStatus)) {
            throw new IllegalArgumentException("Invalid production status transition from '"
                    + order.getProductionStatus() + "' to '" + dto.getProductionStatus() + "'");
        }

        // Update production status
        order.setProductionStatus(targetProdStatus.getValue());
        order.setProductionUpdatedByEmail(productionAdminEmail);

        // Update overall order status based on production status
        if (targetProdStatus == ProductionStatus.IN_PROGRESS) {
            order.setOrderStatus(OrderStatus.IN_PRODUCTION.getValue());
        } else if (targetProdStatus == ProductionStatus.COMPLETED) {
            order.setOrderStatus(OrderStatus.PRODUCTION_COMPLETE.getValue());
            order.setProductionCompletedAt(LocalDateTime.now());
        }

        Order saved = orderRepository.save(order);
        log.info("Order {} production status updated to '{}' by Production Admin {}",
                orderId, targetProdStatus.getValue(), productionAdminEmail);

        return mapToResponse(saved);
    }

    // ==================== SCM ADMIN METHODS ====================

    /**
     * Get all orders where production is completed (ready for SCM processing).
     */
    public List<OrderResponse> getScmOrders() {
        return orderRepository.findByProductionStatusAndOrderStatusOrderByCreatedAtDesc(
                ProductionStatus.COMPLETED.getValue(),
                OrderStatus.PRODUCTION_COMPLETE.getValue()
        ).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific order detail for SCM Admin.
     */
    public OrderResponse getScmOrderDetail(Long orderId) {
        Order order = findOrderById(orderId);

        // Verify the order is in production-complete state
        if (!ProductionStatus.COMPLETED.matches(order.getProductionStatus())) {
            throw new IllegalArgumentException("Order is not yet production-complete. Current production status: " + order.getProductionStatus());
        }

        return mapToResponse(order);
    }

    /**
     * Fill SCM details and mark order as SCM_COMPLETE (SCM Admin only).
     * Computes totalWarrantyMonths = serviceWarrantyMonths + fullWarrantyMonths.
     */
    public OrderResponse updateScmDetails(Long orderId, UpdateScmDetailsDTO dto, String scmAdminEmail) {
        Order order = findOrderById(orderId);

        // Verify the order is in production-complete state
        OrderStatus currentStatus = OrderStatus.fromString(order.getOrderStatus());
        if (currentStatus != OrderStatus.PRODUCTION_COMPLETE) {
            throw new IllegalArgumentException("SCM details can only be filled when order status is PRODUCTION_COMPLETE. Current status: " + order.getOrderStatus());
        }

        // Fill SCM fields
        order.setBarcode(dto.getBarcode());
        order.setServiceWarrantyMonths(dto.getServiceWarrantyMonths());
        order.setFullWarrantyMonths(dto.getFullWarrantyMonths());
        order.setTotalWarrantyMonths(dto.getServiceWarrantyMonths() + dto.getFullWarrantyMonths());
        order.setTrackingId(dto.getTrackingId());
        order.setScmUpdatedByEmail(scmAdminEmail);
        order.setOrderStatus(OrderStatus.SCM_COMPLETE.getValue());
        order.setScmCompletedAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);
        log.info("Order {} SCM details filled by SCM Admin {}. Total warranty: {} months",
                orderId, scmAdminEmail, saved.getTotalWarrantyMonths());

        return mapToResponse(saved);
    }

    /**
     * Mark an SCM-complete order as dispatched (SCM Admin only).
     */
    public OrderResponse markDispatched(Long orderId, String scmAdminEmail) {
        Order order = findOrderById(orderId);

        OrderStatus currentStatus = OrderStatus.fromString(order.getOrderStatus());
        if (currentStatus != OrderStatus.SCM_COMPLETE) {
            throw new IllegalArgumentException("Order can only be dispatched when status is SCM_COMPLETE. Current status: " + order.getOrderStatus());
        }

        order.setOrderStatus(OrderStatus.DISPATCHED.getValue());
        order.setDispatchedAt(LocalDateTime.now());
        order.setScmUpdatedByEmail(scmAdminEmail);

        Order saved = orderRepository.save(order);
        log.info("Order {} marked as DISPATCHED by SCM Admin {}", orderId, scmAdminEmail);

        return mapToResponse(saved);
    }

    // ==================== SHARED METHODS ====================

    /**
     * Get all orders (for ADMIN role — super admin view).
     */
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific order detail (any authorized admin).
     */
    public OrderResponse getOrderDetail(Long orderId) {
        return mapToResponse(findOrderById(orderId));
    }

    // ==================== PRIVATE HELPERS ====================

    private Order findOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));
    }

    private String generateOrderNumber() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%04d", (int) (Math.random() * 10000));
        return "ORD-" + datePart + "-" + randomPart;
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());

        // Sales stage fields
        response.setCustomerName(order.getCustomerName());
        response.setPiNumber(order.getPiNumber());
        response.setProductDetails(order.getProductDetails());
        response.setMobileNumber(order.getMobileNumber());
        response.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setPriority(order.getPriority());

        // Lifecycle status
        response.setOrderStatus(order.getOrderStatus());

        // Production stage
        response.setProductionStatus(order.getProductionStatus());

        // SCM stage
        response.setBarcode(order.getBarcode());
        response.setServiceWarrantyMonths(order.getServiceWarrantyMonths());
        response.setFullWarrantyMonths(order.getFullWarrantyMonths());
        response.setTotalWarrantyMonths(order.getTotalWarrantyMonths());
        response.setTrackingId(order.getTrackingId());

        // Audit fields
        response.setCreatedByAdminEmail(order.getCreatedByAdminEmail());
        response.setProductionUpdatedByEmail(order.getProductionUpdatedByEmail());
        response.setScmUpdatedByEmail(order.getScmUpdatedByEmail());

        // Timestamps
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        response.setProductionCompletedAt(order.getProductionCompletedAt());
        response.setScmCompletedAt(order.getScmCompletedAt());
        response.setDispatchedAt(order.getDispatchedAt());

        return response;
    }
}
