package com.itsqmet.produbanco.service;

import com.itsqmet.produbanco.model.Usuario;
import com.itsqmet.produbanco.repository.MovimientoRepository;
import com.itsqmet.produbanco.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public List<Usuario> obtenerTodos() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> obtenerPorId(UUID id) {
        return usuarioRepository.findById(id);
    }

    public Optional<String> registrar(Usuario usuario) {
        String email = usuario.getEmail().trim().toLowerCase();
        String cedula = usuario.getCedula().trim();

        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            return Optional.of("El email ya está registrado");
        }

        if (usuarioRepository.existsByCedula(cedula)) {
            return Optional.of("La cédula ya está registrada");
        }

        usuario.setEmail(email);
        usuario.setCedula(cedula);
        usuario.setRol(usuario.getRol().trim().toUpperCase());
        usuario.setContrasena(passwordEncoder.encode(usuario.getContrasena()));

        if (usuario.getSaldo() == null) {
            usuario.setSaldo(BigDecimal.ZERO);
        }

        usuarioRepository.save(usuario);
        return Optional.empty();
    }

    public boolean eliminar(UUID id) {
        if (!usuarioRepository.existsById(id)) {
            return false;
        }

        if (movimientoRepository.existsByUsuarioId(id)) {
            throw new IllegalArgumentException(
                    "No se puede eliminar un usuario que tiene movimientos");
        }

        usuarioRepository.deleteById(id);
        return true;
    }

    public Optional<Usuario> actualizar(UUID id, Usuario usuarioActualizado) {
        if (usuarioRepository.existsByEmailIgnoreCaseAndIdNot(
                usuarioActualizado.getEmail(), id)) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        if (usuarioRepository.existsByCedulaAndIdNot(
                usuarioActualizado.getCedula(), id)) {
            throw new IllegalArgumentException("La cédula ya está registrada");
        }

        return usuarioRepository.findById(id).map(usuario -> {
            usuario.setCedula(usuarioActualizado.getCedula().trim());
            usuario.setNombre(usuarioActualizado.getNombre());
            usuario.setEmail(usuarioActualizado.getEmail().trim().toLowerCase());
            usuario.setContrasena(
                    passwordEncoder.encode(usuarioActualizado.getContrasena()));
            usuario.setRol(usuarioActualizado.getRol().trim().toUpperCase());
            usuario.setSaldo(usuarioActualizado.getSaldo());
            return usuarioRepository.save(usuario);
        });
    }

    @Transactional
    public Usuario actualizarPerfil(
            String emailActual,
            Map<String, String> datos) {

        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(emailActual)
                .orElseThrow(() ->
                        new IllegalArgumentException("Usuario autenticado no encontrado"));

        String cedula = datos.get("cedula") == null
                ? ""
                : datos.get("cedula").trim();
        String nombre = datos.get("nombre") == null
                ? ""
                : datos.get("nombre").trim();
        String email = datos.get("email") == null
                ? ""
                : datos.get("email").trim().toLowerCase();
        String contrasena = datos.get("contrasena") == null
                ? ""
                : datos.get("contrasena");

        if (!cedula.matches("\\d{10}")) {
            throw new IllegalArgumentException(
                    "La cédula debe tener 10 dígitos");
        }

        if (nombre.isBlank()) {
            throw new IllegalArgumentException(
                    "El campo nombre no puede estar vacío");
        }

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                    "El email no tiene un formato válido");
        }

        if (usuarioRepository.existsByCedulaAndIdNot(
                cedula, usuario.getId())) {
            throw new IllegalArgumentException(
                    "La cédula ya está registrada");
        }

        if (usuarioRepository.existsByEmailIgnoreCaseAndIdNot(
                email, usuario.getId())) {
            throw new IllegalArgumentException(
                    "El email ya está registrado");
        }

        usuario.setCedula(cedula);
        usuario.setNombre(nombre);
        usuario.setEmail(email);

        if (!contrasena.isBlank()) {
            if (contrasena.length() < 8) {
                throw new IllegalArgumentException(
                        "La contraseña debe tener mínimo 8 caracteres");
            }

            usuario.setContrasena(passwordEncoder.encode(contrasena));
        }

        return usuarioRepository.save(usuario);
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioRepository.findByEmailIgnoreCase(email);
    }

    public Optional<Usuario> buscarPorCedula(String cedula) {
        return usuarioRepository.findByCedula(cedula);
    }

    public long cantidadUsuarios() {
        return usuarioRepository.count();
    }
}
