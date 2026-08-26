package com.commercehub.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationInMs;

    @Value("${jwt.refreshExpiration}")
    private long jwtRefreshExpirationInMs;

    // Tạo key mã hóa từ chuỗi secret Base64 trong application.yml
    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // Sinh Access Token
    public String generateAccessToken(Authentication authentication) {
        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }




    // Lấy username/email từ Token
    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(authToken);
            return true;
        } catch (io.jsonwebtoken.security.SignatureException ex) {
            log.warn("[AUTH][ACCESS_TOKEN_INVALID_SIGNATURE] Access token JWT có chữ ký không hợp lệ.");
        } catch (io.jsonwebtoken.MalformedJwtException ex) {
            log.warn("[AUTH][ACCESS_TOKEN_MALFORMED] Access token JWT không đúng định dạng.");
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            log.warn(
                    "[AUTH][ACCESS_TOKEN_EXPIRED] Access token JWT đã hết hạn lúc {}. "
                            + "Request cần dùng refresh token để nhận access token mới.",
                    ex.getClaims().getExpiration()
            );
        } catch (io.jsonwebtoken.UnsupportedJwtException ex) {
            log.warn("[AUTH][ACCESS_TOKEN_UNSUPPORTED] Access token JWT sử dụng định dạng hoặc thuật toán không được hỗ trợ.");
        } catch (IllegalArgumentException ex) {
            log.warn("[AUTH][ACCESS_TOKEN_EMPTY] Access token JWT hoặc claims đang trống.");
        }
        return false;
    }
}
