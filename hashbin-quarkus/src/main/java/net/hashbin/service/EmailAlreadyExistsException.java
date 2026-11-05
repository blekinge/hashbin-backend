package net.hashbin.service;

/** Thrown by {@link UserService#register} when an e-mail is already taken. */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException() {
        super("Email already registered");
    }
}
