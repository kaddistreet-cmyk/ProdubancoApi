package com.itsqmet.produbanco;

import com.itsqmet.produbanco.model.Movimiento;
import com.itsqmet.produbanco.model.Usuario;
import com.itsqmet.produbanco.repository.MovimientoRepository;
import com.itsqmet.produbanco.repository.UsuarioRepository;
import com.itsqmet.produbanco.service.MovimientoService;
import com.itsqmet.produbanco.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MovimientoServiceTests {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private MovimientoService movimientoService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private MovimientoRepository movimientoRepository;

    @BeforeEach
    void limpiarDatos() {
        movimientoRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    @Test
    void debeDepositarYTransferirConElMismoComprobante() {
        Usuario origen = crearUsuario(
                "1712345675",
                "Usuario Origen",
                "origen@produbanco.com"
        );

        Usuario destino = crearUsuario(
                "1723456784",
                "Usuario Destino",
                "destino@produbanco.com"
        );

        Movimiento deposito = new Movimiento();
        deposito.setTipo("DEPOSITO");
        deposito.setMonto(new BigDecimal("100.00"));
        deposito.setDescripcion("Depósito inicial");

        movimientoService.crearMovimiento(
                deposito, origen.getEmail(), false);

        List<Movimiento> transferencia = movimientoService.transferir(
                origen.getEmail(),
                destino.getCedula(),
                new BigDecimal("30.00"),
                "Transferencia de prueba"
        );

        Usuario origenActualizado = usuarioRepository.findById(origen.getId()).orElseThrow();
        Usuario destinoActualizado = usuarioRepository.findById(destino.getId()).orElseThrow();

        assertThat(origenActualizado.getSaldo()).isEqualByComparingTo("70.00");
        assertThat(destinoActualizado.getSaldo()).isEqualByComparingTo("30.00");
        assertThat(transferencia).hasSize(2);
        assertThat(transferencia.get(0).getComprobante())
                .isEqualTo(transferencia.get(1).getComprobante());
        assertThat(transferencia)
                .extracting(Movimiento::getTipo)
                .containsExactlyInAnyOrder(
                        "TRANSFERENCIA_ENVIADA",
                        "TRANSFERENCIA_RECIBIDA"
                );
    }

    @Test
    void debeActualizarPerfilSinCambiarRolNiSaldo() {
        Usuario usuario = crearUsuario(
                "1734567893",
                "Cliente Original",
                "cliente@produbanco.com"
        );

        Movimiento deposito = new Movimiento();
        deposito.setTipo("DEPOSITO");
        deposito.setMonto(new BigDecimal("80.00"));
        deposito.setDescripcion("Depósito para actualizar perfil");

        movimientoService.crearMovimiento(
                deposito, usuario.getEmail(), false);

        Usuario actualizado = usuarioService.actualizarPerfil(
                usuario.getEmail(),
                Map.of(
                        "cedula", "1745678902",
                        "nombre", "Cliente Actualizado",
                        "email", "actualizado@produbanco.com",
                        "contrasena", ""
                )
        );

        assertThat(actualizado.getCedula()).isEqualTo("1745678902");
        assertThat(actualizado.getNombre()).isEqualTo("Cliente Actualizado");
        assertThat(actualizado.getEmail()).isEqualTo("actualizado@produbanco.com");
        assertThat(actualizado.getRol()).isEqualTo("CLIENTE");
        assertThat(actualizado.getSaldo()).isEqualByComparingTo("80.00");
    }

    private Usuario crearUsuario(
            String cedula,
            String nombre,
            String email) {

        Usuario usuario = new Usuario();
        usuario.setCedula(cedula);
        usuario.setNombre(nombre);
        usuario.setEmail(email);
        usuario.setContrasena("ClaveSegura2026");
        usuario.setRol("CLIENTE");
        usuario.setSaldo(BigDecimal.ZERO);

        usuarioService.registrar(usuario);
        return usuarioService.buscarPorEmail(email).orElseThrow();
    }
}
