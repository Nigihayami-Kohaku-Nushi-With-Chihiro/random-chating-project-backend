package random.chating.org.randomchatingproject.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountSettingsRequest {
    // 알림 설정
    private Boolean emailNotifications;
    private Boolean pushNotifications;
    private Boolean marketingNotifications;

    // 개인정보 보호 설정
    private Boolean showOnlineStatus;
    private Boolean profileVisible;
}