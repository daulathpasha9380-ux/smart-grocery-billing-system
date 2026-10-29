package com.billingsystem.billing_system;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ProductImageWebConfig implements WebMvcConfigurer {
    private final ProductImageService productImageService;

    public ProductImageWebConfig(ProductImageService productImageService) {
        this.productImageService = productImageService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/product-images/**")
                .addResourceLocations(productImageService.storageDirectory().toUri().toString());
    }
}
