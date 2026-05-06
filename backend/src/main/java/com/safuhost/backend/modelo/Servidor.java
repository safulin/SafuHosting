package com.safuhost.backend.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity // Define que esta clase es una tabla en la base de datos
@Data   // Genera automáticamente los Getters, Setters y otros métodos básicos, esto es de una libreria que he metido en mi proyecto,
//se llama lombok y queria probarla porque me parecia un ahora de tiempo y una manera de dejar el codigo mas limpio, ya que la gran mayoria de codigo
//suelen ser getter y setters
public class Servidor {

    @Id // esto hace que la primarykey de la base de datos sea id, evitando que se creen 2 iguales, sabe que es la variable id, porque coge
    //siempre la variable que se declare debajo

    @GeneratedValue(strategy = GenerationType.IDENTITY)  // esto es lo que hace que sea auto-incremental, simplemente le da a la hibernate
    //la instuccion de que la siguiente variable sea auto-incremental

    private Long id; // Identificador único automático en la base de datos


    //razon del private, a parte de por el diseño, he tenido algun error por que hibernate si es public no sabe muy bien cuando actualizar los datos.

    // --- DATOS BÁSICOS ---
    private String nombre; // Nombre identificativo del servidor
    private String version; // Versión de Minecraft (Ej: 1.21.1)
    private Integer puerto; // Puerto físico en tu PC de sobremesa[25565]
    private String estado;  // Estado: CREADO, INICIANDO, EN_LINEA, APAGADO
    private String idContenedor; // El ID largo que nos devolverá Docker

    // --- CONFIGURACIÓN DEL MOTOR (TYPE) ---
    private String tipo; // Puede ser: VANILLA, FORGE o FABRIC

    // --- OPCIONES DE JUEGO (GAMEPLAY) ---
    private String dificultad; // peaceful, easy, normal, hard
    private String modoJuego;  // survival, creative, adventure
    private boolean pvp;       // true o false para el daño entre jugadores

    // --- IDENTIDAD Y SEGURIDAD ---
    private String urlIcono;      // Enlace directo a una imagen de 64x64
    private String administradores; // Lista de nombres (OPS) separados por comas
    private boolean usarWhitelist; // El interruptor: true (activada) o false (desactivada)
    private String listaBlanca;    // Jugadores permitidos (WHITELIST)
    private boolean modoOnline;    // true (Premium) o false (No-Premium)

    // --- RELACIÓN CON USUARIO PROPIETARIO ---
    // @ManyToOne significa "muchos servidores pueden pertenecer a un solo usuario"
    // FetchType.EAGER carga el usuario automáticamente cada vez que cargamos el servidor (necesario para verificar propiedad)
    // @JoinColumn crea una columna "propietario_id" en la tabla servidor que apunta a usuario.id
    // @JsonIgnoreProperties evita que al serializar a JSON aparezca la contraseña del usuario o referencias circulares
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "propietario_id")
    @JsonIgnoreProperties({"password"})
    private Usuario propietario;
}