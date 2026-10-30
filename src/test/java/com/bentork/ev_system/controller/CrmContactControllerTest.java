package com.bentork.ev_system.controller;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bentork.ev_system.dto.request.CreateContactDTO;
import com.bentork.ev_system.dto.request.UpdateContactDTO;
import com.bentork.ev_system.dto.response.ContactResponse;
import com.bentork.ev_system.service.SalesContactService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class CrmContactControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SalesContactService contactService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        CrmContactController controller = new CrmContactController(contactService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testPutContact_Success() throws Exception {
        UpdateContactDTO dto = new UpdateContactDTO();
        dto.setName("Jane Doe");
        dto.setPhone("9876543210");
        dto.setDesignation("General Manager");
        dto.setIsPrimary(true);

        ContactResponse response = new ContactResponse();
        response.setId(10L);
        response.setName("Jane Doe");
        response.setPhone("9876543210");
        response.setDesignation("General Manager");
        response.setPrimary(true);

        when(contactService.updateContact(eq(10L), any(UpdateContactDTO.class))).thenReturn(response);

        mockMvc.perform(put("/api/crm/contacts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.phone").value("9876543210"))
                .andExpect(jsonPath("$.isPrimary").value(true));
    }

    @Test
    void testPutContact_NotFound() throws Exception {
        UpdateContactDTO dto = new UpdateContactDTO();
        dto.setName("Jane Doe");

        when(contactService.updateContact(eq(99L), any(UpdateContactDTO.class)))
                .thenThrow(new IllegalArgumentException("Contact not found with ID: 99"));

        mockMvc.perform(put("/api/crm/contacts/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Contact not found with ID: 99"));
    }

    @Test
    void testPutContact_DuplicatePhoneBadRequest() throws Exception {
        UpdateContactDTO dto = new UpdateContactDTO();
        dto.setPhone("9876543210");

        when(contactService.updateContact(eq(10L), any(UpdateContactDTO.class)))
                .thenThrow(new IllegalArgumentException("Contact with phone '9876543210' already exists"));

        mockMvc.perform(put("/api/crm/contacts/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Contact with phone '9876543210' already exists"));
    }

    @Test
    void testDeleteContact_Success() throws Exception {
        doNothing().when(contactService).deleteContact(10L);

        mockMvc.perform(delete("/api/crm/contacts/10"))
                .andExpect(status().isOk())
                .andExpect(content().string("Contact deleted successfully"));

        verify(contactService).deleteContact(10L);
    }

    @Test
    void testDeleteContact_NotFound() throws Exception {
        doThrow(new IllegalArgumentException("Contact not found with ID: 99"))
                .when(contactService).deleteContact(99L);

        mockMvc.perform(delete("/api/crm/contacts/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Contact not found with ID: 99"));
    }

    @Test
    void testGetContactById_Success() throws Exception {
        ContactResponse response = new ContactResponse();
        response.setId(10L);
        response.setName("Jane Doe");

        when(contactService.getContactById(10L)).thenReturn(response);

        mockMvc.perform(get("/api/crm/contacts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    @Test
    void testGetContactById_NotFound() throws Exception {
        when(contactService.getContactById(99L))
                .thenThrow(new IllegalArgumentException("Contact not found with ID: 99"));

        mockMvc.perform(get("/api/crm/contacts/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testGetAllContacts() throws Exception {
        when(contactService.getAllContacts()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/crm/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}

