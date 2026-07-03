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
            log.error("Invalid JWT signature: JWT bị giả mạo chữ ký!");
        } catch (io.jsonwebtoken.MalformedJwtException ex) {
            log.error("Invalid JWT token: Token không đúng định dạng!");
        } catch (io.jsonwebtoken.ExpiredJwtException ex) {
            log.error("Expired JWT token: Token đã hết hạn!");
        } catch (io.jsonwebtoken.UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: Token không được hỗ trợ!");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: Chuỗi claims trống!");
        }
        return false;
    }
}