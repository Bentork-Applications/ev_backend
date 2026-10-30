package com.bentork.ev_system.controller;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.bentork.ev_system.dto.request.ProductDTO;
import com.bentork.ev_system.dto.response.ProductResponse;
import com.bentork.ev_system.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerImageTest {

    private MockMvc mockMvc;

    @Mock
    private ProductService productService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ProductController controller = new ProductController(productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void testCreateProductWithImageUrl() throws Exception {
        ProductDTO dto = new ProductDTO();
        dto.setName("EV High Capacity Pack");
        dto.setCategory("Battery");
        dto.setImageUrl("https://bentork.com/images/pack.jpg");
        dto.setThumbnail("https://bentork.com/images/pack-thumb.jpg");

        ProductResponse response = new ProductResponse();
        response.setId(50L);
        response.setName("EV High Capacity Pack");
        response.setImageUrl("https://bentork.com/images/pack.jpg");
        response.setThumbnail("https://bentork.com/images/pack-thumb.jpg");

        when(productService.createProduct(any(ProductDTO.class), any())).thenReturn(response);

        mockMvc.perform(post("/api/products/admin/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.imageUrl").value("https://bentork.com/images/pack.jpg"))
                .andExpect(jsonPath("$.thumbnail").value("https://bentork.com/images/pack-thumb.jpg"));
    }

    @Test
    void testGetProductByIdReturnsImages() throws Exception {
        ProductResponse response = new ProductResponse();
        response.setId(77L);
        response.setName("Smart EV Charger 7.4kW");
        response.setImageUrl("https://bentork.com/charger.png");
        response.setThumbnail("https://bentork.com/charger-thumb.png");

        when(productService.getProductById(77L)).thenReturn(response);

        // Test with /api/products/77
        mockMvc.perform(get("/api/products/77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.imageUrl").value("https://bentork.com/charger.png"))
                .andExpect(jsonPath("$.thumbnail").value("https://bentork.com/charger-thumb.png"));

        // Test with /products/77
        mockMvc.perform(get("/products/77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.imageUrl").value("https://bentork.com/charger.png"));
    }

    @Test
    void testUpdateProductImageEndpoint() throws Exception {
        ProductResponse response = new ProductResponse();
        response.setId(88L);
        response.setImageUrl("https://cdn.bentork.com/new.png");
        response.setThumbnail("https://cdn.bentork.com/new-thumb.png");

        when(productService.updateProductImage(eq(88L), eq("https://cdn.bentork.com/new.png"), eq("https://cdn.bentork.com/new-thumb.png"), any()))
                .thenReturn(response);

        Map<String, String> payload = Map.of(
                "imageUrl", "https://cdn.bentork.com/new.png",
                "thumbnail", "https://cdn.bentork.com/new-thumb.png"
        );

        mockMvc.perform(put("/api/products/admin/88/image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("https://cdn.bentork.com/new.png"))
                .andExpect(jsonPath("$.thumbnail").value("https://cdn.bentork.com/new-thumb.png"));
    }
}
