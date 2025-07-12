package random.chating.org.randomchatingproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailChangeRequest {
    private String newEmail;
    private String password; // 보안을 위한 비밀번호 확인
}