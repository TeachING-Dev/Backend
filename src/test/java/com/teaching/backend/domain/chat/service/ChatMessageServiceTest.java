package com.teaching.backend.domain.chat.service;

import com.teaching.backend.domain.chat.dto.MessageCreateRequest;
import com.teaching.backend.domain.chat.entity.ChatMessage;
import com.teaching.backend.domain.chat.entity.ChatRoom;
import com.teaching.backend.domain.chat.repository.ChatMessageRepository;
import com.teaching.backend.domain.chat.repository.ChatSourceRepository;
import com.teaching.backend.domain.material.entity.Material;
import com.teaching.backend.domain.material.entity.MaterialChunk;
import com.teaching.backend.domain.material.enums.PlatformType;
import com.teaching.backend.domain.material.service.MaterialSearchService;
import com.teaching.backend.domain.user.entity.User;
import com.teaching.backend.global.ai.openai.OpenAiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 집계형 질문 분기 + searchByMetadata(1순위)/searchTopChunks(2순위)/searchByTitleTagSimilarity(3순위)
// 계층이 ask()에서 올바르게 사용되는지 검증
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    private static final Long CHAT_ROOM_ID = 10L;
    private static final Long USER_ID = 1L;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatSourceRepository chatSourceRepository;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private MaterialSearchService materialSearchService;

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private ChatAskWriter chatAskWriter;

    @Mock
    private AggregateQuestionAnswerer aggregateQuestionAnswerer;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    void askUsesMetadataChunksAndSkipsVectorSearchWhenMetadataSearchFindsSomething() {
        ChatAskWriter.Reservation reservation = reservation();
        MaterialChunk metadataChunk = chunk(1L, "백엔드 관련 자료 내용");
        when(chatAskWriter.reserve(CHAT_ROOM_ID, USER_ID, "백엔드 자료 있어?")).thenReturn(reservation);
        when(materialSearchService.searchByMetadata("백엔드 자료 있어?", USER_ID)).thenReturn(List.of(metadataChunk));
        when(openAiClient.chatComplete(anyString(), eq("백엔드 자료 있어?"))).thenReturn("네, 있습니다.");
        when(chatAskWriter.finalizeAnswer(eq(reservation), anyString(), eq(false), eq(List.of(metadataChunk))))
                .thenReturn(askResult(reservation));

        chatMessageService.ask(CHAT_ROOM_ID, USER_ID, new MessageCreateRequest("백엔드 자료 있어?"));

        verify(materialSearchService, never()).searchTopChunks(anyString(), eq(USER_ID));
        verify(chatAskWriter).finalizeAnswer(reservation, "네, 있습니다.", false, List.of(metadataChunk));
    }

    @Test
    void askFallsBackToVectorSearchWhenMetadataSearchFindsNothing() {
        ChatAskWriter.Reservation reservation = reservation();
        MaterialChunk vectorChunk = chunk(2L, "Redis 캐시 설명");
        when(chatAskWriter.reserve(CHAT_ROOM_ID, USER_ID, "캐시 알려줘")).thenReturn(reservation);
        when(materialSearchService.searchByMetadata("캐시 알려줘", USER_ID)).thenReturn(List.of());
        when(materialSearchService.searchTopChunks("캐시 알려줘", USER_ID)).thenReturn(List.of(vectorChunk));
        when(openAiClient.chatComplete(anyString(), eq("캐시 알려줘"))).thenReturn("캐시는...");
        when(chatAskWriter.finalizeAnswer(eq(reservation), anyString(), eq(false), eq(List.of(vectorChunk))))
                .thenReturn(askResult(reservation));

        chatMessageService.ask(CHAT_ROOM_ID, USER_ID, new MessageCreateRequest("캐시 알려줘"));

        verify(materialSearchService).searchTopChunks("캐시 알려줘", USER_ID);
        verify(chatAskWriter).finalizeAnswer(reservation, "캐시는...", false, List.of(vectorChunk));
    }

    @Test
    void askMarksFallbackWhenBothMetadataAndVectorSearchFindNothing() {
        ChatAskWriter.Reservation reservation = reservation();
        when(chatAskWriter.reserve(CHAT_ROOM_ID, USER_ID, "오늘 날씨 어때?")).thenReturn(reservation);
        when(materialSearchService.searchByMetadata("오늘 날씨 어때?", USER_ID)).thenReturn(List.of());
        when(materialSearchService.searchTopChunks("오늘 날씨 어때?", USER_ID)).thenReturn(List.of());
        when(openAiClient.chatComplete(anyString(), eq("오늘 날씨 어때?"))).thenReturn("내 자료에는 없지만...");
        when(chatAskWriter.finalizeAnswer(eq(reservation), anyString(), eq(true), eq(List.of())))
                .thenReturn(askResult(reservation));

        chatMessageService.ask(CHAT_ROOM_ID, USER_ID, new MessageCreateRequest("오늘 날씨 어때?"));

        ArgumentCaptor<Boolean> isFallbackCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(chatAskWriter).finalizeAnswer(eq(reservation), anyString(), isFallbackCaptor.capture(), eq(List.of()));
        assertThat(isFallbackCaptor.getValue()).isTrue();
    }

    @Test
    void askAnswersImmediatelyWithoutSearchOrLlmWhenQuestionIsAggregateQuestion() {
        ChatAskWriter.Reservation reservation = reservation();
        when(chatAskWriter.reserve(CHAT_ROOM_ID, USER_ID, "내 자료 몇 개야?")).thenReturn(reservation);
        when(aggregateQuestionAnswerer.tryAnswer("내 자료 몇 개야?", USER_ID))
                .thenReturn(Optional.of("현재 저장하신 자료는 총 12개입니다."));
        when(chatAskWriter.finalizeAnswer(reservation, "현재 저장하신 자료는 총 12개입니다.", false, List.of()))
                .thenReturn(askResult(reservation));

        chatMessageService.ask(CHAT_ROOM_ID, USER_ID, new MessageCreateRequest("내 자료 몇 개야?"));

        verify(materialSearchService, never()).searchByMetadata(anyString(), eq(USER_ID));
        verify(materialSearchService, never()).searchTopChunks(anyString(), eq(USER_ID));
        verify(materialSearchService, never()).searchByTitleTagSimilarity(anyString(), eq(USER_ID));
        verify(openAiClient, never()).chatComplete(anyString(), anyString());
        verify(chatAskWriter).finalizeAnswer(reservation, "현재 저장하신 자료는 총 12개입니다.", false, List.of());
    }

    @Test
    void askFallsBackToTitleTagSimilarityWhenMetadataAndVectorSearchBothFindNothing() {
        ChatAskWriter.Reservation reservation = reservation();
        MaterialChunk similarityChunk = chunk(3L, "백엔드 소개");
        when(chatAskWriter.reserve(CHAT_ROOM_ID, USER_ID, "서버 쪽 자료 있어?")).thenReturn(reservation);
        when(materialSearchService.searchByMetadata("서버 쪽 자료 있어?", USER_ID)).thenReturn(List.of());
        when(materialSearchService.searchTopChunks("서버 쪽 자료 있어?", USER_ID)).thenReturn(List.of());
        when(materialSearchService.searchByTitleTagSimilarity("서버 쪽 자료 있어?", USER_ID))
                .thenReturn(List.of(similarityChunk));
        when(openAiClient.chatComplete(anyString(), eq("서버 쪽 자료 있어?"))).thenReturn("네, 백엔드 자료가 있습니다.");
        when(chatAskWriter.finalizeAnswer(eq(reservation), anyString(), eq(false), eq(List.of(similarityChunk))))
                .thenReturn(askResult(reservation));

        chatMessageService.ask(CHAT_ROOM_ID, USER_ID, new MessageCreateRequest("서버 쪽 자료 있어?"));

        verify(materialSearchService).searchByTitleTagSimilarity("서버 쪽 자료 있어?", USER_ID);
        verify(chatAskWriter).finalizeAnswer(reservation, "네, 백엔드 자료가 있습니다.", false, List.of(similarityChunk));
    }

    private ChatAskWriter.Reservation reservation() {
        User user = User.create("user1@example.com", "user1", null, null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        ChatRoom chatRoom = ChatRoom.create(user, "새로운 대화");
        ReflectionTestUtils.setField(chatRoom, "id", CHAT_ROOM_ID);
        ChatMessage userMessage = ChatMessage.createUserMessage(chatRoom, "질문");
        ReflectionTestUtils.setField(userMessage, "id", 100L);
        return new ChatAskWriter.Reservation(CHAT_ROOM_ID, USER_ID, userMessage);
    }

    private AskResult askResult(ChatAskWriter.Reservation reservation) {
        return new AskResult(
                reservation.userMessage().getChatRoom(),
                reservation.userMessage(),
                reservation.userMessage(),
                List.of(),
                null
        );
    }

    private MaterialChunk chunk(Long chunkId, String text) {
        User user = User.create("user1@example.com", "user1", null, null, null);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        Material material = Material.create(user, null, "자료", "https://example.com", PlatformType.WEB);
        ReflectionTestUtils.setField(material, "id", 500L + chunkId);
        MaterialChunk chunk = MaterialChunk.create(material, 0, text, "point-" + chunkId, null, null);
        ReflectionTestUtils.setField(chunk, "id", chunkId);
        return chunk;
    }
}
