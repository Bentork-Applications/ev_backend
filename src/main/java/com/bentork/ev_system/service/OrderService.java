package com.bentork.ev_system.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bentork.ev_system.dto.request.CreateOrderDTO;
import com.bentork.ev_system.dto.request.UpdateProductionStatusDTO;
import com.bentork.ev_system.dto.request.UpdateScmDetailsDTO;
import com.bentork.ev_system.dto.response.OrderResponse;
import com.bentork.ev_system.enums.OrderStatus;
import com.bentork.ev_system.enums.ProductionStatus;
import com.bentork.ev_system.model.BatteryData;
import com.bentork.ev_system.model.Order;
import com.bentork.ev_system.model.User;
import com.bentork.ev_system.repository.BatteryDataRepository;
import com.bentork.ev_system.repository.OrderRepository;
import com.bentork.ev_system.repository.UserRepository;
import com.bentork.ev_system.service.interfaces.IUserNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final BatteryDataRepository batteryDataRepository;
    private final UserRepository userRepository;
    private final IUserNotificationService userNotificationService;

    // ==================== SALES ADMIN METHODS ====================

    /**
     * Create a new order (Sales Admin only).
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderDTO dto, String salesAdminEmail) {
        // Parse and validate expected delivery date
        LocalDate deliveryDate;
        try {
            deliveryDate = LocalDate.parse(dto.getExpectedDeliveryDate());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format for expectedDeliveryDate. Use yyyy-MM-dd format.");
        }

        // Validate assigned user exists
        User assignedUser = userRepository.findById(dto.getAssignedUserId())
                .orElseThrow(() -> new IllegalArgumentException("Assigned user ID not found in database"));

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setAssignedUserId(assignedUser.getId());
        order.setCustomerName(dto.getCustomerName());
        order.setPiNumber(dto.getPiNumber());
        order.setProductDetails(dto.getProductDetails());
        order.setQuantity(dto.getQuantity());
        order.setMobileNumber(dto.getMobileNumber());
        order.setExpectedDeliveryDate(deliveryDate);
        order.setPaymentStatus(dto.getPaymentStatus());
        order.setPriority(dto.getPriority());
        order.setOrderStatus(OrderStatus.SALES_REGISTERED.getValue());
        order.setProductionStatus(ProductionStatus.CONFIRM.getValue());
        order.setCreatedByAdminEmail(salesAdminEmail);

        Order saved = orderRepository.save(order);
        log.info("Order {} created by Sales Admin {}", saved.getOrderNumber(), salesAdminEmail);

        userNotificationService.createNotification(assignedUser.getId(),
                "Order Created",
                "Your order " + saved.getOrderNumber() + " has been registered.",
                "ORDER_UPDATE");

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
    @Transactional
    public OrderResponse updateSalesOrder(Long orderId, CreateOrderDTO dto, String salesAdminEmail) {
        Order order = findOrderById(orderId);

        // Verify permissions and status
        if (!order.getCreatedByAdminEmail().equals(salesAdminEmail)) {
            throw new IllegalArgumentException("You can only edit orders you created");
        }
        if (!OrderStatus.SALES_REGISTERED.getValue().equals(order.getOrderStatus())) {
            throw new IllegalArgumentException("Cannot update order that has moved past sales stage");
        }

        // Validate assigned user exists
        User assignedUser = userRepository.findById(dto.getAssignedUserId())
                .orElseThrow(() -> new IllegalArgumentException("Assigned user ID not found in database"));

        // Parse and validate expected delivery date
        LocalDate deliveryDate;
        try {
            deliveryDate = LocalDate.parse(dto.getExpectedDeliveryDate());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format for expectedDeliveryDate. Use yyyy-MM-dd format.");
        }

        order.setAssignedUserId(assignedUser.getId());
        order.setCustomerName(dto.getCustomerName());
        order.setPiNumber(dto.getPiNumber());
        order.setProductDetails(dto.getProductDetails());
        order.setQuantity(dto.getQuantity());
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
     * Get all orders that are in production pipeline (confirm, in_progress, testing).
     */
    public List<OrderResponse> getProductionOrders() {
        List<String> productionStatuses = Arrays.asList(
                ProductionStatus.CONFIRM.getValue(),
                ProductionStatus.IN_PROGRESS.getValue(),
                ProductionStatus.TESTING.getValue()
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
        if (targetProdStatus == ProductionStatus.IN_PROGRESS || targetProdStatus == ProductionStatus.TESTING) {
            order.setOrderStatus(OrderStatus.IN_PRODUCTION.getValue());
        } else if (targetProdStatus == ProductionStatus.COMPLETED) {
            order.setOrderStatus(OrderStatus.PRODUCTION_COMPLETE.getValue());
            order.setProductionCompletedAt(LocalDateTime.now());
        }

        Order saved = orderRepository.save(order);
        log.info("Order {} production status updated to '{}' by Production Admin {}",
                orderId, targetProdStatus.getValue(), productionAdminEmail);

        userNotificationService.createNotification(saved.getAssignedUserId(),
                "Production Update",
                "Your order " + saved.getOrderNumber() + " is now in production status: " + targetProdStatus.getValue() + ".",
                "ORDER_UPDATE");

        return mapToResponse(saved);
    }

    // ==================== SCM ADMIN METHODS ====================

    /**
     * Get all orders where production is completed (ready for SCM processing, or already processed/dispatched).
     */
    public List<OrderResponse> getScmOrders() {
        List<String> scmStatuses = Arrays.asList(
                OrderStatus.PRODUCTION_COMPLETE.getValue(),
                OrderStatus.SCM_COMPLETE.getValue(),
                OrderStatus.DISPATCHED.getValue()
        );
        return orderRepository.findByOrderStatusInOrderByCreatedAtDesc(scmStatuses).stream()
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
     * Also creates BatteryData records for each barcode.
     */
    @Transactional
    public OrderResponse updateScmDetails(Long orderId, UpdateScmDetailsDTO dto, String scmAdminEmail) {
        Order order = findOrderById(orderId);

        // Verify the order is in production-complete state
        OrderStatus currentStatus = OrderStatus.fromString(order.getOrderStatus());
        if (currentStatus != OrderStatus.PRODUCTION_COMPLETE) {
            throw new IllegalArgumentException("SCM details can only be filled when order status is PRODUCTION_COMPLETE. Current status: " + order.getOrderStatus());
        }

        // Validate quantity matches barcodes
        int expectedQuantity = (order.getQuantity() != null) ? order.getQuantity() : 1;
        if (dto.getBarcodes().size() != expectedQuantity) {
            throw new IllegalArgumentException("Number of barcodes provided (" + dto.getBarcodes().size() + 
                    ") does not match the order quantity (" + expectedQuantity + ").");
        }

        // Fill SCM fields
        order.setInvoiceNumber(dto.getInvoiceNumber());
        order.setBarcode(String.join(",", dto.getBarcodes()));
        order.setServiceWarrantyMonths(dto.getServiceWarrantyMonths());
        order.setFullWarrantyMonths(dto.getFullWarrantyMonths());
        order.setTotalWarrantyMonths(dto.getServiceWarrantyMonths() + dto.getFullWarrantyMonths());
        order.setTrackingId(dto.getTrackingId());
        order.setScmUpdatedByEmail(scmAdminEmail);
        order.setOrderStatus(OrderStatus.SCM_COMPLETE.getValue());
        order.setScmCompletedAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);

        // Create BatteryData records
        List<BatteryData> batteryDataList = new ArrayList<>();
        for (String barcode : dto.getBarcodes()) {
            BatteryData batteryData = new BatteryData();
            batteryData.setCustomerName(order.getCustomerName());
            batteryData.setProductDetails(order.getProductDetails());
            batteryData.setInvoiceNumber(dto.getInvoiceNumber());
            batteryData.setBarcode(barcode);
            batteryData.setWarrantyStartDate(LocalDate.now());
            batteryData.setWarrantyEndDate(LocalDate.now().plusMonths(order.getTotalWarrantyMonths()));
            batteryData.setCreatedByAdminEmail(scmAdminEmail);
            batteryDataList.add(batteryData);
        }
        batteryDataRepository.saveAll(batteryDataList);

        log.info("Order {} SCM details filled by SCM Admin {}. Created {} BatteryData records.",
                orderId, scmAdminEmail, expectedQuantity);

        userNotificationService.createNotification(saved.getAssignedUserId(),
                "SCM Processing Complete",
                "Your order " + saved.getOrderNumber() + " has been processed for shipping.",
                "ORDER_UPDATE");

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

        userNotificationService.createNotification(saved.getAssignedUserId(),
                "Order Dispatched",
                "Your order " + saved.getOrderNumber() + " has been dispatched!",
                "ORDER_UPDATE");

        return mapToResponse(saved);
    }

    // ==================== SHARED METHODS ====================

    /**
     * Get all orders assigned to the currently logged-in user.
     */
    public List<OrderResponse> getUserOrders(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        return orderRepository.findByAssignedUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific order detail for the user (must match assigned user ID).
     */
    public OrderResponse getUserOrderDetail(Long orderId, String userEmail) {
        Order order = findOrderById(orderId);
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.getId().equals(order.getAssignedUserId())) {
            throw new IllegalArgumentException("You do not have access to this order.");
        }

        return mapToResponse(order);
    }

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
        response.setAssignedUserId(order.getAssignedUserId());

        // Sales stage fields
        response.setCustomerName(order.getCustomerName());
        response.setPiNumber(order.getPiNumber());
        response.setProductDetails(order.getProductDetails());
        response.setQuantity(order.getQuantity());
        response.setMobileNumber(order.getMobileNumber());
        response.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
        response.setPaymentStatus(order.getPaymentStatus());
        response.setPriority(order.getPriority());

        // Lifecycle status
        response.setOrderStatus(order.getOrderStatus());

        // Production stage
        response.setProductionStatus(order.getProductionStatus());

        // SCM stage
        response.setInvoiceNumber(order.getInvoiceNumber());
        if (order.getBarcode() != null && !order.getBarcode().isEmpty()) {
            response.setBarcodes(Arrays.asList(order.getBarcode().split(",")));
        } else {
            response.setBarcodes(new ArrayList<>());
        }
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
