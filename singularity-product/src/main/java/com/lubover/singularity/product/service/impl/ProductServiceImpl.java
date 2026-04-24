package com.lubover.singularity.product.service.impl;

import com.lubover.singularity.product.dto.CreateProductRequest;
import com.lubover.singularity.product.dto.PageResponse;
import com.lubover.singularity.product.dto.ProductView;
import com.lubover.singularity.product.dto.UpdateProductRequest;
import com.lubover.singularity.product.entity.Product;
import com.lubover.singularity.product.exception.BusinessException;
import com.lubover.singularity.product.exception.ErrorCode;
import com.lubover.singularity.product.mapper.ProductMapper;
import com.lubover.singularity.product.service.ProductService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private static final int STATUS_OFFLINE = 0;
    private static final int STATUS_ONLINE = 1;

    private final ProductMapper productMapper;

    public ProductServiceImpl(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductView create(CreateProductRequest request) {
        validateCreateRequest(request);
        Product product = new Product();
        product.setProductId(request.getProductId().trim());
        product.setName(request.getName().trim());
        product.setSubtitle(trimToNull(request.getSubtitle()));
        product.setMainImage(trimToNull(request.getMainImage()));
        product.setCategory(request.getCategory().trim());
        product.setTags(trimToNull(request.getTags()));
        product.setStatus(request.getStatus() == null ? STATUS_ONLINE : request.getStatus());
        product.setPrice(request.getPrice());
        product.setVersion(0L);
        product.setIsDeleted(0);

        try {
            int affected = productMapper.insert(product);
            if (affected <= 0) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "create product failed");
            }
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.PRODUCT_ALREADY_EXISTS, "productId already exists");
        }

        return ProductView.from(productMapper.selectByProductId(product.getProductId()));
    }

    @Override
    public ProductView getByProductId(String productId) {
        validateProductId(productId);
        Product product = productMapper.selectByProductId(productId.trim());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return ProductView.from(product);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProductView update(String productId, UpdateProductRequest request) {
        validateProductId(productId);
        validateUpdateRequest(request);
        Product exists = productMapper.selectByProductId(productId.trim());
        if (exists == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        Product update = new Product();
        update.setProductId(productId.trim());
        update.setName(trimToNull(request.getName()));
        update.setSubtitle(trimToNull(request.getSubtitle()));
        update.setMainImage(trimToNull(request.getMainImage()));
        update.setCategory(trimToNull(request.getCategory()));
        update.setTags(trimToNull(request.getTags()));
        update.setStatus(request.getStatus());
        update.setPrice(request.getPrice());

        int affected = productMapper.updateByProductId(update);
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "update product failed");
        }

        return ProductView.from(productMapper.selectByProductId(productId.trim()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String productId) {
        validateProductId(productId);
        int affected = productMapper.markDeleted(productId.trim());
        if (affected <= 0) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }

    @Override
    public PageResponse<ProductView> list(Integer status, String category, String keyword, int pageNo, int pageSize) {
        validateListParam(status, pageNo, pageSize);

        int normalizedPageNo = Math.max(pageNo, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (normalizedPageNo - 1) * normalizedPageSize;
        String normalizedCategory = trimToNull(category);
        String normalizedKeyword = trimToNull(keyword);

        List<ProductView> views = productMapper.selectList(status, normalizedCategory, normalizedKeyword, offset, normalizedPageSize)
                .stream()
                .map(ProductView::from)
                .collect(Collectors.toList());
        long total = productMapper.countList(status, normalizedCategory, normalizedKeyword);
        return PageResponse.of(views, total, normalizedPageNo, normalizedPageSize);
    }

    private void validateCreateRequest(CreateProductRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM);
        }
        validateProductId(request.getProductId());
        validateName(request.getName());
        validateCategory(request.getCategory());
        validateStatus(request.getStatus());
        validatePrice(request.getPrice());
    }

    private void validateUpdateRequest(UpdateProductRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM);
        }
        if (request.getName() == null
                && request.getSubtitle() == null
                && request.getMainImage() == null
                && request.getCategory() == null
                && request.getTags() == null
                && request.getStatus() == null
                && request.getPrice() == null) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "at least one field must be provided");
        }
        if (request.getName() != null) {
            validateName(request.getName());
        }
        if (request.getCategory() != null) {
            validateCategory(request.getCategory());
        }
        validateStatus(request.getStatus());
        if (request.getPrice() != null) {
            validatePrice(request.getPrice());
        }
    }

    private void validateListParam(Integer status, int pageNo, int pageSize) {
        validateStatus(status);
        if (pageNo <= 0) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "pageNo must be positive");
        }
        if (pageSize <= 0 || pageSize > 100) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "pageSize must be between 1 and 100");
        }
    }

    private void validateProductId(String productId) {
        if (productId == null || productId.trim().isEmpty() || productId.trim().length() > 64) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "productId is invalid");
        }
    }

    private void validateName(String name) {
        if (name == null || name.trim().isEmpty() || name.trim().length() > 128) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "name is invalid");
        }
    }

    private void validateCategory(String category) {
        if (category == null || category.trim().isEmpty() || category.trim().length() > 64) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "category is invalid");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null) {
            return;
        }
        if (status != STATUS_OFFLINE && status != STATUS_ONLINE) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "status is invalid");
        }
    }

    private void validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.REQ_INVALID_PARAM, "price is invalid");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
