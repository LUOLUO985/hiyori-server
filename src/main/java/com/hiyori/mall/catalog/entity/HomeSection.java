package com.hiyori.mall.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("home_section")
public class HomeSection {
    @TableId(type = IdType.INPUT)
    private String id;
    private String title;
    private String titleEn;
    private String sub;
    private String subEn;
    private String linkCategoryId;
    private Integer sort;
    private Integer enabled;
}
