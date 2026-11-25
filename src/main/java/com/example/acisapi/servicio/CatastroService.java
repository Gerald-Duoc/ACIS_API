package com.example.acisapi.servicio;

import com.example.acisapi.dto.Mapeos;
import com.example.acisapi.dto.Peticiones.ClienteRequest;
import com.example.acisapi.dto.Peticiones.EquipoRequest;
import com.example.acisapi.dto.Peticiones.LocalRequest;
import com.example.acisapi.dto.Peticiones.TecnicoRequest;
import com.example.acisapi.dto.Respuestas.ClienteDto;
import com.example.acisapi.dto.Respuestas.EquipoDto;
import com.example.acisapi.dto.Respuestas.HistorialItem;
import com.example.acisapi.dto.Respuestas.LocalDto;
import com.example.acisapi.dto.Respuestas.TecnicoDto;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Cliente;
import com.example.acisapi.modelo.Equipo;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.Local;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.repositorio.ClienteRepository;
import com.example.acisapi.repositorio.EquipoRepository;
import com.example.acisapi.repositorio.LocalRepository;
import com.example.acisapi.repositorio.OrdenRepository;
import com.example.acisapi.repositorio.UsuarioRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catastro: clientes, locales, equipos y técnicos (HU-02, HU-32, HU-33, HU-34, HU-35, HU-36). */
@Service
public class CatastroService {

    /** Clave inicial de un técnico nuevo si no se indica otra. */
    public static final String CLAVE_INICIAL = "1234";

    private final ClienteRepository clientes;
    private final LocalRepository locales;
    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final OrdenRepository ordenes;
    private final PasswordEncoder codificador;

    public CatastroService(ClienteRepository clientes,
                           LocalRepository locales,
                           EquipoRepository equipos,
                           UsuarioRepository usuarios,
                           OrdenRepository ordenes,
                           PasswordEncoder codificador) {
        this.clientes = clientes;
        this.locales = locales;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.ordenes = ordenes;
        this.codificador = codificador;
    }

    // --- Clientes (HU-32, HU-36) ---

    @Transactional(readOnly = true)
    public List<ClienteDto> listarClientes() {
        return clientes.findAll().stream().map(this::clienteDto).toList();
    }

    @Transactional
    public ClienteDto crearCliente(ClienteRequest r) {
        if (clientes.existsByNombreIgnoreCase(r.nombre().trim())) {
            throw ApiException.conflicto("Ya existe un cliente con esa razón social.");
        }
        Cliente cliente = clientes.save(new Cliente(r.nombre().trim(), r.contacto().trim(), r.slaHoras()));
        return Mapeos.cliente(cliente, List.of());
    }

    // --- Locales (HU-33) ---

    @Transactional
    public LocalDto crearLocal(Long clienteId, LocalRequest r) {
        Cliente cliente = clientes.findById(clienteId)
                .orElseThrow(() -> ApiException.noEncontrado("El cliente no existe."));
        validarCoordenadas(r.latitud(), r.longitud());
        Local local = locales.save(new Local(cliente, r.nombre().trim(), r.direccion().trim(), r.latitud(), r.longitud()));
        return Mapeos.local(local, List.of());
    }

    @Transactional(readOnly = true)
    public LocalDto obtenerLocal(Long localId) {
        Local local = locales.findById(localId)
                .orElseThrow(() -> ApiException.noEncontrado("El local no existe."));
        return localDto(local);
    }

    /** Local del usuario con perfil CLIENTE (para elegir el equipo al solicitar una mantención). */
    @Transactional(readOnly = true)
    public LocalDto miLocal(Usuario usuario) {
        if (usuario.getLocal() == null) {
            throw ApiException.noEncontrado("Tu usuario no está asociado a un local.");
        }
        return localDto(usuario.getLocal());
    }

    // --- Equipos (HU-34) ---

    @Transactional
    public EquipoDto crearEquipo(Long localId, EquipoRequest r) {
        Local local = locales.findById(localId)
                .orElseThrow(() -> ApiException.noEncontrado("El local no existe."));
        String ubicacion = r.ubicacion() == null || r.ubicacion().isBlank()
                ? (r.exterior() ? "Exterior" : "Interior")
                : r.ubicacion().trim();
        Equipo equipo = equipos.save(new Equipo(local, r.nombre().trim(), r.tipo().trim(), r.marca().trim(),
                r.modelo().trim(), r.exterior(), ubicacion, null));
        return Mapeos.equipo(equipo);
    }

    /** HU-35: intervenciones anteriores (OT cerradas) de un equipo, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<HistorialItem> historialEquipo(Long equipoId) {
        if (!equipos.existsById(equipoId)) {
            throw ApiException.noEncontrado("El equipo no existe.");
        }
        return ordenes.findByEquipoIdAndEstadoOrderByFechaDesc(equipoId, EstadoOt.CERRADA).stream()
                .map(o -> new HistorialItem(o.getCodigo(), o.getFecha(), o.getTipo(), o.getDiagnostico()))
                .toList();
    }

    // --- Técnicos (HU-02) ---

    @Transactional(readOnly = true)
    public List<TecnicoDto> listarTecnicos() {
        return usuarios.findByPerfilOrderByNombre(Perfil.TECNICO).stream()
                .map(t -> new TecnicoDto(t.getId(), t.getNombre(), t.getCorreo(),
                        ordenes.countByTecnicoIdAndEstadoNot(t.getId(), EstadoOt.CERRADA)))
                .toList();
    }

    @Transactional
    public TecnicoDto crearTecnico(TecnicoRequest r) {
        if (usuarios.existsByCorreoIgnoreCase(r.correo().trim())) {
            throw ApiException.conflicto("Ya existe un usuario con ese correo.");
        }
        String clave = r.clave() == null || r.clave().isBlank() ? CLAVE_INICIAL : r.clave();
        Usuario tecnico = usuarios.save(new Usuario(r.nombre().trim(), r.correo(), codificador.encode(clave), Perfil.TECNICO));
        return new TecnicoDto(tecnico.getId(), tecnico.getNombre(), tecnico.getCorreo(), 0);
    }

    // --- Apoyo ---

    private ClienteDto clienteDto(Cliente cliente) {
        List<LocalDto> locs = locales.findByClienteIdOrderById(cliente.getId()).stream().map(this::localDto).toList();
        return Mapeos.cliente(cliente, locs);
    }

    private LocalDto localDto(Local local) {
        return Mapeos.local(local, equipos.findByLocalIdOrderById(local.getId()));
    }

    private static void validarCoordenadas(double latitud, double longitud) {
        if (latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
            throw ApiException.invalido("Las coordenadas no son válidas (latitud entre -90 y 90, longitud entre -180 y 180).");
        }
    }
}
