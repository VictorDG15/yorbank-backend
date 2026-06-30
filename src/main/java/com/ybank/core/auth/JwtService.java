package com.ybank.core.auth;
import io.jsonwebtoken.*;import io.jsonwebtoken.security.Keys;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;import java.nio.charset.StandardCharsets;import java.time.Instant;import java.util.*;
@Service
public class JwtService { private final byte[] key; public JwtService(@Value("${app.security.jwt-secret}") String secret){this.key=secret.getBytes(StandardCharsets.UTF_8);} 
 public String create(User u,long seconds){var now=Instant.now();return Jwts.builder().subject(u.email).claim("uid",u.id).claim("role",u.role.name()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(seconds))).signWith(Keys.hmacShaKeyFor(key)).compact();}
 public String subject(String token){return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(key)).build().parseSignedClaims(token).getPayload().getSubject();}
}
