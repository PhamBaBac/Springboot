package com.bacpham.kanban_service.configuration.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;
    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;
    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class));
    }

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS);

        // Lấy authorities từ UserDetails
        claims.put("authorities", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        return buildToken(claims, userDetails, jwtExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH);

        claims.put("authorities", userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        return buildToken(claims, userDetails, refreshExpiration);
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractUserEmail(String token) {
        return extractUsername(token); // sub chính là email
    }

    public String extractRole(String token) {
        try {
            Claims claims = extractAllClaims(token);
            List<String> authorities = claims.get("authorities", List.class);

            if (authorities != null) {
                return authorities.stream()
                        .filter(auth -> auth.startsWith("ROLE_"))
                        .map(auth -> auth.replace("ROLE_", ""))
                        .findFirst()
                        .orElse("USER"); // default role
            }
            return "USER"; // default role
        } catch (Exception e) {
            log.warn("Error extracting role from token: {}", e.getMessage());
            return "USER"; // default role
        }
    }



    // FIX QUAN TRỌNG: Method validation an toàn, không throw exception
    public boolean isTokenValid(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return false;
            }

            Claims claims = extractAllClaims(token);

            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (!TOKEN_TYPE_ACCESS.equals(tokenType) && !TOKEN_TYPE_REFRESH.equals(tokenType)) {
                log.warn("Invalid token type: {}", tokenType);
                return false;
            }

            Date expiration = claims.getExpiration();
            if (expiration.before(new Date())) {
                log.debug("Token expired at: {}", expiration);
                return false;
            }

            return true;

        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected error during token validation: {}", e.getMessage());
            return false;
        }
    }

    // FIX: Method cũ giữ lại cho compatibility, nhưng sử dụng method mới
    public boolean isTokenValidForUser(String token, UserDetails userDetails, String expectedTokenType) {
        if (!isTokenValid(token)) {
            return false;
        }

        try {
            final String username = extractUsername(token);
            final String tokenType = extractTokenType(token);
            return username.equals(userDetails.getUsername()) && expectedTokenType.equals(tokenType);
        } catch (Exception e) {
            log.warn("Error validating token with user details: {}", e.getMessage());
            return false;
        }
    }

    // FIX: Method kiểm tra expiration an toàn
    public boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException e) {
            return true; // Token đã hết hạn
        } catch (Exception e) {
            log.warn("Error checking token expiration: {}", e.getMessage());
            return true; // Coi như expired nếu có lỗi
        }
    }

    // FIX: Method extract expiration an toàn
    private Date extractExpiration(String token) {
        try {
            return extractClaim(token, Claims::getExpiration);
        } catch (ExpiredJwtException e) {
            // Trả về expiration date từ token đã hết hạn
            return e.getClaims().getExpiration();
        }
    }

    // FIX: Method extract claims an toàn
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean validateAccessToken(String token) {
        try {
            String type = extractTokenType(token);
            return TOKEN_TYPE_ACCESS.equals(type) && isTokenValid(token);
        } catch (Exception e) {
            log.warn("Error validating access token: {}", e.getMessage());
            return false;
        }
    }

    private Key getSignInKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(secretKey);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            log.error("Error generating signing key: {}", e.getMessage());
            throw new RuntimeException("Invalid secret key", e);
        }
    }


}