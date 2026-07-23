package com.teaching.backend.domain.teachingmap.service;

import com.teaching.backend.domain.teachingmap.dto.TeachingMapListResponse;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeachingMapService {

    private final TeachingMapRepository teachingMapRepository;

    public List<TeachingMapListResponse> getTeachingMaps(
            Long userId,
            TeachingMapStatus status,
            Integer size
    ) {
        validateUserId(userId);
        validateSize(size);

        return findTeachingMaps(userId, status, size)
                .stream()
                .map(TeachingMapListResponse::from)
                .toList();
    }

    private List<TeachingMap> findTeachingMaps(
            Long userId,
            TeachingMapStatus status,
            Integer size
    ) {
        Sort recentSort = recentSort();

        if (size == null) {
            if (status == null) {
                return teachingMapRepository.findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(
                        userId,
                        recentSort
                );
            }

            return teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                    userId,
                    status,
                    recentSort
            );
        }

        PageRequest pageRequest = PageRequest.of(0, size, recentSort);
        if (status == null) {
            return teachingMapRepository.findAllByUser_IdAndIsDraftFalseAndDeletedAtIsNull(
                    userId,
                    pageRequest
            ).getContent();
        }

        return teachingMapRepository.findAllByUser_IdAndStatusAndIsDraftFalseAndDeletedAtIsNull(
                userId,
                status,
                pageRequest
        ).getContent();
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new GeneralException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    private void validateSize(Integer size) {
        if (size != null && size <= 0) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private Sort recentSort() {
        return Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );
    }
}
