package com.dochub.workbench.manage.service.impl;

import lombok.AllArgsConstructor;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import com.dochub.workbench.manage.config.DocumentManageProperties;
import com.dochub.workbench.manage.service.DocumentStorageService;
import com.dochub.workbench.manage.support.StoredObjectInfo;
import org.javaup.enums.DocumentManageCode;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务实现层
 * @author: zhangjihe
 **/

@AllArgsConstructor
@Service
public class MinioDocumentStorageService implements DocumentStorageService {

    private final MinioClient minioClient;

    private final DocumentManageProperties properties;

    @Override
    public StoredObjectInfo uploadOriginalFile(Long documentId, String originalFileName, byte[] bytes, String contentType) {
        //原始文件路径按“前缀/文档ID/时间戳-原始文件名”组织，便于同一文档的多次上传版本区分与追踪。
        String objectName = properties.getMinio().getObjectPrefix() + "/" + documentId + "/" + System.currentTimeMillis() + "-" + originalFileName;
        upload(objectName, bytes, contentType);
        //返回对象定位信息给上层，供文档主表持久化保存。
        return new StoredObjectInfo(properties.getMinio().getBucketName(), objectName, buildObjectUrl(objectName));
    }


    @Override
    public String uploadParsedText(Long documentId, String parsedText) {

        String objectName = properties.getMinio().getParsedTextPrefix() + "/" + documentId + ".txt";
        upload(objectName, parsedText.getBytes(StandardCharsets.UTF_8), "text/plain;charset=UTF-8");
        return objectName;
    }

    @Override
    public byte[] downloadObject(String objectName) {
        try (InputStream inputStream = minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(properties.getMinio().getBucketName())
                .object(objectName)
                .build())) {

            return inputStream.readAllBytes();
        }
        catch (Exception exception) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_STORAGE_FAILED.getCode(),
                "下载 MinIO 文件失败: " + exception.getMessage(), exception);
        }
    }

    @Override
    public String downloadText(String objectName) {

        return new String(downloadObject(objectName), StandardCharsets.UTF_8);
    }

    @Override
    public void deleteObjects(List<String> objectNameList) {
        if (CollUtil.isEmpty(objectNameList)) {
            return;
        }

        List<String> validObjectNameList = objectNameList.stream()
            .filter(StrUtil::isNotBlank)
            .map(String::trim)
            .distinct()
            .toList();
        if (validObjectNameList.isEmpty()) {
            return;
        }

        try {

            if (!bucketExists()) {
                return;
            }

            for (String objectName : validObjectNameList) {
                minioClient.removeObject(
                    RemoveObjectArgs.builder()
                        .bucket(properties.getMinio().getBucketName())
                        .object(objectName)
                        .build()
                );
            }
        }
        catch (Exception exception) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_STORAGE_FAILED.getCode(),
                "删除 MinIO 文件失败: " + exception.getMessage(), exception);
        }
    }

    private void upload(String objectName, byte[] bytes, String contentType) {
        try {
            //先保证桶存在，再写对象；否则首次部署或新环境下第一次上传会直接失败
            ensureBucketExists();
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucketName())
                    .object(objectName)
                    //如果上层没能提供明确的MIME类型，就回退到通用二进制类型
                    .contentType(StrUtil.isNotBlank(contentType) ? contentType : "application/octet-stream")
                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                    .build()
            );
        }
        catch (Exception exception) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_STORAGE_FAILED.getCode(),
                "上传 MinIO 文件失败: " + exception.getMessage(), exception);
        }
    }

    private void ensureBucketExists() throws Exception {
        if (!bucketExists()) {
            //只有桶不存在时才创建，避免每次上传都重复发起创建请求
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getMinio().getBucketName()).build());
        }
    }

    private boolean bucketExists() throws Exception {
        String bucketName = properties.getMinio().getBucketName();
        return minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    private String buildObjectUrl(String objectName) {
        String endpoint = properties.getMinio().getEndpoint();
        if (endpoint.endsWith("/")) {

            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + properties.getMinio().getBucketName() + "/" + objectName;
    }
}
