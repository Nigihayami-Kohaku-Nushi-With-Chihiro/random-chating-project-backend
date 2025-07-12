package random.chating.org.randomchatingproject.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import random.chating.org.randomchatingproject.entity.ChatMessage;
import random.chating.org.randomchatingproject.entity.ChatRoom;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.repository.ChatMessageRepository;
import random.chating.org.randomchatingproject.repository.ChatRoomRepository;
import random.chating.org.randomchatingproject.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    /**
     * 채팅 메시지 저장
     */
    public ChatMessage saveMessage(String roomId, Long senderId, String message, ChatMessage.MessageType type) {
        try {
            ChatMessage chatMessage = ChatMessage.builder()
                    .roomId(roomId)
                    .senderId(senderId)
                    .message(message)
                    .type(type)
                    .timestamp(LocalDateTime.now())
                    .build();

            ChatMessage saved = chatMessageRepository.save(chatMessage);
            log.info("메시지 저장 완료: roomId={}, senderId={}, type={}", roomId, senderId, type);
            return saved;

        } catch (Exception e) {
            log.error("메시지 저장 실패: roomId={}, senderId={}, error={}", roomId, senderId, e.getMessage(), e);
            throw new RuntimeException("메시지 저장에 실패했습니다", e);
        }
    }

    /**
     * 사용자 정보와 함께 채팅 기록 조회
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChatHistoryWithUserInfo(String roomId) {
        try {
            List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByTimestampAsc(roomId);
            log.info("채팅 기록 조회: roomId={}, 메시지 수={}", roomId, messages.size());

            return messages.stream().map(message -> {
                Map<String, Object> messageMap = new HashMap<>();

                // 기본 메시지 정보
                messageMap.put("id", message.getId());
                messageMap.put("message", message.getMessage());
                messageMap.put("timestamp", message.getTimestamp());
                messageMap.put("type", message.getType().name());
                messageMap.put("senderId", message.getSenderId());

                // 사용자 정보 추가
                if (message.getType() != ChatMessage.MessageType.SYSTEM) {
                    try {
                        User sender = userRepository.findById(message.getSenderId()).orElse(null);
                        if (sender != null) {
                            messageMap.put("senderUsername", sender.getUsername());
                            messageMap.put("senderAvatar", sender.getUsername().substring(0, 1).toUpperCase());
                        } else {
                            log.warn("사용자를 찾을 수 없음: senderId={}", message.getSenderId());
                            messageMap.put("senderUsername", "Unknown");
                            messageMap.put("senderAvatar", "?");
                        }
                    } catch (Exception e) {
                        log.error("사용자 정보 조회 실패: senderId={}, error={}", message.getSenderId(), e.getMessage());
                        messageMap.put("senderUsername", "Unknown");
                        messageMap.put("senderAvatar", "?");
                    }
                } else {
                    // 시스템 메시지
                    messageMap.put("senderUsername", "SYSTEM");
                    messageMap.put("senderAvatar", "S");
                }

                return messageMap;
            }).toList();

        } catch (Exception e) {
            log.error("채팅 기록 조회 실패: roomId={}, error={}", roomId, e.getMessage(), e);
            return List.of(); // 빈 리스트 반환
        }
    }

    /**
     * 단순 채팅 기록 조회
     */
    @Transactional(readOnly = true)
    public List<ChatMessage> getChatHistory(String roomId) {
        try {
            return chatMessageRepository.findByRoomIdOrderByTimestampAsc(roomId);
        } catch (Exception e) {
            log.error("채팅 기록 조회 실패: roomId={}, error={}", roomId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 사용자가 채팅방에 접근할 수 있는지 확인
     */
    @Transactional(readOnly = true)
    public boolean canUserAccessRoom(Long userId, String roomId) {
        try {
            ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElse(null);

            if (chatRoom == null) {
                log.warn("채팅방을 찾을 수 없음: roomId={}", roomId);
                return false;
            }

            boolean hasAccess = chatRoom.getUser1Id().equals(userId) || chatRoom.getUser2Id().equals(userId);
            boolean isActive = chatRoom.getStatus() == ChatRoom.RoomStatus.ACTIVE;

            log.info("채팅방 접근 권한 확인: userId={}, roomId={}, hasAccess={}, isActive={}",
                    userId, roomId, hasAccess, isActive);

            return hasAccess && isActive;

        } catch (Exception e) {
            log.error("채팅방 접근 권한 확인 실패: userId={}, roomId={}, error={}", userId, roomId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 사용자 ID로 사용자 정보 조회
     */
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        try {
            return userRepository.findById(userId).orElse(null);
        } catch (Exception e) {
            log.error("사용자 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 채팅 상대방 정보 조회
     */
    @Transactional(readOnly = true)
    public User getChatPartner(String roomId, Long userId) {
        try {
            ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElse(null);
            if (chatRoom == null) {
                log.warn("채팅방을 찾을 수 없음: roomId={}", roomId);
                return null;
            }

            Long partnerId = chatRoom.getUser1Id().equals(userId)
                    ? chatRoom.getUser2Id()
                    : chatRoom.getUser1Id();

            User partner = getUserById(partnerId);
            log.info("채팅 상대방 조회: roomId={}, userId={}, partnerId={}, partnerFound={}",
                    roomId, userId, partnerId, partner != null);

            return partner;

        } catch (Exception e) {
            log.error("채팅 상대방 조회 실패: roomId={}, userId={}, error={}", roomId, userId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 채팅방 정보 조회
     */
    @Transactional(readOnly = true)
    public ChatRoom getChatRoom(String roomId) {
        try {
            return chatRoomRepository.findById(roomId).orElse(null);
        } catch (Exception e) {
            log.error("채팅방 정보 조회 실패: roomId={}, error={}", roomId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 사용자의 활성 채팅방 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ChatRoom> getUserActiveChatRooms(Long userId) {
        try {
            return chatRoomRepository.findByUserId(userId).stream()
                    .filter(room -> room.getStatus() == ChatRoom.RoomStatus.ACTIVE)
                    .toList();
        } catch (Exception e) {
            log.error("사용자 활성 채팅방 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 채팅방 상태 업데이트
     */
    public void updateChatRoomStatus(String roomId, ChatRoom.RoomStatus status) {
        try {
            ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElse(null);
            if (chatRoom != null) {
                chatRoom.setStatus(status);
                chatRoomRepository.save(chatRoom);
                log.info("채팅방 상태 업데이트: roomId={}, status={}", roomId, status);
            } else {
                log.warn("채팅방을 찾을 수 없어 상태 업데이트 실패: roomId={}", roomId);
            }
        } catch (Exception e) {
            log.error("채팅방 상태 업데이트 실패: roomId={}, status={}, error={}", roomId, status, e.getMessage(), e);
        }
    }

    /**
     * 사용자별 총 메시지 수 조회
     */
    @Transactional(readOnly = true)
    public long getUserMessageCount(Long userId) {
        try {
            return chatMessageRepository.countBySenderId(userId);
        } catch (Exception e) {
            log.error("사용자 메시지 수 조회 실패: userId={}, error={}", userId, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 채팅방별 메시지 수 조회
     */
    @Transactional(readOnly = true)
    public long getRoomMessageCount(String roomId) {
        try {
            return chatMessageRepository.findByRoomIdOrderByTimestampAsc(roomId).size();
        } catch (Exception e) {
            log.error("채팅방 메시지 수 조회 실패: roomId={}, error={}", roomId, e.getMessage(), e);
            return 0;
        }
    }
}