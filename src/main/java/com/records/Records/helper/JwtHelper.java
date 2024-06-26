package com.records.Records.helper;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Ashwini Charles on 3/10/2024
 * @project RecordsSpringBootApplication
 */

@Component
public class JwtHelper {

    public static final long JWT_TOKEN_VALIDITY = 18000;

    @Autowired
    private Environment env;

    //retrieve username from jwt token
    public String extractUsername(String token) {

        DecodedJWT decodedJWT = JWT.decode(token);
        String encodedBody = decodedJWT.getPayload();
        String body = new String(Base64.getUrlDecoder().decode(encodedBody));
        JSONObject jsonObject = new JSONObject(body);
        return jsonObject.getString("sub");
    }

    public Date getExpirationDateFromToken(String token) {
        DecodedJWT decodedJWT = JWT.decode(token);
        return decodedJWT.getExpiresAt();
    }

    //check if the token has expired
    private Boolean isTokenExpired(String token) {
        DecodedJWT decodedJWT = JWT.decode(token);
        Date expiresAt = decodedJWT.getExpiresAt();
        return expiresAt.before(new Date());
    }

    //generate token for user
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return doGenerateToken(claims, userDetails.getUsername());
    }

    private String doGenerateToken(Map<String, Object> claims, String subject) {

        return Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY * 1000))
                .signWith(this.getSecret(), SignatureAlgorithm.HS512).compact();
    }

    //validate token
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private SecretKey getSecret() {
       return Keys.hmacShaKeyFor(this.env.getProperty("authentication.client.client-secret").getBytes(StandardCharsets.UTF_8));
    }

}
