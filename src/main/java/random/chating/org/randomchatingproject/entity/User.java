package random.chating.org.randomchatingproject.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id  // JPA용 @Id (jakarta.persistence.Id)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== 기본 정보 ==========
    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    // ========== 매칭에 필요한 정보 ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender; // 이성 매칭을 위해 필수!

    private Integer age; // JWT에 넣을 정보 + 매칭 조건

    private boolean isVerified = false;

    // ========== 권한 관리 ==========
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER; // 기본값: 일반 사용자

    // ========== Spring Security 계정 상태 ==========
    @Builder.Default
    private Boolean enabled = true;

    @Builder.Default
    private Boolean accountNonExpired = true;

    @Builder.Default
    private Boolean accountNonLocked = true;

    @Builder.Default
    private Boolean credentialsNonExpired = true;

    // ========== Spring Security UserDetails 구현 ==========
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Role에 따라 권한 부여
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired != null ? accountNonExpired : true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked != null ? accountNonLocked : true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired != null ? credentialsNonExpired : true;
    }

    @Override
    public boolean isEnabled() {
        return enabled != null ? enabled : true;
    }

    // ========== Enum 정의 ==========
    public enum Gender {
        MALE, FEMALE
    }

    public enum Role {
        USER, ADMIN
    }
}