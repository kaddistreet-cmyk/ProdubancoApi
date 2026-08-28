package com.itsqmet.produbanco.controller;

import com.itsqmet.produbanco.model.Usuario;
import com.itsqmet.produbanco.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Value("${app.jwt.expiration}")
    private long jwtExpiration;

    @PostMapping("/registro")
    public ResponseEntity<?> registrar(
            @Valid @RequestBody Usuario usuario,
            BindingResult result) {

        if (result.hasErrors()) {
            Map<String, String> errores = new HashMap<>();
            result.getFieldErrors().forEach(error ->
                    errores.put(error.getField(), error.getDefaultMessage())
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errores);
        }

        if (usuarioService.cantidadUsuarios() > 0) {
            usuario.setRol("CLIENTE");
            usuario.setSaldo(java.math.BigDecimal.ZERO);
        }

        return usuarioService.registrar(usuario)
                .map(error -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", error)))
                .orElse(ResponseEntity.status(HttpStatus.CREATED)
                        .body(Map.of("mensaje", "Usuario registrado correctamente")));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> credenciales) {

        String email = credenciales.get("email");
        String contrasena = credenciales.get("contrasena");

        if (email == null || email.isBlank() ||
                contrasena == null || contrasena.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Debes ingresar el email y la contraseña"));
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, contrasena)
            );

            Usuario usuario = usuarioService.buscarPorEmail(email)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Usuario no encontrado"));

            List<String> roles = authentication.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .toList();

            Instant ahora = Instant.now();
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer("produbanco")
                    .issuedAt(ahora)
                    .expiresAt(ahora.plusSeconds(jwtExpiration))
                    .subject(usuario.getEmail())
                    .claim("roles", roles)
                    .build();

            JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
            String token = jwtEncoder.encode(
                    JwtEncoderParameters.from(header, claims)).getTokenValue();

            return ResponseEntity.ok(Map.of(
                    "mensaje", "Login exitoso",
                    "token", token,
                    "tipo", "Bearer",
                    "email", usuario.getEmail(),
                    "rol", usuario.getRol()
            ));

        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Email o contraseña incorrectos"));
        }
    }

    @GetMapping("/usuario")
    public ResponseEntity<?> usuario(Authentication authentication) {
        Usuario usuario = usuarioService.buscarPorEmail(authentication.getName())
                .orElseThrow(() ->
                        new IllegalArgumentException("Usuario autenticado no encontrado"));

        return ResponseEntity.ok(Map.of(
                "mensaje", "Acceso autorizado",
                "id", usuario.getId(),
                "cedula", usuario.getCedula(),
                "email", usuario.getEmail(),
                "nombre", usuario.getNombre(),
                "rol", usuario.getRol(),
                "saldo", usuario.getSaldo()
        ));
    }

    @PutMapping("/usuario")
    public ResponseEntity<?> actualizarUsuario(
            @RequestBody Map<String, String> datos,
            Authentication authentication) {

        try {
            Usuario usuario = usuarioService.actualizarPerfil(
                    authentication.getName(), datos);

            return ResponseEntity.ok(Map.of(
                    "mensaje", "Datos actualizados correctamente",
                    "id", usuario.getId(),
                    "cedula", usuario.getCedula(),
                    "email", usuario.getEmail(),
                    "nombre", usuario.getNombre(),
                    "rol", usuario.getRol(),
                    "saldo", usuario.getSaldo()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
