package random.chating.org.randomchatingproject.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import random.chating.org.randomchatingproject.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {

    private final Key key;
    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 24; // 24시간

    public JwtProvider(@Value("${jwt.secret}") String secretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        log.info("JWT Provider 초기화 완료");
    }

    // ========== JWT 토큰 생성 ==========

    /**
     * User Entity로 JWT 토큰 생성 (Subject에는 userId, Claims에는 다른 정보들)
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + EXPIRATION_TIME);

        String token = Jwts.builder()
                .setSubject(user.getId().toString())         // ⭐ Subject에 userId (숫자)
                .claim("username", user.getUsername())       // username은 claim으로
                .claim("userId", user.getId())               // 사용자 ID (중복이지만 호환성)
                .claim("email", user.getEmail())             // 이메일
                .claim("gender", user.getGender().name())    // 성별 (MALE/FEMALE)
                .claim("age", user.getAge())                 // 나이
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        log.info("JWT 토큰 생성: userId={}, username={}, email={}, gender={}, age={}",
                user.getId(), user.getUsername(), user.getEmail(), user.getGender(), user.getAge());

        return token;
    }

    // ========== JWT 토큰 검증 ==========

    /**
     * JWT 토큰 유효성 검증
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("유효하지 않은 JWT 서명: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("지원하지 않는 JWT 토큰: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 JWT 토큰: {}", e.getMessage());
        } catch (Exception e) {
            log.error("JWT 토큰 검증 중 예외 발생: {}", e.getMessage());
        }
        return false;
    }

    // ========== JWT 정보 추출 ==========

    /**
     * 토큰에서 userId 추출 (Subject에서)
     */
    public Long getUserIdFromSubject(String token) {
        try {
            Claims claims = getClaims(token);
            String subject = claims.getSubject();
            return Long.parseLong(subject);
        } catch (Exception e) {
            log.error("JWT Subject에서 userId 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 사용자명 추출 (Claim에서)
     */
    public String getUsername(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.get("username", String.class);
        } catch (Exception e) {
            log.error("JWT에서 사용자명 추출 실패: {}", e.getMessage());
            throw new RuntimeException("JWT 파싱 실패", e);
        }
    }

    /**
     * 토큰에서 사용자 ID 추출 (Claim에서 - 호환성)
     */
    public Long getUserId(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.get("userId", Long.class);
        } catch (Exception e) {
            log.error("JWT에서 사용자 ID 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 이메일 추출
     */
    public String getEmail(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.get("email", String.class);
        } catch (Exception e) {
            log.error("JWT에서 이메일 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 성별 추출
     */
    public String getGender(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.get("gender", String.class);
        } catch (Exception e) {
            log.error("JWT에서 성별 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 토큰에서 나이 추출
     */
    public Integer getAge(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.get("age", Integer.class);
        } catch (Exception e) {
            log.error("JWT에서 나이 추출 실패: {}", e.getMessage());
            return null;
        }
    }

    // ========== 내부 메서드 ==========

    /**
     * 토큰에서 Claims 추출
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}