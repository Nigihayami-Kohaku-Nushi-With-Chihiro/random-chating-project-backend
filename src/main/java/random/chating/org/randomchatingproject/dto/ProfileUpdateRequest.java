package random.chating.org.randomchatingproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequest {
    private String nickname;        // 닉네임 (username과 다를 수 있음)
    private Integer age;           // 나이
    private String bio;            // 자기소개
    private String location;       // 지역
    private List<String> interests; // 관심사 목록
}