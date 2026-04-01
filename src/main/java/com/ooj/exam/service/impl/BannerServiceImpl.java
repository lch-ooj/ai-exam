package com.ooj.exam.service.impl;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.ooj.exam.entity.Banner;
import com.ooj.exam.mapper.BannerMapper;
import com.ooj.exam.service.BannerService;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ooj.exam.service.FileUploadService;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 轮播图服务实现类
 */
@Slf4j
@Service
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements BannerService {

    @Autowired
    private FileUploadService fileUploadService;

    @Override
    public String uploadImage(MultipartFile file) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        // 1. 文件非空校验
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }

        // 2. 格式校验 - 检查是否为图片类型
        String contentType = file.getContentType();
        if (ObjectUtils.isEmpty(contentType) || !contentType.startsWith("image/")) {
            throw new RuntimeException("不支持的图片格式，仅支持 jpg、jpeg、png、gif、webp 格式");
        }

        // 3. 调用文件上传服务上传到 MinIO
        String imageUrl = fileUploadService.uploadFile(file, "banners");
        log.info("图片上传成功，访问路径：{}", imageUrl);
        return imageUrl;
    }



    @Override
    public void addBanner(Banner banner) {

        // 1. 默认值设置
        if (banner.getIsActive() == null) {
            banner.setIsActive(true);
        }

        if (banner.getSortOrder() == null) {
            banner.setSortOrder(0);
        }

        // 2. 保存到数据库
        boolean result = save(banner);

        if (!result) {
            log.error("轮播图添加失败，标题：{}", banner.getTitle());
        }
    }

    @Override
    public void updateBanner(Banner banner) {
        boolean result = updateById(banner);
        if (!result) {
            log.error("轮播图更新失败，id：{}", banner.getId());
        }
    }


}