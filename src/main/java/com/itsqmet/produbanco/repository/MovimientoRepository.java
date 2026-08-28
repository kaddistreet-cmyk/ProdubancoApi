package com.itsqmet.produbanco.repository;

import com.itsqmet.produbanco.model.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MovimientoRepository extends JpaRepository<Movimiento, UUID> {

    List<Movimiento> findAllByOrderByFechaDesc();

    List<Movimiento> findByUsuarioIdOrderByFechaDesc(UUID usuarioId);

    List<Movimiento> findByComprobanteOrderByFechaAsc(Long comprobante);

    boolean existsByUsuarioId(UUID usuarioId);

    @Query(value = "SELECT nextval('public.movimiento_comprobante_seq')", nativeQuery = true)
    Long siguienteComprobante();
}
