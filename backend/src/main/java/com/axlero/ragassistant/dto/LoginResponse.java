package com.axlero.ragassistant.dto;

import com.axlero.ragassistant.entities.Role;

public record LoginResponse(String token, String email, Role role) {

    @Override
    public String toString() {
        return "LoginResponse[token=***, email=" + email + ", role=" + role + "]";
    }
}