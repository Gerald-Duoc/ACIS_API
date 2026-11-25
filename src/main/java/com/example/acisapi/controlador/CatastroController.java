package com.example.acisapi.controlador;

import static com.example.acisapi.seguridad.AuthInterceptor.ATRIBUTO_USUARIO;

import com.example.acisapi.dto.Peticiones.ClienteRequest;
import com.example.acisapi.dto.Peticiones.EquipoRequest;
import com.example.acisapi.dto.Peticiones.LocalRequest;
import com.example.acisapi.dto.Peticiones.TecnicoRequest;
import com.example.acisapi.dto.Respuestas.ClienteDto;
import com.example.acisapi.dto.Respuestas.EquipoDto;
import com.example.acisapi.dto.Respuestas.HistorialItem;
import com.example.acisapi.dto.Respuestas.LocalDto;
import com.example.acisapi.dto.Respuestas.TecnicoDto;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.seguridad.RequierePerfil;
import com.example.acisapi.servicio.CatastroService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Catastro de clientes, locales, equipos y técnicos. */
@RestController
@RequestMapping("/api")
public class CatastroController {

    private final CatastroService catastro;

    public CatastroController(CatastroService catastro) {
        this.catastro = catastro;
    }

    // --- Clientes (HU-32, HU-36) ---

    @GetMapping("/clientes")
    @RequierePerfil(Perfil.PLANIFICADOR)
    public List<ClienteDto> clientes() {
        return catastro.listarClientes();
    }

    @PostMapping("/clientes")
    @RequierePerfil(Perfil.PLANIFICADOR)
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteDto crearCliente(@Valid @RequestBody ClienteRequest peticion) {
        return catastro.crearCliente(peticion);
    }

    // --- Locales (HU-33) ---

    @PostMapping("/clientes/{clienteId}/locales")
    @RequierePerfil(Perfil.PLANIFICADOR)
    @ResponseStatus(HttpStatus.CREATED)
    public LocalDto crearLocal(@PathVariable("clienteId") Long clienteId, @Valid @RequestBody LocalRequest peticion) {
        return catastro.crearLocal(clienteId, peticion);
    }

    @GetMapping("/locales/{localId}")
    @RequierePerfil(Perfil.PLANIFICADOR)
    public LocalDto local(@PathVariable("localId") Long localId) {
        return catastro.obtenerLocal(localId);
    }

    /** El cliente ve su propio local y sus equipos para pedir una mantención. */
    @GetMapping("/mi-local")
    @RequierePerfil(Perfil.CLIENTE)
    public LocalDto miLocal(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario) {
        return catastro.miLocal(usuario);
    }

    // --- Equipos (HU-34, HU-35) ---

    @PostMapping("/locales/{localId}/equipos")
    @RequierePerfil(Perfil.PLANIFICADOR)
    @ResponseStatus(HttpStatus.CREATED)
    public EquipoDto crearEquipo(@PathVariable("localId") Long localId, @Valid @RequestBody EquipoRequest peticion) {
        return catastro.crearEquipo(localId, peticion);
    }

    @GetMapping("/equipos/{equipoId}/historial")
    @RequierePerfil({Perfil.PLANIFICADOR, Perfil.TECNICO})
    public List<HistorialItem> historial(@PathVariable("equipoId") Long equipoId) {
        return catastro.historialEquipo(equipoId);
    }

    // --- Técnicos (HU-02) ---

    @GetMapping("/tecnicos")
    @RequierePerfil(Perfil.PLANIFICADOR)
    public List<TecnicoDto> tecnicos() {
        return catastro.listarTecnicos();
    }

    @PostMapping("/tecnicos")
    @RequierePerfil(Perfil.PLANIFICADOR)
    @ResponseStatus(HttpStatus.CREATED)
    public TecnicoDto crearTecnico(@Valid @RequestBody TecnicoRequest peticion) {
        return catastro.crearTecnico(peticion);
    }
}
