package com.ooj.exam.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "轮播图 VO")
public class BannerVO implements Serializable {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "创建时间")
    private String createTime;

    @Schema(description = "逻辑删除标识")
    private Byte isDeleted;

    @Schema(description = "轮播图标题")
    private String title;

    @Schema(description = "轮播图描述")
    private String description;

    @Schema(description = "图片 URL")
    private String imageUrl;

    @Schema(description = "链接 URL")
    private String linkUrl;

    @Schema(description = "排序顺序")
    private Integer sortOrder;

    @Schema(description = "是否激活")
    private Boolean isActive;
}
