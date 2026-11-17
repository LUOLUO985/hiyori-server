package com.hiyori.mall.catalog.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("product")
public class Product {

    public static final int STATUS_ON = 1;
    public static final int STATUS_OFF = 0;

    /** 导入商品沿用 p101 这类编号；后台新建商品不传 id 时由 MyBatis-Plus 生成雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String categoryId;
    private String name;
    private String nameEn;
    private String spec;
    private String specEn;
    /** 价签原价；0 = 到店询价 */
    private BigDecimal price;
    /** 特价；0 = 没有特价 */
    private BigDecimal salePrice;
    /** 促销说明，如「2件3€」「买1送1」 */
    private String promo;
    private String promoEn;
    /** 给顾客看的补充说明，如整箱价 */
    private String remark;
    private String remarkEn;
    private String barcode;
    private String badge;
    private String badgeEn;
    /** 大图（详情页） */
    private String image;
    /** 缩略图（列表）；空则用大图 */
    private String thumb;
    private Integer status;
    private Integer sort;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
