package com.eventflow.auth.dto;
import com.eventflow.auth.domain.AppUser;
public record AuthUserResponse(Long id,String email,String nom) {
    public static AuthUserResponse from(AppUser user) { return new AuthUserResponse(user.getId(),user.getEmail(),user.getNom()); }
}
