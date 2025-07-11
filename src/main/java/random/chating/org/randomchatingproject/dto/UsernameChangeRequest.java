package random.chating.org.randomchatingproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsernameChangeRequest {
    private String newUsername;
    private String password; // 확인용
}