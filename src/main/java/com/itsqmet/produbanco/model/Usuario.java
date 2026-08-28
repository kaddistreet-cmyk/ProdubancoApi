package com.itsqmet.produbanco.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "usuarios")
@Data

public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank(message = "El campo cédula no puede estar vacio")
    @Pattern(regexp = "\\d{10}", message = "La cédula debe tener 10 dígitos")
    @Column(unique = true, nullable = false, length = 10)
    private String cedula;

    @NotBlank(message = "El campo nombre no puede estar vacio")
    @Column(nullable = false, length = 100)
    private String nombre;

    @NotBlank(message = "El campo email no puede estar vacio")
    @Email(message = "El email no tiene un formato válido")
    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @NotBlank(message = "El campo contraseña no puede estar vacio")
    @Size(min = 8, message = "La contraseña debe tener mínimo 8 caracteres")
    @Column(name = "password", nullable = false, length = 255)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String contrasena;

    @NotBlank(message = "El campo rol no puede estar vacio")
    @Pattern(regexp = "ADMIN|CLIENTE", message = "El rol debe ser ADMIN o CLIENTE")
    @Column(nullable = false, length = 20)
    private String rol;

    @NotNull(message = "El campo saldo no puede estar vacio")
    @DecimalMin(value = "0.00", message = "El saldo no puede ser negativo")
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal saldo = BigDecimal.ZERO;

    @OneToMany(mappedBy = "usuario")
    @JsonIgnoreProperties("usuario")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Movimiento> movimientos;

}
