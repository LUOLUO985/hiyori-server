package com.hiyori.mall.catalog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hiyori.mall.catalog.entity.Product;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.annotations.Select;

public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 一次查出所有启用区块里的上架商品（JOIN），按区块、区块内顺序排好。
     * 首页有多个区块，逐个区块查会变成 N+1 次查询，这里合成一条。
     */
    @Select("""
            SELECT i.section_id AS section_id, p.*
            FROM home_section_item i
            JOIN home_section s ON s.id = i.section_id AND s.enabled = 1
            JOIN product p ON p.id = i.product_id AND p.status = 1 AND p.deleted = 0
            ORDER BY s.sort, i.sort
            """)
    List<SectionProductRow> listEnabledSectionProducts();

    /** 每个分类下有多少上架商品（分类页左侧显示数量用） */
    @Select("""
            SELECT category_id AS category_id, COUNT(*) AS total
            FROM product
            WHERE status = 1 AND deleted = 0
            GROUP BY category_id
            """)
    List<CategoryCount> countOnShelfByCategory();

    /** 有特价或促销的上架商品数 */
    @Select("""
            SELECT COUNT(*)
            FROM product
            WHERE status = 1 AND deleted = 0 AND (sale_price > 0 OR promo <> '')
            """)
    long countDeals();

    @Data
    @EqualsAndHashCode(callSuper = true)
    class SectionProductRow extends Product {
        private String sectionId;
    }

    @Data
    class CategoryCount {
        private String categoryId;
        private long total;
    }
}
