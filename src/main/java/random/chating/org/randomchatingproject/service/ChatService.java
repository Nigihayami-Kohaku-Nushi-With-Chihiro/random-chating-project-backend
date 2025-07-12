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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final UserRepository userRepository;

    public ChatMessage saveMessage(String roomId, Long senderId, String message, ChatMessage.MessageType type) {
        ChatMessage chatMessage = ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .message(message)
                .type(type)
                .timestamp(LocalDateTime.now())
                .build();

        return chatMessageRepository.save(chatMessage);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getChatHistoryWithUserInfo(String roomId) {
        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByTimestampAsc(roomId);

        return messages.stream().map(message -> {
            User sender = null;
            if (message.getType() != ChatMessage.MessageType.SYSTEM) {
                sender = userRepository.findById(message.getSenderId()).orElse(null);
            }

            return Map.of(
                    "id", message.getId(),
                    "message", message.getMessage(),
                    "timestamp", message.getTimestamp(),
                    "type", message.getType().name(),
                    "senderId", message.getSenderId(),
                    "senderUsername", sender != null ? sender.getUsername() : "SYSTEM",
                    "senderAvatar", sender != null ? sender.getUsername().substring(0, 1).toUpperCase() : "S"
            );
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getChatHistory(String roomId) {
        return chatMessageRepository.findByRoomIdOrderByTimestampAsc(roomId);
    }

    @Transactional(readOnly = true)
    public boolean canUserAccessRoom(Long userId, String roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElse(null);

        if (chatRoom == null) {
            return false;
        }

        boolean hasAccess = chatRoom.getUser1Id().equals(userId) || chatRoom.getUser2Id().equals(userId);

        return hasAccess && chatRoom.getStatus() == ChatRoom.RoomStatus.ACTIVE;
    }

    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public User getChatPartner(String roomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId).orElse(null);
        if (chatRoom == null) {
            return null;
        }

        Long partnerId = chatRoom.getUser1Id().equals(userId) ?
                chatRoom.getUser2Id() : chatRoom.getUser1Id();

        return getUserById(partnerId);
    }
}