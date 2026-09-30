package com.study.chat.module.file.service;

import com.study.chat.common.result.BizException;
import com.study.chat.common.result.ResultCode;
import com.study.chat.module.file.dto.FileUploadResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 媒体文件落地：按 yyyy/MM 分目录写入本地磁盘（Docker 下走持久化卷），
 * 图片顺带读出宽高，视频时长由前端回传。URL 统一为 /static/... 相对路径。
 */
@Slf4j
@Service
public class FileStorageService {

    private final String storageDir;

    private static final long IMAGE_MAX = 20L * 1024 * 1024;
    private static final long VIDEO_MAX = 100L * 1024 * 1024;

    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp",
            "image/bmp", "bmp");

    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", "mp4",
            "video/webm", "webm",
            "video/ogg", "ogv",
            "video/quicktime", "mov",
            "video/x-matroska", "mkv");

    public FileStorageService(@Value("${lufeng.storage.dir:${user.home}/.lufeng/uploads}") String storageDir) {
        this.storageDir = Paths.get(storageDir).toAbsolutePath().toString();
    }

    @PostConstruct
    void ensureDir() {
        try {
            Files.createDirectories(Paths.get(storageDir));
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", storageDir, e);
        }
    }

    public FileUploadResult store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件为空");
        }
        boolean isVideo = "video".equalsIgnoreCase(category);
        Map<String, String> allowed = isVideo ? VIDEO_TYPES : IMAGE_TYPES;
        String contentType = file.getContentType();
        if (contentType == null || !allowed.containsKey(contentType)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不支持的文件类型：" + contentType);
        }
        long limit = isVideo ? VIDEO_MAX : IMAGE_MAX;
        if (file.getSize() > limit) {
            throw new BizException(ResultCode.MESSAGE_TOO_LARGE, "文件超过大小限制（" + (limit / 1024 / 1024) + "MB）");
        }

        String ext = allowed.get(contentType);
        String rel = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"))
                + "/" + UUID.randomUUID() + "." + ext;
        Path target = Paths.get(storageDir, rel);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BizException(ResultCode.SERVER_ERROR, "文件保存失败：" + e.getMessage());
        }

        FileUploadResult result = new FileUploadResult();
        result.setUrl("/static/" + rel);
        result.setContentType(contentType);
        result.setSize(file.getSize());

        if (!isVideo) {
            readImageSize(target, result);
        }
        return result;
    }

    private void readImageSize(Path target, FileUploadResult result) {
        try (InputStream in = Files.newInputStream(target)) {
            BufferedImage img = ImageIO.read(in);
            if (img != null) {
                result.setWidth(img.getWidth());
                result.setHeight(img.getHeight());
            }
        } catch (Exception e) {
            log.warn("读取图片尺寸失败: {}", target, e);
        }
    }
}
