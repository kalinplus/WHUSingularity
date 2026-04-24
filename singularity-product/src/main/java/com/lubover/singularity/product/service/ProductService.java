package com.lubover.singularity.product.service;

import com.lubover.singularity.product.dto.CreateProductRequest;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductView;
import com.lubover.singularity.product.dto.UpdateProductRequest;

public interface ProductService {

    ProductView create(CreateProductRequest request);

    ProductView getByProductId(String productId);

    ProductView update(String productId, UpdateProductRequest request);

    void delete(String productId);

    PageResponse<ProductView> list(Integer status, String category, String keyword, int pageNo, int pageSize);
}
