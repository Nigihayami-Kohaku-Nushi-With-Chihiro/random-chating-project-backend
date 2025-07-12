package random.chating.org.randomchatingproject.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MatchingResult {
    private boolean success;
    private boolean matched;
    private String roomId;
    private Long partnerId;
    private String message;
    private Integer waitingCount;
}