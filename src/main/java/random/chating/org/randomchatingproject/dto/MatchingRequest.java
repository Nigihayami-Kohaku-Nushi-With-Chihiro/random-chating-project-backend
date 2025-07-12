package random.chating.org.randomchatingproject.dto;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MatchingRequest {
    private String action; // START, CANCEL, END
    private String roomId;
}