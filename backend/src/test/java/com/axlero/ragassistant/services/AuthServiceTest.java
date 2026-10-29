package com.axlero.ragassistant.services;

import com.axlero.ragassistant.dto.RegisterRequest;
import com.axlero.ragassistant.dto.RegisterResponse;
import com.axlero.ragassistant.entities.Role;
import com.axlero.ragassistant.entities.User;
import com.axlero.ragassistant.exceptions.EmailAlreadyRegisteredException;
import com.axlero.ragassistant.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axlero.ragassistant.dto.LoginRequest;
import com.axlero.ragassistant.dto.LoginResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Test
    void registerNormalizesEmailHashesPasswordAndAssignsUserRole() {
                AuthService service = new AuthService(userRepository, passwordEncoder, jwtService);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("HASHED");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        RegisterResponse response = service.register(new RegisterRequest("  Alice@Example.com ", "secret123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("HASHED");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    void registerRejectsAnExistingEmail() {
                AuthService service = new AuthService(userRepository, passwordEncoder, jwtService);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("alice@example.com", "secret123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void registerMapsAUniqueConstraintRaceToConflict() {
                AuthService service = new AuthService(userRepository, passwordEncoder, jwtService);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("HASHED");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("alice@example.com", "secret123")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

        @Test
    void loginReturnsATokenForValidCredentials() {
        AuthService service = new AuthService(userRepository, passwordEncoder, jwtService);
        UUID id = UUID.randomUUID();
        User user = new User("alice@example.com", "HASHED", Role.USER);
        ReflectionTestUtils.setField(user, "id", id);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "HASHED")).thenReturn(true);
        when(jwtService.generateToken(id, "alice@example.com", Role.USER)).thenReturn("TOKEN");

        LoginResponse response = service.login(new LoginRequest("  Alice@Example.com ", "secret123"));

        assertThat(response.token()).isEqualTo("TOKEN");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.role()).isEqualTo(Role.USER);
    }

    @Test
    void loginRejectsAnUnknownEmailWithTheGenericMessage() {
        AuthService service = new AuthService(userRepository, passwordEncoder, jwtService);
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nobody@example.com", "secret123")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(jwtService, never()).generateToken(any(), any(), any());
    }

    @Test
    void loginRejectsAWrongPasswordWithTheSameMessage() {
        AuthService service = new AuthService(userRepository, passwordEncoder, jwtService);
        User user = new User("alice@example.com", "HASHED", Role.USER);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "HASHED")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("alice@example.com", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(jwtService, never()).generateToken(any(), any(), any());
    }
}