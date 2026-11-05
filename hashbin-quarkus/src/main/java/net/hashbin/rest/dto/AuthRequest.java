package net.hashbin.rest.dto;

/** Register/login request body: {@code {"email": "...", "password": "..."}}. */
public class AuthRequest {
    public String email;
    public String password;
}
