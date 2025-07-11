package random.chating.org.randomchatingproject.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String gender;
    private Integer age;
    private String role;
    private boolean isAuthenticated;  // 이것만 추가!

}