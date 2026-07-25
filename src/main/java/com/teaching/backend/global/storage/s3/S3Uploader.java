package com.teaching.backend.global.storage.s3;

import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.UUID;

/**
 * AWS S3 업로드 클라이언트. 자격증명은 코드에 직접 넣지 않고 AWS 기본 자격증명 체인
 * (환경변수 -> ~/.aws/credentials -> EC2 인스턴스 프로필)을 그대로 사용한다.
 */
@Component
public class S3Uploader {

    private final S3Client s3Client;
    private final String bucket;

    public S3Uploader(
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.s3.region}") String region
    ) {
        this.bucket = bucket;
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * 파일을 {@code {directory}/{uuid}.{format}} 키로 업로드하고 공개 URL을 반환한다.
     * {@code format} 은 클라이언트가 보낸 값이 아니라 서버에서 실제 파일 내용을 디코딩해
     * 검증한 이미지 포맷(예: "png", "jpeg")이어야 한다.
     */
    public String upload(MultipartFile file, String directory, String format) {
        String key = directory + "/" + UUID.randomUUID() + "." + format;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("image/" + format)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | S3Exception e) {
            throw new GeneralException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
        }

        return s3Client.utilities().getUrl(builder -> builder.bucket(bucket).key(key)).toExternalForm();
    }
}
