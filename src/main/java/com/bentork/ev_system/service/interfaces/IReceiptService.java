package com.bentork.ev_system.service.interfaces;

import com.bentork.ev_system.model.Charger;
import com.bentork.ev_system.model.Plan;
import com.bentork.ev_system.model.Receipt;
import com.bentork.ev_system.model.Session;
import com.bentork.ev_system.model.User;

import java.math.BigDecimal;

/**
 * Interface for receipt creation, payment, and finalization.
 */
public interface IReceiptService {
    Receipt createReceipt(User user, Plan plan, Charger charger, BigDecimal selectedKwh);
    Receipt payReceipt(Long receiptId, String boxId);
    void finalizeReceipt(Session session, BigDecimal finalCost);
    Receipt save(Receipt receipt);
}
