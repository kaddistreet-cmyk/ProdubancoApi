package com.itsqmet.produbanco.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "movimientos")
@Data
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "El tipo de movimiento no puede estar vacio")
    @Pattern(
            regexp = "DEPOSITO|RETIRO|TRANSFERENCIA_ENVIADA|TRANSFERENCIA_RECIBIDA",
            message = "El tipo de movimiento no es válido"
    )
    @Column(nullable = false, length = 35)
    private String tipo;

    @NotNull(message = "El campo monto no puede estar vacio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    @Size(max = 255, message = "La descripción supera los 255 caracteres")
    @Column(length = 255)
    private String descripcion;

    @Column(nullable = false)
    private Long comprobante;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonIgnoreProperties("movimientos")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Usuario usuario;


}
