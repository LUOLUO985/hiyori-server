package com.hiyori.mall.catalog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hiyori.mall.catalog.dto.CatalogDtos.BannerVO;
import com.hiyori.mall.catalog.dto.CatalogDtos.CategoryVO;
import com.hiyori.mall.catalog.dto.CatalogDtos.HomeVO;
import com.hiyori.mall.catalog.dto.CatalogDtos.KeywordVO;
import com.hiyori.mall.catalog.dto.CatalogDtos.ProductDetailVO;
import com.hiyori.mall.catalog.dto.CatalogDtos.ProductVO;
import com.hiyori.mall.catalog.dto.CatalogDtos.SectionVO;
import com.hiyori.mall.catalog.entity.Banner;
import com.hiyori.mall.catalog.entity.Category;
import com.hiyori.mall.catalog.entity.HomeSection;
import com.hiyori.mall.catalog.entity.HotKeyword;
import com.hiyori.mall.catalog.entity.Product;
import com.hiyori.mall.catalog.mapper.BannerMapper;
import com.hiyori.mall.catalog.mapper.CategoryMapper;
import com.hiyori.mall.catalog.mapper.HomeSectionMapper;
import com.hiyori.mall.catalog.mapper.HotKeywordMapper;
import com.hiyori.mall.catalog.mapper.ProductMapper;
import com.hiyori.mall.catalog.mapper.ProductMapper.CategoryCount;
import com.hiyori.mall.catalog.mapper.ProductMapper.SectionProductRow;
import com.hiyori.mall.common.BizException;
import com.hiyori.mall.common.ErrorCode;
import com.hiyori.mall.common.PageResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** 小程序端（顾客）用的只读查询，只返回上架商品 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    public static final String CACHE_HOME = "home";
    public static final String CACHE_CATEGORIES = "categories";

    /** 虚拟分类：所有有特价 / 促销的商品。分类表里有这一行，但没有商品直接挂在它下面 */
    public static final String CAT_DEALS = "deals";

    /** 详情页「同类推荐」的数量 */
    private static final int RELATED_SIZE = 6;

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final BannerMapper bannerMapper;
    private final HomeSectionMapper sectionMapper;
    private final HotKeywordMapper hotKeywordMapper;

    /** 首页一次拿全：轮播、分类、区块、热搜。访问最多的接口，加缓存，后台改商品时清掉 */
    @Cacheable(CACHE_HOME)
    public HomeVO home() {
        List<HomeSection> sections = sectionMapper.selectList(new LambdaQueryWrapper<HomeSection>()
                .eq(HomeSection::getEnabled, 1)
                .orderByAsc(HomeSection::getSort));

        Map<String, List<ProductVO>> productsBySection = new LinkedHashMap<>();
        for (SectionProductRow row : productMapper.listEnabledSectionProducts()) {
            productsBySection.computeIfAbsent(row.getSectionId(), k -> new ArrayList<>()).add(ProductVO.of(row));
        }

        List<SectionVO> sectionVOs = sections.stream()
                .map(s -> new SectionVO(s.getId(), s.getTitle(), s.getTitleEn(), s.getSub(), s.getSubEn(),
                        s.getLinkCategoryId(), productsBySection.getOrDefault(s.getId(), List.of())))
                .filter(s -> !s.list().isEmpty())
                .toList();

        List<BannerVO> banners = bannerMapper.selectList(new LambdaQueryWrapper<Banner>()
                        .eq(Banner::getEnabled, 1)
                        .orderByAsc(Banner::getSort))
                .stream().map(BannerVO::of).toList();

        return new HomeVO(banners, categories(), sectionVOs, hotKeywords());
    }

    /** 分类列表，带每个分类的上架商品数；没有商品的分类不返回 */
    @Cacheable(CACHE_CATEGORIES)
    public List<CategoryVO> categories() {
        Map<String, Long> counts = new HashMap<>();
        for (CategoryCount c : productMapper.countOnShelfByCategory()) {
            counts.put(c.getCategoryId(), c.getTotal());
        }
        counts.put(CAT_DEALS, productMapper.countDeals());

        return categoryMapper.selectList(new LambdaQueryWrapper<Category>().orderByAsc(Category::getSort))
                .stream()
                .map(c -> CategoryVO.of(c, counts.getOrDefault(c.getId(), 0L)))
                .filter(c -> c.count() > 0)
                .toList();
    }

    public List<KeywordVO> hotKeywords() {
        return hotKeywordMapper.selectList(new LambdaQueryWrapper<HotKeyword>().orderByAsc(HotKeyword::getSort))
                .stream().map(KeywordVO::of).toList();
    }

    /** 某分类下的上架商品，分页；cat = deals 时返回所有特价 / 促销商品 */
    public PageResult<ProductVO> listByCategory(String categoryId, int page, int size) {
        LambdaQueryWrapper<Product> query = onShelf();
        if (CAT_DEALS.equals(categoryId)) {
            query.and(w -> w.gt(Product::getSalePrice, BigDecimal.ZERO).or().ne(Product::getPromo, ""));
        } else {
            query.eq(Product::getCategoryId, categoryId);
        }
        query.orderByAsc(Product::getSort);
        return PageResult.of(productMapper.selectPage(new Page<>(page, size), query), ProductVO::of);
    }

    /** 关键词命中：中文名 / 英文名（不分大小写）/ 规格 / 条码 / 分类名 */
    public PageResult<ProductVO> search(String keyword, int page, int size) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return new PageResult<>(List.of(), 0, page, size);
        }
        String lower = kw.toLowerCase(Locale.ROOT);
        List<String> matchedCats = categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .ne(Category::getId, CAT_DEALS)
                        .and(w -> w.like(Category::getName, kw)
                                .or().apply("LOWER(name_en) LIKE {0}", "%" + lower + "%")))
                .stream().map(Category::getId).toList();

        LambdaQueryWrapper<Product> query = onShelf().and(w -> {
            w.like(Product::getName, kw)
                    .or().apply("LOWER(name_en) LIKE {0}", "%" + lower + "%")
                    .or().like(Product::getSpec, kw)
                    .or().eq(Product::getBarcode, kw);
            if (!matchedCats.isEmpty()) {
                w.or().in(Product::getCategoryId, matchedCats);
            }
        }).orderByAsc(Product::getSort);

        return PageResult.of(productMapper.selectPage(new Page<>(page, size), query), ProductVO::of);
    }

    /** 商品详情 + 同类推荐（同分类里货架位置最近的几件，通常是同品牌 / 同系列） */
    public ProductDetailVO detail(String id) {
        Product p = productMapper.selectOne(onShelf().eq(Product::getId, id));
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在或已下架");
        }
        Category c = categoryMapper.selectById(p.getCategoryId());

        int sort = p.getSort() == null ? 0 : p.getSort();
        List<ProductVO> related = productMapper.selectList(onShelf()
                        .eq(Product::getCategoryId, p.getCategoryId())
                        .ne(Product::getId, p.getId())
                        .last("ORDER BY ABS(sort - " + sort + "), sort LIMIT " + RELATED_SIZE))
                .stream().map(ProductVO::of).toList();

        return ProductDetailVO.of(p, c, related);
    }

    private static LambdaQueryWrapper<Product> onShelf() {
        return new LambdaQueryWrapper<Product>().eq(Product::getStatus, Product.STATUS_ON);
    }
}
