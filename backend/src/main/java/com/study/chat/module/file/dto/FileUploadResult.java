package com.study.chat.module.file.dto;

import lombok.Data;

/**
 * 文件上传结果：返回前端可直接使用的相对 URL（/static/...），
 * 由 WebConfig 的静态资源映射 + Electron/Vite 的 /static 代理统一回源。
 */
@Data
public class FileUploadResult {

    /** 形如 /static/2026/09/<uuid>.jpg，前端直接当 src 用 */
    private String url;

    /** 原始 Content-Type */
    private String contentType;

    /** 字节数 */
    private long size;

    /** 图片宽（视频为 null） */
    private Integer width;

    /** 图片高（视频为 null） */
    private Integer height;

    /** 视频时长秒（图片为 null） */
    private Double duration;
}
