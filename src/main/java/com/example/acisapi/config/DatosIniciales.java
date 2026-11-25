package com.example.acisapi.config;

import com.example.acisapi.modelo.Cliente;
import com.example.acisapi.modelo.Conformidad;
import com.example.acisapi.modelo.Equipo;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.Local;
import com.example.acisapi.modelo.OrdenTrabajo;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Prioridad;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.repositorio.ClienteRepository;
import com.example.acisapi.repositorio.EquipoRepository;
import com.example.acisapi.repositorio.LocalRepository;
import com.example.acisapi.repositorio.OrdenRepository;
import com.example.acisapi.repositorio.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Carga los mismos datos de ejemplo que tiene la app Android (clientes, locales, equipos,
 * técnicos y OT) la primera vez que se inicia la API con la base de datos vacía.
 * Las coordenadas de los locales son aproximadas: sirven para consultar el clima.
 * Todos los usuarios de ejemplo usan la clave "1234".
 */
@Component
public class DatosIniciales implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DatosIniciales.class);

    private final boolean activo;
    private final UsuarioRepository usuarios;
    private final ClienteRepository clientes;
    private final LocalRepository locales;
    private final EquipoRepository equipos;
    private final OrdenRepository ordenes;
    private final PasswordEncoder codificador;

    public DatosIniciales(@Value("${acis.datos-iniciales:true}") boolean activo,
                          UsuarioRepository usuarios,
                          ClienteRepository clientes,
                          LocalRepository locales,
                          EquipoRepository equipos,
                          OrdenRepository ordenes,
                          PasswordEncoder codificador) {
        this.activo = activo;
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.locales = locales;
        this.equipos = equipos;
        this.ordenes = ordenes;
        this.codificador = codificador;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!activo || usuarios.count() > 0) {
            return;
        }
        LOG.info("Base de datos vacía: cargando datos de ejemplo de ACIS...");
        LocalDate hoy = LocalDate.now();

        // --- Clientes, locales y equipos ---
        Cliente cordillera = clientes.save(new Cliente("Supermercado Cordillera", "Ana Pérez · ana.perez@cordillera.cl", 4));
        Cliente alerces = clientes.save(new Cliente("Centro Comercial Los Alerces", "Marcelo Díaz · mdiaz@losalerces.cl", 6));
        Cliente moderna = clientes.save(new Cliente("Retail Moderna", "Paula Fuentes · pfuentes@retailmoderna.cl", 3));

        Local independencia = locales.save(new Local(cordillera, "Sucursal Independencia",
                "Av. Independencia 1250, Independencia", -33.4150, -70.6580));
        Local maipu = locales.save(new Local(cordillera, "Sucursal Maipú",
                "Av. Pajaritos 3400, Maipú", -33.5100, -70.7600));
        Local torre = locales.save(new Local(alerces, "Torre Oriente",
                "Av. Apoquindo 4500, Las Condes", -33.4140, -70.5800));
        Local costanera = locales.save(new Local(moderna, "Tienda Costanera",
                "Av. Andrés Bello 2425, Providencia", -33.4180, -70.6050));

        Equipo rooftop = equipos.save(new Equipo(independencia, "Condensadora rooftop 1", "Condensadora", "FrioMax", "CX-450",
                true, "Azotea", hoy.minusDays(100)));
        Equipo camara1 = equipos.save(new Equipo(independencia, "Cámara frigorífica 1", "Cámara frigorífica", "PolarTec", "CF-220",
                false, "Bodega", hoy.minusDays(55)));
        Equipo patio = equipos.save(new Equipo(maipu, "Condensadora patio 1", "Condensadora", "FrioMax", "CX-300",
                true, "Patio exterior", hoy.minusDays(130)));
        equipos.save(new Equipo(maipu, "Cámara frigorífica 2", "Cámara frigorífica", "PolarTec", "CF-180",
                false, "Bodega", hoy.minusDays(80)));
        Equipo chiller = equipos.save(new Equipo(torre, "Chiller central", "Chiller", "ClimaTec", "CH-900",
                true, "Patio técnico", hoy.minusDays(90)));
        Equipo split = equipos.save(new Equipo(torre, "Split sala técnica", "Split", "ClimaTec", "SP-120",
                false, "Sala técnica", hoy.minusDays(40)));
        Equipo climaTienda = equipos.save(new Equipo(costanera, "Climatización tienda", "Rooftop", "ClimaTec", "RT-600",
                true, "Azotea", hoy.minusDays(75)));
        Equipo vitrina = equipos.save(new Equipo(costanera, "Vitrina refrigerada 3", "Vitrina", "PolarTec", "VR-90",
                false, "Salón de ventas", hoy.minusDays(30)));

        // --- Usuarios (clave "1234") ---
        Usuario carlos = usuario("Carlos Muñoz", "tecnico@acis.cl", Perfil.TECNICO);
        Usuario javiera = usuario("Javiera Rojas", "jrojas@acis.cl", Perfil.TECNICO);
        Usuario matias = usuario("Matías Soto", "msoto@acis.cl", Perfil.TECNICO);
        usuario("Planificador ACIS", "planificador@acis.cl", Perfil.PLANIFICADOR);
        Usuario anaCliente = usuario("Ana Pérez", "cliente@acis.cl", Perfil.CLIENTE);
        anaCliente.asociarALocal(independencia);
        usuarios.save(anaCliente);

        // --- Órdenes de trabajo ---
        // En curso
        ordenes.save(orden(1041, rooftop, "Correctiva", Prioridad.ALTA, EstadoOt.ASIGNADA, carlos, hoy,
                "La unidad no enfría y el compresor hace un ruido anormal."));
        ordenes.save(orden(1042, vitrina, "Preventiva", Prioridad.MEDIA, EstadoOt.ACEPTADA, carlos, hoy,
                "Mantención preventiva trimestral: limpieza y revisión de temperatura."));
        ordenes.save(orden(1043, chiller, "Preventiva", Prioridad.MEDIA, EstadoOt.ASIGNADA, carlos, hoy,
                "Revisión general del chiller y toma de presiones."));

        OrdenTrabajo enEjecucion = orden(1040, climaTienda, "Correctiva", Prioridad.ALTA, EstadoOt.EN_EJECUCION, javiera, hoy,
                "La tienda no alcanza la temperatura de confort.");
        enEjecucion.setUbicacionAutorizada(true);
        enEjecucion.setCheckIn(LocalDateTime.now().minusMinutes(40));
        ordenes.save(enEjecucion);

        // Sin técnico
        ordenes.save(orden(1044, patio, "Correctiva", Prioridad.ALTA, EstadoOt.CREADA, null, hoy.plusDays(2),
                "Ruido anormal en el ventilador de la condensadora."));
        OrdenTrabajo pendiente = orden(1045, split, "Preventiva", Prioridad.BAJA, EstadoOt.PENDIENTE_ASIGNACION, null,
                hoy.plusDays(4), "Mantención preventiva trimestral del split.");
        pendiente.setObservacion("Rechazada por Matías Soto: conflicto de agenda.");
        ordenes.save(pendiente);

        // Cerradas (para el historial, el SLA y los reportes)
        ordenes.save(cerrada(1038, camara1, "Correctiva", Prioridad.ALTA, javiera, hoy.minusDays(3), 150, Conformidad.SIN_REVISAR,
                "Falla en el termostato de la cámara; la temperatura estaba sobre el rango.",
                List.of("Reemplazo del termostato digital", "Calibración de la temperatura a 2 °C",
                        "Prueba de funcionamiento por 30 minutos"),
                List.of("Termostato digital × 1", "Cinta aislante × 1")));
        ordenes.save(cerrada(1030, rooftop, "Preventiva", Prioridad.MEDIA, matias, hoy.minusDays(14), 95, Conformidad.APROBADA,
                "Equipo en buen estado; serpentín con suciedad leve.",
                List.of("Limpieza de serpentín", "Revisión de presiones"),
                List.of("Desengrasante × 1 L")));
        ordenes.save(cerrada(1036, chiller, "Correctiva", Prioridad.ALTA, matias, hoy.minusDays(4), 410, Conformidad.APROBADA,
                "Válvula de expansión obstruida; alta presión en el circuito.",
                List.of("Limpieza de la válvula de expansión", "Recarga de refrigerante", "Prueba de carga"),
                List.of("Refrigerante R-410A × 3 kg")));
        ordenes.save(cerrada(1035, climaTienda, "Preventiva", Prioridad.MEDIA, javiera, hoy.minusDays(5), 130, Conformidad.APROBADA,
                "Sin fallas; filtros con desgaste normal.",
                List.of("Cambio de filtros", "Revisión eléctrica"),
                List.of("Filtro G4 × 4")));

        LOG.info("Datos de ejemplo cargados. Usuarios: tecnico@acis.cl, planificador@acis.cl, cliente@acis.cl (clave 1234).");
    }

    private Usuario usuario(String nombre, String correo, Perfil perfil) {
        return usuarios.save(new Usuario(nombre, correo, codificador.encode("1234"), perfil));
    }

    private OrdenTrabajo orden(int numero, Equipo equipo, String tipo, Prioridad prioridad, EstadoOt estado,
                               Usuario tecnico, LocalDate fecha, String descripcion) {
        OrdenTrabajo o = new OrdenTrabajo();
        o.setNumero(numero);
        o.setEquipo(equipo);
        o.setTipo(tipo);
        o.setPrioridad(prioridad);
        o.setEstado(estado);
        o.setTecnico(tecnico);
        o.setFecha(fecha);
        o.setDescripcion(descripcion);
        o.setSlaHoras(equipo.getLocal().getCliente().getSlaHoras());
        o.setCreadaEn(LocalDateTime.now().minusDays(1));
        return o;
    }

    private OrdenTrabajo cerrada(int numero, Equipo equipo, String tipo, Prioridad prioridad, Usuario tecnico,
                                 LocalDate fecha, int minutos, Conformidad conformidad, String diagnostico,
                                 List<String> actividades, List<String> materiales) {
        OrdenTrabajo o = orden(numero, equipo, tipo, prioridad, EstadoOt.CERRADA, tecnico, fecha, "Visita ya realizada.");
        LocalDateTime entrada = fecha.atTime(9, 0);
        o.setUbicacionAutorizada(true);
        o.setCheckIn(entrada);
        o.setCheckOut(entrada.plusMinutes(minutos));
        o.setTiempoRealMin(minutos);
        o.setDiagnostico(diagnostico);
        o.getActividades().addAll(actividades);
        o.getMateriales().addAll(materiales);
        o.setConformidad(conformidad);
        if (conformidad == Conformidad.APROBADA) {
            o.setFechaConformidad(entrada.plusMinutes(minutos + 60));
        }
        return o;
    }
}
