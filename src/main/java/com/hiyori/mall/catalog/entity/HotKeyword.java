package com.hiyori.mall.catalog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("hot_keyword")
public class HotKeyword {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String keyword;
    private String keywordEn;
    private Integer sort;
}
