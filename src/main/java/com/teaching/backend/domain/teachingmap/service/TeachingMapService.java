package com.teaching.backend.domain.teachingmap.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.teachingmap.dto.request.TeachingMapCreateRequest;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapCreateResponse;
import com.teaching.backend.domain.teachingmap.dto.response.TeachingMapListResponse;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.entity.TeachingMapStep;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapStatus;
import com.teaching.backend.domain.teachingmap.enums.TeachingMapType;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapErrorCode;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapException;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapStepRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.UserRepository;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeachingMapService {

    private final TeachingMapRepository teachingMapRepository;
    private final FolderRepository folderRepository;
    private final TeachingMapStepRepository stepRepository;
    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final OpenAiClient openAiClient;
    private final TeachingMapPromptGenerator promptGenerator;
    private final TeachingMapAiResultParser resultParser;
    private final MaterialAnalysisRepository materialAnalysisRepository;


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

    // 티칭맵 생성
    @Transactional
    public TeachingMapCreateResponse createTeachingMap(Long userId, TeachingMapCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        Folder folder = folderRepository.findByIdAndUser_Id(request.folderId(), userId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.FOLDER_NOT_FOUND));

        List<Material> materials = materialRepository.findAllByFolderId(folder.getId()).stream()
                .filter(m -> m.getAiStatus() == AiStatus.COMPLETED)
                .toList();
        if (materials.size() < 3) {
            throw new GeneralException(TeachingMapErrorCode.FOLDER_MATERIAL_NOT_ENOUGH);
        }

        List<Long> materialIds = materials.stream().map(Material::getId).toList();
        Map<Long, MaterialAnalysis> analysisByMaterialId = materialAnalysisRepository
                .findAllActiveByMaterialIds(materialIds).stream()   // findAllByMaterialIdIn → 이걸로 교체
                .collect(Collectors.toMap(a -> a.getMaterial().getId(), a -> a));
        Map<Long, Material> materialById = materials.stream()
                .collect(Collectors.toMap(Material::getId, m -> m));

        String systemPrompt = promptGenerator.buildSystemPrompt();
        String userMessage = promptGenerator.buildUserMessage(request.type(), materials, analysisByMaterialId);
        String aiResponse = openAiClient.chatCompleteJson(systemPrompt, userMessage);
        var result = resultParser.parse(aiResponse);

        //ai 응답 검증
        validateAiResult(result, request.type(), materials.size());
        TeachingMap teachingMap = TeachingMap.create(
                folder, user, request.title(), request.description(),
                result.nodes().size(), request.type(), false
        );
        teachingMapRepository.save(teachingMap);

        List<TeachingMapStep> steps = result.nodes().stream()
                .sorted(Comparator.comparingInt(TeachingMapAiResultParser.TeachingMapAiNode::step))
                .map(node -> {
                    Material material = materialById.get(node.materialId());
                    if (material == null) {
                        throw new GeneralException(TeachingMapErrorCode.AI_RESULT_MATERIAL_MISMATCH);
                    }
                    return TeachingMapStep.create(teachingMap, material, node.step(), node.title(), node.aiGuide());
                })
                .toList();
        stepRepository.saveAll(steps);

        return TeachingMapCreateResponse.from(teachingMap);
    }
    private void validateAiResult(TeachingMapAiResultParser.TeachingMapAiResult result,
                                  TeachingMapType type, int materialCount) {
        List<TeachingMapAiResultParser.TeachingMapAiNode> nodes = result.nodes();

        if (nodes == null || nodes.isEmpty()) {
            throw new GeneralException(TeachingMapErrorCode.AI_RESULT_INVALID);
        }

        long distinctMaterialCount = nodes.stream()
                .map(TeachingMapAiResultParser.TeachingMapAiNode::materialId)
                .distinct().count();
        if (distinctMaterialCount != nodes.size()) {
            throw new GeneralException(TeachingMapErrorCode.AI_RESULT_INVALID);
        }

        Set<Integer> steps = nodes.stream()
                .map(TeachingMapAiResultParser.TeachingMapAiNode::step)
                .collect(Collectors.toSet());
        boolean isSequential = IntStream.rangeClosed(1, nodes.size()).allMatch(steps::contains);
        if (!isSequential) {
            throw new GeneralException(TeachingMapErrorCode.AI_RESULT_INVALID);
        }

        if (type == TeachingMapType.SHORTCUT && (nodes.size() < 3 || nodes.size() > 5)) {
            throw new GeneralException(TeachingMapErrorCode.AI_RESULT_INVALID);
        }
        if (type == TeachingMapType.DEEPDIVE && nodes.size() != materialCount) {
            throw new GeneralException(TeachingMapErrorCode.AI_RESULT_INVALID);
        }
    }
}
