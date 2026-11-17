package com.hiyori.mall.catalog.dto;

import com.hiyori.mall.catalog.entity.Banner;
import com.hiyori.mall.catalog.entity.Category;
import com.hiyori.mall.catalog.entity.HotKeyword;
import com.hiyori.mall.catalog.entity.Product;
import java.math.BigDecimal;
import java.util.List;

/**
 * 返回给小程序的数据结构。
 * 中英文两套文案一起返回（xxx / xxxEn），由小程序按用户选的语言显示，
 * 这样切换语言不用重新请求，购物车里存的商品也能跟着换语言。
 */
public final class CatalogDtos {

    private CatalogDtos() {}

    private static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** 列表里的一件商品 */
    public record ProductVO(
            String id, String cat,
            String name, String nameEn,
            String spec, String specEn,
            BigDecimal price, BigDecimal salePrice,
            String promo, String promoEn,
            String badge, String badgeEn,
            String image, String thumb) {

        public static ProductVO of(Product p) {
            return new ProductVO(
                    p.getId(), p.getCategoryId(),
                    p.getName(), p.getNameEn(),
                    p.getSpec(), p.getSpecEn(),
                    money(p.getPrice()), money(p.getSalePrice()),
                    p.getPromo(), p.getPromoEn(),
                    p.getBadge(), p.getBadgeEn(),
                    p.getImage(), p.getThumb());
        }
    }

    /** 商品详情：多带说明、条码、分类名和「同类推荐」 */
    public record ProductDetailVO(
            String id, String cat, String catName, String catNameEn,
            String name, String nameEn,
            String spec, String specEn,
            BigDecimal price, BigDecimal salePrice,
            String promo, String promoEn,
            String remark, String remarkEn,
            String barcode,
            String badge, String badgeEn,
            String image, String thumb,
            List<ProductVO> related) {

        public static ProductDetailVO of(Product p, Category c, List<ProductVO> related) {
            return new ProductDetailVO(
                    p.getId(), p.getCategoryId(),
                    c == null ? "" : c.getName(), c == null ? "" : c.getNameEn(),
                    p.getName(), p.getNameEn(),
                    p.getSpec(), p.getSpecEn(),
                    money(p.getPrice()), money(p.getSalePrice()),
                    p.getPromo(), p.getPromoEn(),
                    p.getRemark(), p.getRemarkEn(),
                    p.getBarcode(),
                    p.getBadge(), p.getBadgeEn(),
                    p.getImage(), p.getThumb(),
                    related);
        }
    }

    /** 后台列表多带几个管理字段 */
    public record AdminProductVO(
            String id, String cat,
            String name, String nameEn,
            String spec, String specEn,
            BigDecimal price, BigDecimal salePrice,
            String promo, String promoEn,
            String remark, String remarkEn,
            String barcode,
            String badge, String badgeEn,
            String image, String thumb,
            int status, int sort) {

        public static AdminProductVO of(Product p) {
            return new AdminProductVO(
                    p.getId(), p.getCategoryId(),
                    p.getName(), p.getNameEn(),
                    p.getSpec(), p.getSpecEn(),
                    money(p.getPrice()), money(p.getSalePrice()),
                    p.getPromo(), p.getPromoEn(),
                    p.getRemark(), p.getRemarkEn(),
                    p.getBarcode(),
                    p.getBadge(), p.getBadgeEn(),
                    p.getImage(), p.getThumb(),
                    p.getStatus(), p.getSort());
        }
    }

    /** count = 该分类下上架商品数 */
    public record CategoryVO(
            String id, String name, String nameEn, String emoji, String image,
            String desc, String descEn, long count) {

        public static CategoryVO of(Category c, long count) {
            return new CategoryVO(
                    c.getId(), c.getName(), c.getNameEn(), c.getEmoji(), c.getImage(),
                    c.getDescription(), c.getDescriptionEn(), count);
        }
    }

    /** link = 点轮播图跳到的分类 id，空则不跳 */
    public record BannerVO(
            String id, String title, String titleEn, String sub, String subEn, String image, String link) {

        public static BannerVO of(Banner b) {
            return new BannerVO(
                    String.valueOf(b.getId()), b.getTitle(), b.getTitleEn(), b.getSub(), b.getSubEn(),
                    b.getImage(), b.getLinkCategoryId());
        }
    }

    public record SectionVO(
            String id, String title, String titleEn, String sub, String subEn, String link,
            List<ProductVO> list) {}

    public record KeywordVO(String keyword, String keywordEn) {
        public static KeywordVO of(HotKeyword k) {
            return new KeywordVO(k.getKeyword(), k.getKeywordEn());
        }
    }

    public record HomeVO(
            List<BannerVO> banners,
            List<CategoryVO> categories,
            List<SectionVO> sections,
            List<KeywordVO> hotKeywords) {}
}
