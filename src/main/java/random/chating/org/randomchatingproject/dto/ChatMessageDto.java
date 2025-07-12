package random.chating.org.randomchatingproject.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatMessageDto {
    private String type;
    private String roomId;
    private String message;
    private String senderUsername;
    private String senderAvatar;
    private String timestamp;
}