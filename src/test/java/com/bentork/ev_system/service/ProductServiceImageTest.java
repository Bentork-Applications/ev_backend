package com.bentork.ev_system.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bentork.ev_system.dto.request.ProductDTO;
import com.bentork.ev_system.dto.response.ProductResponse;
import com.bentork.ev_system.model.Product;
import com.bentork.ev_system.repository.ProductRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImageTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    void testCreateProductWithImageUrlAndThumbnail() {
        ProductDTO dto = new ProductDTO();
        dto.setName("Lithium 48V Battery");
        dto.setCategory("Battery");
        dto.setImageUrl("https://bentork.com/images/products/bat-48v-full.png");
        dto.setThumbnail("https://bentork.com/images/products/bat-48v-thumb.png");

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        ProductResponse response = productService.createProduct(dto, "admin@bentork.com");

        assertNotNull(response);
        assertEquals("Lithium 48V Battery", response.getName());
        assertEquals("https://bentork.com/images/products/bat-48v-full.png", response.getImageUrl());
        assertEquals("https://bentork.com/images/products/bat-48v-thumb.png", response.getThumbnail());
    }

    @Test
    void testCreateProductThumbnailFallbackToImageUrl() {
        ProductDTO dto = new ProductDTO();
        dto.setName("Fast Charger 60V");
        dto.setCategory("Charger");
        dto.setImageUrl("https://bentork.com/images/products/charger-60v.png");
        // No thumbnail provided

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(101L);
            return saved;
        });

        ProductResponse response = productService.createProduct(dto, "admin@bentork.com");

        assertNotNull(response);
        assertEquals("https://bentork.com/images/products/charger-60v.png", response.getImageUrl());
        assertEquals("https://bentork.com/images/products/charger-60v.png", response.getThumbnail());
    }

    @Test
    void testUpdateProductImageUrls() {
        Product existing = new Product();
        existing.setId(200L);
        existing.setName("Existing Battery");
        existing.setImageUrl("https://old.url/image.png");
        existing.setThumbnail("https://old.url/thumb.png");

        when(productRepository.findById(200L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductDTO updateDTO = new ProductDTO();
        updateDTO.setImageUrl("https://new.url/image.png");
        updateDTO.setThumbnail("https://new.url/thumb.png");

        ProductResponse response = productService.updateProduct(200L, updateDTO, "admin@bentork.com");

        assertNotNull(response);
        assertEquals("https://new.url/image.png", response.getImageUrl());
        assertEquals("https://new.url/thumb.png", response.getThumbnail());
    }

    @Test
    void testUpdateProductImageDirectly() {
        Product existing = new Product();
        existing.setId(300L);
        existing.setName("Station Connector");

        when(productRepository.findById(300L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.updateProductImage(
                300L,
                "https://cdn.bentork.com/connector.jpg",
                "https://cdn.bentork.com/connector-thumb.jpg",
                "admin@bentork.com"
        );

        assertNotNull(response);
        assertEquals("https://cdn.bentork.com/connector.jpg", response.getImageUrl());
        assertEquals("https://cdn.bentork.com/connector-thumb.jpg", response.getThumbnail());
    }
}
