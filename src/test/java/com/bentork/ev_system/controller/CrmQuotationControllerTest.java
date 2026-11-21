package com.bentork.ev_system.controller;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bentork.ev_system.dto.response.PagedResponse;
import com.bentork.ev_system.dto.response.QuotationResponse;
import com.bentork.ev_system.service.QuotationService;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrmQuotationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private QuotationService quotationService;

    @BeforeEach
    void setUp() {
        CrmQuotationController controller = new CrmQuotationController(quotationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/crm/quotations/all should return all quotations")
    void testGetAllQuotations_All() throws Exception {
        QuotationResponse q1 = new QuotationResponse();
        q1.setId(1L);
        q1.setQuoteNumber("QT-001");

        when(quotationService.getAllQuotations()).thenReturn(List.of(q1));

        mockMvc.perform(get("/api/crm/quotations/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].quoteNumber").value("QT-001"));
    }

    @Test
    @DisplayName("GET /api/crm/quotations should return all quotations on root path")
    void testGetAllQuotations_Root() throws Exception {
        when(quotationService.getAllQuotations()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/crm/quotations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/crm/quotations/all?paged=true should return paginated quotations")
    void testGetAllQuotations_Paged() throws Exception {
        PagedResponse<QuotationResponse> pagedResponse = PagedResponse.of(Collections.emptyList(), 0, 20, 0, 0, true);
        when(quotationService.getAllQuotationsPaged(anyInt(), anyInt())).thenReturn(pagedResponse);

        mockMvc.perform(get("/api/crm/quotations/all").param("paged", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/crm/quotations/{id} should return single quotation")
    void testGetQuotationById() throws Exception {
        QuotationResponse q1 = new QuotationResponse();
        q1.setId(5L);
        q1.setQuoteNumber("QT-005");

        when(quotationService.getQuotationById(5L)).thenReturn(q1);

        mockMvc.perform(get("/api/crm/quotations/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5L))
                .andExpect(jsonPath("$.quoteNumber").value("QT-005"));
    }
}
