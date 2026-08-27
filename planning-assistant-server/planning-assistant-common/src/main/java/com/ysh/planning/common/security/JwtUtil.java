package com.ysh.planning.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static final long EXPIRY_MS = 7L * 24 * 3600 * 1000;

    @Value("${jwt.secret}")
    private String secret;

    @PostConstruct
    void validateSecret() {
        getKey();
    }

    public String generateToken(Long userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("token_use", "miniapp")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRY_MS))
                .signWith(getKey())
                .compact();
    }

    public Long parseUserId(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String tokenUse = claims.get("token_use", String.class);
        if (tokenUse != null && !"miniapp".equals(tokenUse)) throw new JwtException("not a miniapp token");
        return Long.parseLong(claims.getSubject());
    }

    public String generateWebToken(Long userId) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("token_use", "web")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 8L * 3600 * 1000))
                .signWith(getKey())
                .compact();
    }

    public Long parseWebUserId(String token) throws JwtException {
        Claims claims = Jwts.parser().verifyWith(getKey()).build()
                .parseSignedClaims(token).getPayload();
        if (!"web".equals(claims.get("token_use", String.class))) {
            throw new JwtException("not a web token");
        }
        return Long.parseLong(claims.getSubject());
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}
