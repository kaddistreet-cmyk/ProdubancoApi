package com.itsqmet.produbanco.repository;

import com.itsqmet.produbanco.model.Usuario;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    Optional<Usuario> findByCedula(String cedula);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCedula(String cedula);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    boolean existsByCedulaAndIdNot(String cedula, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id IN :ids ORDER BY u.id")
    List<Usuario> buscarUsuariosParaActualizar(@Param("ids") List<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE LOWER(u.email) = LOWER(:email)")
    Optional<Usuario> buscarPorEmailParaActualizar(@Param("email") String email);

    @Query("SELECT COALESCE(SUM(u.saldo), 0) FROM Usuario u")
    BigDecimal sumarSaldos();
}
