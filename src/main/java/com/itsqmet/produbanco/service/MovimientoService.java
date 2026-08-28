package com.itsqmet.produbanco.service;

import com.itsqmet.produbanco.model.Movimiento;
import com.itsqmet.produbanco.model.Usuario;
import com.itsqmet.produbanco.repository.MovimientoRepository;
import com.itsqmet.produbanco.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MovimientoService {

    @Autowired
    private MovimientoRepository movimientoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    public List<Movimiento> obtenerTodos(String email, boolean esAdmin) {
        if (esAdmin) {
            return movimientoRepository.findAllByOrderByFechaDesc();
        }

        Usuario usuario = obtenerUsuarioPorEmail(email);
        return movimientoRepository.findByUsuarioIdOrderByFechaDesc(usuario.getId());
    }

    public Optional<Movimiento> buscarPorId(
            UUID id, String email, boolean esAdmin) {

        return movimientoRepository.findById(id)
                .filter(movimiento -> esAdmin ||
                        movimiento.getUsuario().getEmail().equalsIgnoreCase(email));
    }

    @Transactional
    public Movimiento crearMovimiento(
            Movimiento movimiento, String email, boolean esAdmin) {

        Usuario usuario = obtenerUsuarioParaMovimiento(
                movimiento, email, esAdmin);

        String tipo = normalizarTipo(movimiento.getTipo());
        validarMovimientoBasico(tipo);

        movimiento.setTipo(tipo);
        movimiento.setUsuario(usuario);
        movimiento.setFecha(OffsetDateTime.now());
        movimiento.setComprobante(movimientoRepository.siguienteComprobante());

        aplicarSaldo(usuario, tipo, movimiento.getMonto());
        usuarioRepository.save(usuario);

        return movimientoRepository.save(movimiento);
    }

    private Usuario obtenerUsuarioParaMovimiento(
            Movimiento movimiento, String email, boolean esAdmin) {

        if (esAdmin && movimiento.getUsuario() != null &&
                movimiento.getUsuario().getId() != null) {

            Usuario cliente = usuarioRepository.buscarUsuariosParaActualizar(
                            List.of(movimiento.getUsuario().getId()))
                    .stream()
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException("Cliente no encontrado"));

            if (!cliente.getRol().equals("CLIENTE")) {
                throw new IllegalArgumentException(
                        "Debes seleccionar una cuenta de cliente");
            }

            return cliente;
        }

        return usuarioRepository.buscarPorEmailParaActualizar(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Usuario autenticado no encontrado"));
    }

    @Transactional
    public List<Movimiento> transferir(
            String emailOrigen,
            String cedulaDestino,
            BigDecimal monto,
            String descripcion) {

        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }

        Usuario origenBuscado = obtenerUsuarioPorEmail(emailOrigen);
        Usuario destinoBuscado = usuarioRepository.findByCedula(cedulaDestino)
                .orElseThrow(() ->
                        new IllegalArgumentException("La cédula de destino no existe"));

        if (origenBuscado.getId().equals(destinoBuscado.getId())) {
            throw new IllegalArgumentException(
                    "No puedes realizar una transferencia a tu propia cuenta");
        }

        List<UUID> ids = new ArrayList<>(List.of(
                origenBuscado.getId(), destinoBuscado.getId()));
        ids.sort(Comparator.comparing(UUID::toString));

        List<Usuario> usuarios = usuarioRepository.buscarUsuariosParaActualizar(ids);

        Usuario origen = usuarios.stream()
                .filter(usuario -> usuario.getId().equals(origenBuscado.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Usuario origen no encontrado"));

        Usuario destino = usuarios.stream()
                .filter(usuario -> usuario.getId().equals(destinoBuscado.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Usuario destino no encontrado"));

        if (origen.getSaldo().compareTo(monto) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente para la transferencia");
        }

        Long comprobante = movimientoRepository.siguienteComprobante();
        OffsetDateTime fecha = OffsetDateTime.now();

        origen.setSaldo(origen.getSaldo().subtract(monto));
        destino.setSaldo(destino.getSaldo().add(monto));
        usuarioRepository.saveAll(List.of(origen, destino));

        Movimiento enviado = new Movimiento();
        enviado.setUsuario(origen);
        enviado.setTipo("TRANSFERENCIA_ENVIADA");
        enviado.setMonto(monto);
        enviado.setFecha(fecha);
        enviado.setDescripcion(descripcion);
        enviado.setComprobante(comprobante);

        Movimiento recibido = new Movimiento();
        recibido.setUsuario(destino);
        recibido.setTipo("TRANSFERENCIA_RECIBIDA");
        recibido.setMonto(monto);
        recibido.setFecha(fecha);
        recibido.setDescripcion(descripcion);
        recibido.setComprobante(comprobante);

        return movimientoRepository.saveAll(List.of(enviado, recibido));
    }

    @Transactional
    public Optional<Movimiento> actualizar(
            UUID id,
            Movimiento movimientoActualizado,
            String email,
            boolean esAdmin) {

        Optional<Movimiento> encontrado = buscarPorId(id, email, esAdmin);

        if (encontrado.isEmpty()) {
            return Optional.empty();
        }

        Movimiento movimiento = encontrado.get();

        if (esTransferencia(movimiento.getTipo())) {
            throw new IllegalArgumentException(
                    "Las transferencias no se pueden modificar");
        }

        Usuario usuario = usuarioRepository
                .buscarUsuariosParaActualizar(List.of(movimiento.getUsuario().getId()))
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Usuario del movimiento no encontrado"));

        revertirSaldo(usuario, movimiento.getTipo(), movimiento.getMonto());

        String tipo = normalizarTipo(movimientoActualizado.getTipo());
        validarMovimientoBasico(tipo);
        aplicarSaldo(usuario, tipo, movimientoActualizado.getMonto());

        movimiento.setUsuario(usuario);
        movimiento.setTipo(tipo);
        movimiento.setMonto(movimientoActualizado.getMonto());
        movimiento.setDescripcion(movimientoActualizado.getDescripcion());

        usuarioRepository.save(usuario);
        return Optional.of(movimientoRepository.save(movimiento));
    }

    @Transactional
    public boolean eliminar(UUID id, String email, boolean esAdmin) {
        Optional<Movimiento> encontrado = buscarPorId(id, email, esAdmin);

        if (encontrado.isEmpty()) {
            return false;
        }

        Movimiento movimiento = encontrado.get();

        if (esTransferencia(movimiento.getTipo())) {
            throw new IllegalArgumentException(
                    "Las transferencias no se pueden eliminar");
        }

        Usuario usuario = usuarioRepository
                .buscarUsuariosParaActualizar(List.of(movimiento.getUsuario().getId()))
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Usuario del movimiento no encontrado"));

        revertirSaldo(usuario, movimiento.getTipo(), movimiento.getMonto());
        usuarioRepository.save(usuario);
        movimientoRepository.delete(movimiento);
        return true;
    }

    public Map<String, Object> obtenerResumen(String email, boolean esAdmin) {
        List<Movimiento> movimientos = obtenerTodos(email, esAdmin);

        BigDecimal depositos = sumarPorTipo(movimientos, "DEPOSITO");
        BigDecimal retiros = sumarPorTipo(movimientos, "RETIRO");
        BigDecimal transferenciasEnviadas =
                sumarPorTipo(movimientos, "TRANSFERENCIA_ENVIADA");
        BigDecimal transferenciasRecibidas =
                sumarPorTipo(movimientos, "TRANSFERENCIA_RECIBIDA");

        BigDecimal saldo = esAdmin
                ? usuarioRepository.sumarSaldos()
                : obtenerUsuarioPorEmail(email).getSaldo();

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("totalDepositado", depositos);
        resumen.put("totalRetirado", retiros);
        resumen.put("totalTransferenciasEnviadas", transferenciasEnviadas);
        resumen.put("totalTransferenciasRecibidas", transferenciasRecibidas);
        resumen.put("totalIngresos", depositos.add(transferenciasRecibidas));
        resumen.put("totalEgresos", retiros.add(transferenciasEnviadas));
        resumen.put(esAdmin ? "saldoTotal" : "saldoActual", saldo);
        return resumen;
    }

    private Usuario obtenerUsuarioPorEmail(String email) {
        return usuarioRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new IllegalArgumentException("Usuario autenticado no encontrado"));
    }

    private String normalizarTipo(String tipo) {
        return tipo == null ? "" : tipo.trim().toUpperCase();
    }

    private void validarMovimientoBasico(String tipo) {
        if (!tipo.equals("DEPOSITO") && !tipo.equals("RETIRO")) {
            throw new IllegalArgumentException(
                    "Solo se permite crear depósitos o retiros directamente");
        }
    }

    private boolean esTransferencia(String tipo) {
        return tipo.equals("TRANSFERENCIA_ENVIADA") ||
                tipo.equals("TRANSFERENCIA_RECIBIDA");
    }

    private void aplicarSaldo(Usuario usuario, String tipo, BigDecimal monto) {
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor que cero");
        }

        if (tipo.equals("DEPOSITO")) {
            usuario.setSaldo(usuario.getSaldo().add(monto));
            return;
        }

        if (usuario.getSaldo().compareTo(monto) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente para realizar el retiro");
        }

        usuario.setSaldo(usuario.getSaldo().subtract(monto));
    }

    private void revertirSaldo(Usuario usuario, String tipo, BigDecimal monto) {
        if (tipo.equals("DEPOSITO")) {
            if (usuario.getSaldo().compareTo(monto) < 0) {
                throw new IllegalArgumentException(
                        "No se puede revertir el depósito porque el saldo es insuficiente");
            }

            usuario.setSaldo(usuario.getSaldo().subtract(monto));
            return;
        }

        usuario.setSaldo(usuario.getSaldo().add(monto));
    }

    private BigDecimal sumarPorTipo(
            List<Movimiento> movimientos, String tipo) {

        return movimientos.stream()
                .filter(movimiento -> movimiento.getTipo().equals(tipo))
                .map(Movimiento::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
