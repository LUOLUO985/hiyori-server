package com.hiyori.mall.admin;

import com.hiyori.mall.catalog.dto.CatalogDtos.AdminProductVO;
import com.hiyori.mall.catalog.dto.ProductSaveRequest;
import com.hiyori.mall.common.PageResult;
import com.hiyori.mall.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端商品接口：AuthInterceptor 已经保证只有管理员能进来 */
@Validated
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
public class AdminProductController {

    private final AdminProductService service;

    public record StatusRequest(@Min(0) @Max(1) int status) {}

    @GetMapping
    public Result<PageResult<AdminProductVO>> page(
            @RequestParam(required = false) String cat,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.page(cat, keyword, status, page, size));
    }

    @PostMapping
    public Result<AdminProductVO> create(@Valid @RequestBody ProductSaveRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public Result<AdminProductVO> update(@PathVariable String id, @Valid @RequestBody ProductSaveRequest req) {
        return Result.ok(service.update(id, req));
    }

    /** 上架 / 下架 */
    @PatchMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable String id, @Valid @RequestBody StatusRequest req) {
        service.changeStatus(id, req.status());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }
}
