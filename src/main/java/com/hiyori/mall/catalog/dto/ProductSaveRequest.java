package com.hiyori.mall.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 后台新建 / 编辑商品。只有 cat / name / price 必填，其余不传就是空 */
public record ProductSaveRequest(
        @NotBlank @Size(max = 32) String cat,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 160) String nameEn,
        @Size(max = 64) String spec,
        @Size(max = 64) String specEn,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal salePrice,
        @Size(max = 32) String promo,
        @Size(max = 48) String promoEn,
        @Size(max = 128) String remark,
        @Size(max = 160) String remarkEn,
        @Size(max = 32) String barcode,
        @Size(max = 16) String badge,
        @Size(max = 24) String badgeEn,
        @Size(max = 512) String image,
        @Size(max = 512) String thumb,
        @Min(0) @Max(1) Integer status,
        @Min(0) Integer sort) {}
