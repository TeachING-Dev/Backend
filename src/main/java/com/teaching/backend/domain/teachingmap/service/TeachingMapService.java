package com.teaching.backend.domain.teachingmap.service;

import com.teaching.backend.domain.folder.entity.Folder;
import com.teaching.backend.domain.folder.exception.FolderErrorCode;
import com.teaching.backend.domain.folder.exception.FolderException;
import com.teaching.backend.domain.folder.repository.FolderRepository;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialAnalysis;
import com.teaching.backend.domain.material.entity.MaterialHighlight;
import com.teaching.backend.domain.material.enums.AiStatus;
import com.teaching.backend.domain.material.enums.HighlightType;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.repository.MaterialAnalysisRepository;
import com.teaching.backend.domain.material.repository.MaterialHighlightRepository;
import com.teaching.backend.domain.material.repository.MaterialRepository;
import com.teaching.backend.domain.tag.repository.MaterialTagRepository;
import com.teaching.backend.domain.teachingmap.dto.request.TeachingMapCreateRequest;
import com.teaching.backend.domain.teachingmap.dto.request.TeachingMapTempSaveRequest;
import com.teaching.backend.domain.teachingmap.dto.request.TeachingMapUpdateRequest;
import com.teaching.backend.domain.teachingmap.dto.response.*;
import com.teaching.backend.domain.teachingmap.entity.AiGuide;
import com.teaching.backend.domain.teachingmap.entity.TeachingMap;
import com.teaching.backend.domain.teachingmap.entity.TeachingMapStep;
import com.teaching.backend.domain.teachingmap.enums.*;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapErrorCode;
import com.teaching.backend.domain.teachingmap.exception.TeachingMapException;
import com.teaching.backend.domain.teachingmap.repository.AiGuideRepository;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapPlatformProjection;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapRepository;
import com.teaching.backend.domain.teachingmap.repository.TeachingMapStepRepository;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.domain.user.enums.TeacherPersona;
import com.teaching.backend.domain.user.exception.UserErrorCode;
import com.teaching.backend.domain.user.exception.UserException;
import com.teaching.backend.domain.user.repository.UserRepository;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import com.teaching.backend.global.apiPayload.code.GlobalErrorCode;
import com.teaching.backend.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    @Value("${app.icon-base-url}")
    private String iconBaseUrl;
    @Value("${app.teacher-image-base-url}")
    private String teacherImageBaseUrl;

    private final MaterialHighlightRepository materialHighlightRepository;
    private final MaterialTagRepository materialTagRepository;
    private final AiGuideRepository aiGuideRepository;
    private final HighlightAnalysisPromptGenerator highlightPromptGenerator;

