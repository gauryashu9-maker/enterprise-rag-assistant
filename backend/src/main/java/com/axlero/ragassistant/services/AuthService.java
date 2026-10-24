package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.RegisterRequest;
import com.axlero.ragassistant.dto.RegisterResponse;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.entities.User;
import com.axlero.ragassistant.exceptions.EmailAlreadyRegisteredException;
import com.axlero.ragassistant.repositories.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        String hash = passwordEncoder.encode(request.password());
        try {
            User saved = userRepository.saveAndFlush(new User(email, hash, Role.USER));
            return new RegisterResponse(saved.getId(), saved.getEmail(), saved.getRole());
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyRegisteredException();
        }
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}