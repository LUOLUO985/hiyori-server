package com.hiyori.mall.catalog;

import com.hiyori.mall.catalog.dto.CatalogDtos.CategoryVO;
import com.hiyori.mall.catalog.dto.CatalogDtos.HomeVO;
import com.hiyori.mall.catalog.dto.CatalogDtos.KeywordVO;
import com.hiyori.mall.catalog.dto.CatalogDtos.ProductDetailVO;
import com.hiyori.mall.catalog.dto.CatalogDtos.ProductVO;
import com.hiyori.mall.catalog.service.CatalogService;
import com.hiyori.mall.common.PageResult;
import com.hiyori.mall.common.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 顾客端公开接口，不需要登录 */
@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/home")
    public Result<HomeVO> home() {
        return Result.ok(catalogService.home());
    }

    @GetMapping("/categories")
    public Result<List<CategoryVO>> categories() {
        return Result.ok(catalogService.categories());
    }

    /** 某分类下的商品，分页；cat=deals 是「特价优惠」 */
    @GetMapping("/products")
    public Result<PageResult<ProductVO>> listByCategory(
            @RequestParam("cat") String categoryId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(60) int size) {
        return Result.ok(catalogService.listByCategory(categoryId, page, size));
    }

    @GetMapping("/products/search")
    public Result<PageResult<ProductVO>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(catalogService.search(keyword, page, size));
    }

    @GetMapping("/products/{id}")
    public Result<ProductDetailVO> detail(@PathVariable String id) {
        return Result.ok(catalogService.detail(id));
    }

    @GetMapping("/hot-keywords")
    public Result<List<KeywordVO>> hotKeywords() {
        return Result.ok(catalogService.hotKeywords());
    }
}
