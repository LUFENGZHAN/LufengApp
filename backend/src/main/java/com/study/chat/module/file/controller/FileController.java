package com.study.chat.module.file.controller;

import com.study.chat.common.result.Result;
import com.study.chat.module.file.dto.FileUploadResult;
import com.study.chat.module.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 媒体文件上传：聊天发图片/视频的前置步骤。
 * 返回 /static/... 相对 URL，前端直接当作消息内容发送（msgType=2 图片 / 4 视频）。
 */
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService storageService;

    @PostMapping("/upload")
    public Result<FileUploadResult> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "image") String category) {
        return Result.success(storageService.store(file, category));
    }
}
