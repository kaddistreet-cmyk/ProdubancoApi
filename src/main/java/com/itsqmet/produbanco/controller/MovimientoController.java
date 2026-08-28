package com.itsqmet.produbanco.controller;

import com.itsqmet.produbanco.model.Movimiento;
import com.itsqmet.produbanco.service.MovimientoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    @Autowired
    private MovimientoService movimientoService;

    @GetMapping
    public ResponseEntity<List<Movimiento>> obtenerTodos(
            Authentication authentication) {

        return ResponseEntity.ok(movimientoService.obtenerTodos(
                authentication.getName(), esAdmin(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(
            @PathVariable UUID id,
            Authentication authentication) {

        return movimientoService.buscarPorId(
                        id, authentication.getName(), esAdmin(authentication))
                .map(movimiento -> ResponseEntity.ok((Object) movimiento))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Movimiento con id " + id + " no encontrado")));
    }

    @PostMapping
    public ResponseEntity<?> crear(
            @Valid @RequestBody Movimiento movimiento,
            BindingResult result,
            Authentication authentication) {

        if (result.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(obtenerErrores(result));
        }

        try {
            Movimiento nuevo = movimientoService.crearMovimiento(
                    movimiento,
                    authentication.getName(),
                    esAdmin(authentication));

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "mensaje", "Movimiento creado correctamente",
                    "movimiento", nuevo
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/transferir")
    public ResponseEntity<?> transferir(
            @RequestBody Map<String, Object> datos,
            Authentication authentication) {

        try {
            Object cedulaRecibida = datos.get("cedulaDestino");
            Object montoRecibido = datos.get("monto");

            if (cedulaRecibida == null || montoRecibido == null) {
                throw new IllegalArgumentException(
                        "Debes ingresar la cédula de destino y el monto");
            }

            String cedulaDestino = cedulaRecibida.toString();
            BigDecimal monto = new BigDecimal(montoRecibido.toString());
            String descripcion = datos.get("descripcion") == null
                    ? null
                    : datos.get("descripcion").toString();

            List<Movimiento> movimientos = movimientoService.transferir(
                    authentication.getName(), cedulaDestino, monto, descripcion);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "mensaje", "Transferencia realizada correctamente",
                    "comprobante", movimientos.get(0).getComprobante(),
                    "movimientos", movimientos
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(
            @PathVariable UUID id,
            @Valid @RequestBody Movimiento movimiento,
            BindingResult result,
            Authentication authentication) {

        if (result.hasErrors()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(obtenerErrores(result));
        }

        try {
            return movimientoService.actualizar(
                            id,
                            movimiento,
                            authentication.getName(),
                            esAdmin(authentication)
                    )
                    .map(actualizado -> ResponseEntity.ok((Object) Map.of(
                            "mensaje", "Movimiento actualizado correctamente",
                            "movimiento", actualizado
                    )))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "Movimiento con id " + id + " no encontrado")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @PathVariable UUID id,
            Authentication authentication) {

        try {
            if (movimientoService.eliminar(
                    id, authentication.getName(), esAdmin(authentication))) {

                return ResponseEntity.ok(
                        Map.of("mensaje", "Movimiento eliminado correctamente"));
            }

            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Movimiento con id " + id + " no encontrado"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/resumen")
    public ResponseEntity<Map<String, Object>> obtenerResumen(
            Authentication authentication) {

        return ResponseEntity.ok(movimientoService.obtenerResumen(
                authentication.getName(), esAdmin(authentication)));
    }

    private boolean esAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private Map<String, String> obtenerErrores(BindingResult result) {
        Map<String, String> errores = new HashMap<>();
        result.getFieldErrors().forEach(error ->
                errores.put(error.getField(), error.getDefaultMessage())
        );
        return errores;
    }
}
