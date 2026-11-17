package com.hiyori.mall.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hiyori.mall.catalog.dto.CatalogDtos.AdminProductVO;
import com.hiyori.mall.catalog.dto.ProductSaveRequest;
import com.hiyori.mall.catalog.entity.Product;
import com.hiyori.mall.catalog.mapper.CategoryMapper;
import com.hiyori.mall.catalog.mapper.ProductMapper;
import com.hiyori.mall.catalog.service.CatalogService;
import com.hiyori.mall.common.BizException;
import com.hiyori.mall.common.ErrorCode;
import com.hiyori.mall.common.PageResult;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 老板在后台维护商品；任何写操作都会清掉首页缓存，顾客下次刷新就能看到 */
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;

    public PageResult<AdminProductVO> page(String categoryId, String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<Product> q = new LambdaQueryWrapper<Product>()
                .eq(StringUtils.hasText(categoryId), Product::getCategoryId, categoryId)
                .eq(status != null, Product::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w.like(Product::getName, keyword)
                        .or().like(Product::getNameEn, keyword)
                        .or().eq(Product::getBarcode, keyword))
                .orderByAsc(Product::getCategoryId)
                .orderByAsc(Product::getSort);
        return PageResult.of(productMapper.selectPage(new Page<>(page, size), q), AdminProductVO::of);
    }

    @Transactional
    @CacheEvict(cacheNames = {CatalogService.CACHE_HOME, CatalogService.CACHE_CATEGORIES}, allEntries = true)
    public AdminProductVO create(ProductSaveRequest req) {
        requireCategory(req.cat());
        Product p = new Product();
        apply(p, req);
        if (p.getStatus() == null) p.setStatus(Product.STATUS_ON);
        if (p.getSort() == null) p.setSort(0);
        productMapper.insert(p);
        return AdminProductVO.of(p);
    }

    @Transactional
    @CacheEvict(cacheNames = {CatalogService.CACHE_HOME, CatalogService.CACHE_CATEGORIES}, allEntries = true)
    public AdminProductVO update(String id, ProductSaveRequest req) {
        Product p = require(id);
        requireCategory(req.cat());
        apply(p, req);
        productMapper.updateById(p);
        return AdminProductVO.of(p);
    }

    @Transactional
    @CacheEvict(cacheNames = {CatalogService.CACHE_HOME, CatalogService.CACHE_CATEGORIES}, allEntries = true)
    public void changeStatus(String id, int status) {
        Product p = require(id);
        p.setStatus(status);
        productMapper.updateById(p);
    }

    @Transactional
    @CacheEvict(cacheNames = {CatalogService.CACHE_HOME, CatalogService.CACHE_CATEGORIES}, allEntries = true)
    public void delete(String id) {
        require(id);
        productMapper.deleteById(id); // @TableLogic：逻辑删除，数据还能找回
    }

    private Product require(String id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return p;
    }

    private void requireCategory(String categoryId) {
        // deals 是按特价自动汇总的虚拟分类，商品不能直接挂在它下面
        if (CatalogService.CAT_DEALS.equals(categoryId) || categoryMapper.selectById(categoryId) == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分类不存在：" + categoryId);
        }
    }

    private static void apply(Product p, ProductSaveRequest req) {
        p.setCategoryId(req.cat());
        p.setName(req.name().trim());
        p.setNameEn(nullToEmpty(req.nameEn()));
        p.setSpec(nullToEmpty(req.spec()));
        p.setSpecEn(nullToEmpty(req.specEn()));
        p.setPrice(req.price());
        p.setSalePrice(req.salePrice() == null ? BigDecimal.ZERO : req.salePrice());
        p.setPromo(nullToEmpty(req.promo()));
        p.setPromoEn(nullToEmpty(req.promoEn()));
        p.setRemark(nullToEmpty(req.remark()));
        p.setRemarkEn(nullToEmpty(req.remarkEn()));
        p.setBarcode(nullToEmpty(req.barcode()));
        p.setBadge(nullToEmpty(req.badge()));
        p.setBadgeEn(nullToEmpty(req.badgeEn()));
        p.setImage(nullToEmpty(req.image()));
        p.setThumb(nullToEmpty(req.thumb()));
        if (req.status() != null) p.setStatus(req.status());
        if (req.sort() != null) p.setSort(req.sort());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
