package random.chating.org.randomchatingproject.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyMailDto {
    private String code;    // 6자리 인증 코드
}