//티칭맵 전체목록 조회

    @Transactional(readOnly = true)
    public TeachingMapListResponse getTeachingMaps(Long userId, TeachingMapStatus status,
                                                   TeachingMapType type, TeachingMapListSort sort, int page) {

        boolean isDraft = (status == TeachingMapStatus.TEMPORARY);
        TeachingMapStatus entityStatus = isDraft ? null : TeachingMapStatus.valueOf(status.name());
        TeachingMapType entityType = (type == TeachingMapType.ALL) ? null : TeachingMapType.valueOf(type.name());

        Sort sortOption = (sort == TeachingMapListSort.OLDEST)
                ? Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"))
                : Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, 10, sortOption);

        Page<TeachingMap> teachingMaps = teachingMapRepository.findAllByFilter(
                userId, isDraft, entityStatus, entityType, pageable);

        List<Long> teachingMapIds = teachingMaps.stream().map(TeachingMap::getId).toList();
        Map<Long, List<PlatformType>> platformTypesByTeachingMapId = teachingMapIds.isEmpty()
                ? Map.of()
                : stepRepository.findDistinctPlatformTypesByTeachingMapIdIn(teachingMapIds).stream()
                .collect(Collectors.groupingBy(
                        TeachingMapPlatformProjection::getTeachingMapId,
                        Collectors.mapping(TeachingMapPlatformProjection::getPlatformType, Collectors.toList())
                ));

        List<TeachingMapListItem> items = teachingMaps.stream()
                .map(tm -> toListItem(tm, isDraft, platformTypesByTeachingMapId.getOrDefault(tm.getId(), List.of())))
                .toList();

        return new TeachingMapListResponse(status.name(), type.name(), sort.name(), items,teachingMaps.getNumber(), teachingMaps.getSize(),
                teachingMaps.getTotalPages(), teachingMaps.getTotalElements(),
                teachingMaps.hasNext());
    }

    private TeachingMapListItem toListItem(TeachingMap tm, boolean isDraft,  List<PlatformType> platformTypes) {
        List<SourcePlatform> sourcePlatforms = platformTypes.stream()
                .limit(3)
                .map(p -> new SourcePlatform(p.name(), buildIconUrl(p.getIconPath())))
                .toList();
        int extraCount = Math.max(0, platformTypes.size() - 3);

        return TeachingMapListItem.from(tm, isDraft, sourcePlatforms, extraCount);
    }

    private String buildIconUrl(String iconPath) {
        return iconBaseUrl + "/" + iconPath;
    }
    private GuideType toGuideType(TeacherPersona persona) {
        return switch (persona) {
            case FRIENDLY -> GuideType.FRIENDLY;
            case STRICT -> GuideType.STRICT;
            case CHEERING -> GuideType.ENCOURAGING;
        };
    }
    private String buildTeacherImageUrl(GuideType guideType) {
        return teacherImageBaseUrl + "/" + guideType.getImagePath();
    }

    // 티칭맵 생성
    @Transactional
    public TeachingMapCreateResponse createTeachingMap(Long userId, TeachingMapCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        Folder folder = folderRepository.findByIdAndUser_Id(request.folderId(), userId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.FOLDER_NOT_FOUND));

        List<Material> materials = materialRepository.findAllByFolder_Id(folder.getId()).stream()
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

    //티칭맵 상세 조회
    @Transactional(readOnly = true)
    public TeachingMapStepDetailResponse getStepDetail(Long userId, Long teachingMapId, Long stepId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        teachingMapRepository.findByIdAndUser_IdAndDeletedAtIsNull(teachingMapId, userId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.TEACHING_MAP_NOT_FOUND));

        TeachingMapStep step = stepRepository.findByIdAndTeachingMapId(stepId, teachingMapId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.STEP_NOT_FOUND));

        Material material = step.getMaterial();

        MaterialAnalysis analysis = materialAnalysisRepository.findByMaterialId(material.getId())
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.MATERIAL_ANALYSIS_NOT_FOUND));

        List<MaterialHighlight> materialHighlights = materialHighlightRepository.findAllByMaterialId(material.getId());
        List<HighlightResponse> highlights = materialHighlights.stream()
                .map(HighlightResponse::from)
                .toList();

        List<String> tags = materialTagRepository.findAllTagNamesByMaterialId(material.getId());

        ExistingAiAnalysis existingAiAnalysis = ExistingAiAnalysis.of(analysis, highlights);

        GuideType guideType = toGuideType(user.getTeacherPersona());
        List<Long> highlightIds = materialHighlights.stream().map(MaterialHighlight::getId).toList();
        List<FeedbackResponse> feedbacks = aiGuideRepository
                .findAllByMaterialHighlightIdInAndGuideType(highlightIds, guideType).stream()
                .map(FeedbackResponse::from)
                .toList();

        AiTeacherAnalysis aiTeacherAnalysis = AiTeacherAnalysis.of(guideType, buildTeacherImageUrl(guideType), feedbacks);

        return TeachingMapStepDetailResponse.of(step, material, tags, existingAiAnalysis, aiTeacherAnalysis);
    }

    public TeachingMapDetailResponse getTeachingMap(Long userId, Long teachingMapId) {
        TeachingMap teachingMap = teachingMapRepository.findByIdAndUser_IdAndDeletedAtIsNull(teachingMapId, userId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.TEACHING_MAP_NOT_FOUND));

        List<TeachingMapStep> steps = stepRepository
                .findByTeachingMapIdAndDeletedAtIsNullOrderByStepOrder(teachingMapId);

        return TeachingMapDetailResponse.from(teachingMap, steps);
    }
    //티칭맵 스텝 완료 상태 토글
    @Transactional
    public StepToggleResponse toggleStep(Long userId, Long teachingMapId, Long stepId) {
        TeachingMap teachingMap = teachingMapRepository.findByIdAndUser_IdAndDeletedAtIsNull(teachingMapId, userId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.TEACHING_MAP_NOT_FOUND));

        TeachingMapStep step = stepRepository.findByIdAndTeachingMapId(stepId, teachingMapId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.STEP_NOT_FOUND));

        boolean isCompleted = step.toggle();
        teachingMap.applyStepToggle(isCompleted);

        double progressRate = teachingMap.getTotalSteps() == 0
                ? 0.0
                : Math.round((teachingMap.getCurrentSteps() * 1000.0) / teachingMap.getTotalSteps()) / 10.0;

        return StepToggleResponse.of(
                step.getId(),
                isCompleted,
                teachingMap.getCurrentSteps(),
                teachingMap.getTotalSteps(),
                progressRate
        );
    }

    // 티칭맵 휴지통으로 이동 (다중 선택)
    @Transactional
    public TeachingMapTrashResponse moveToTrash(Long userId, List<Long> teachingMapIds) {
        List<TeachingMap> teachingMaps = teachingMapRepository
                .findAllByIdInAndUser_IdAndDeletedAtIsNull(teachingMapIds, userId);

        if (teachingMaps.size() != teachingMapIds.size()) {
            throw new GeneralException(TeachingMapErrorCode.TEACHING_MAP_NOT_FOUND);
        }

        teachingMaps.forEach(TeachingMap::delete);

        List<Long> deletedIds = teachingMaps.stream().map(TeachingMap::getId).toList();
        return TeachingMapTrashResponse.of(deletedIds);
    }

    //티칭맵 임시저장
    @Transactional
    public TeachingMapCreateResponse tempSave (Long userId, TeachingMapTempSaveRequest request)
    { if (request.type() == TeachingMapType.ALL) {
        throw new GeneralException(TeachingMapErrorCode.INVALID_TEACHING_MAP_TYPE); // 티칭맵 모드 설정 관련
    }


        Folder folder = folderRepository.findByIdAndUser_Id(request.folderId(), userId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.FOLDER_NOT_FOUND));

        List<Material> materials = materialRepository.findAllByFolder_Id(folder.getId()).stream()
                .filter(m -> m.getAiStatus() == AiStatus.COMPLETED)
                .toList();
        if (materials.size() < 3) {
            throw new GeneralException(TeachingMapErrorCode.FOLDER_MATERIAL_NOT_ENOUGH);
        }

        TeachingMap teachingMap;
        if (request.teachingMapId() != null) {
            teachingMap = teachingMapRepository.findByIdAndUser_IdAndDeletedAtIsNull(request.teachingMapId(), userId)
                    .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.TEACHING_MAP_NOT_FOUND));

            teachingMap.updateDraft(folder, request.title(), request.description(), request.type());
        } else {
            User user = userRepository.getReferenceById(userId);
            teachingMap = TeachingMap.create(
                    folder, user, request.title(), request.description(),
                    0, request.type(), true
            );
            teachingMapRepository.save(teachingMap);
        }

        return TeachingMapCreateResponse.from(teachingMap);



    }

    @Transactional
    public TeachingMapCreateResponse updateInfo(Long userId, Long teachingMapId, TeachingMapUpdateRequest request) {
        TeachingMap teachingMap = teachingMapRepository.findByIdAndUser_IdAndDeletedAtIsNull(teachingMapId, userId)
                .orElseThrow(() -> new GeneralException(TeachingMapErrorCode.TEACHING_MAP_NOT_FOUND));

        teachingMap.updateInfo(request.title(), request.description());
        return TeachingMapCreateResponse.from(teachingMap);
    }
}
