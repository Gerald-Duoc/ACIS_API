package com.example.acisapi.repositorio;

import com.example.acisapi.dto.FotoResumen;
import com.example.acisapi.modelo.FotoOrden;
import com.example.acisapi.modelo.TipoFoto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FotoRepository extends JpaRepository<FotoOrden, Long> {

    /** Lista las fotos de una OT sin cargar los bytes de las imágenes. */
    @Query("select new com.example.acisapi.dto.FotoResumen(f.id, f.tipo, f.nombreArchivo, f.contentType, f.tamano, f.creadaEn) "
            + "from FotoOrden f where f.orden.id = :ordenId order by f.id")
    List<FotoResumen> resumenes(@Param("ordenId") Long ordenId);

    long countByOrdenIdAndTipo(Long ordenId, TipoFoto tipo);
}
