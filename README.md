# API de ACIS — Gestión de Órdenes de Trabajo en Terreno

API REST hecha con **Spring Boot (Java + Maven)** para la app Android de ACIS. Guarda los datos reales en **MySQL/MariaDB** (la que ves en **phpMyAdmin**), entrega el **clima real** y guarda las **fotografías** de las OT dentro de la base de datos.

```
App Android  ⇄  API Spring Boot (VS Code, corre en tu PC)  ⇄  MySQL / MariaDB (phpMyAdmin)
                         │
                         ├──►  Open-Meteo (clima real, por internet)
                         └──►  Fotos guardadas en la tabla "fotos" (LONGBLOB)
```

## Qué incluye

| Requisito del profesor | Cómo está resuelto |
|---|---|
| API con Spring Boot, hecha en VS Code, que guarde datos reales | Java 17+, Maven, Spring Data JPA. Las tablas se crean solas en MySQL/MariaDB. |
| Clima real a través de la API | `GET /api/clima…` consulta Open-Meteo y devuelve `DESPEJADO`, `LLUVIA` o `VIENTO_FUERTE` más la alerta para equipos exteriores. |
| Fotos reales guardadas, conectadas con la API | `POST /api/ordenes/{id}/fotos` (multipart). La imagen queda en la base de datos. |
| Login validado con la API | `POST /api/auth/login` entrega un token. Cada perfil solo ve y hace lo suyo. |
| Que funcione con el proyecto de Android Studio | Mismos nombres de estados, prioridades y climas que la app (`EstadoOt`, `Prioridad`, `Clima`, `Conformidad`). |
| Pruebas | 202 pruebas con JUnit 5 (ver más abajo). |
| Documentación de los endpoints | Este archivo y `api.http` (ejecutable desde VS Code). |

> **Esta API es la fuente real de datos de la app Android.** Sin ella la app sigue abriendo en *modo demostración*, pero el login y el listado de OT **no vienen de MySQL**. El proyecto Android está en <https://github.com/Gerald-Duoc/Aplicaciones_Moviles>.

## Requisitos mínimos para que la API funcione

Comprueba estos puntos **antes** de ejecutar `.\mvnw.cmd spring-boot:run`:

| Requisito | Cómo comprobarlo | Error si falta |
|---|---|---|
| **JDK 17 o superior** | `java -version` | `release version 17 not supported` |
| **MySQL/MariaDB encendido en `localhost:3306`** (XAMPP) | Abre `http://localhost/phpmyadmin` | `Communications link failure` o `Connection refused` |
| **Base de datos `acis` creada** | Ejecuta `crear_bd.sql` en phpMyAdmin | `Unknown database 'acis'` |
| **Usuario `root` sin clave** (o ajusta `application.properties`) | Intenta entrar a phpMyAdmin | `Access denied for user 'root'` |
| **Puerto `8080` libre** | La API debe responder `/api/salud` | `Port 8080 was already in use` |
| **Internet en el PC** (solo para el clima real) | `GET /api/clima…` | El clima queda sin datos de Open-Meteo |
| **App Android apuntando a esta API** | Login → **Configurar API (otra PC)** | La app dice *Modo demostración (API no disponible)* |

La app Android debe usar como URL base:

```text
http://IP_DE_LA_PC:8080/api/
```

o, si usas el túnel de VS Code:

```text
https://TU_TUNEL-8080.devtunnels.ms/api/
```

El puerto es **8080** y la ruta siempre termina en **`/api/`**. La URL se cambia desde la propia app, sin recompilar.

---

## 1. Qué necesitas instalar

1. **JDK 17 o superior** (sirve el 21 o el 25). Revisa con `java -version` en una terminal.
   Si no lo tienes: `winget install EclipseAdoptium.Temurin.21.JDK` y abre una terminal nueva.
2. **XAMPP** (o similar) con **MySQL/MariaDB** y **phpMyAdmin** encendidos.
3. **VS Code** con estas extensiones:
   - **Extension Pack for Java**
   - **Spring Boot Extension Pack**
   - **REST Client** (de *Huachao Mao*), para usar `api.http`

No necesitas instalar Maven: el proyecto trae `mvnw` / `mvnw.cmd` (descarga Maven solo la primera vez).

## 2. Crear la base de datos (phpMyAdmin)

1. Enciende **MySQL** en el panel de XAMPP y abre `http://localhost/phpmyadmin`.
2. Pestaña **SQL**, pega el contenido de `crear_bd.sql` y presiona **Continuar**.
   (O pestaña *Bases de datos* → nombre `acis` → cotejamiento `utf8mb4_unicode_ci` → *Crear*.)
3. No crees tablas: la API las crea sola.

## 3. Configurar la conexión

Abre `src/main/resources/application.properties`:

- Si phpMyAdmin dice **Servidor: MariaDB** (lo normal en XAMPP) no cambies nada: usa `jdbc:mariadb://…`.
- Si dice **Servidor: MySQL**, comenta la línea `jdbc:mariadb` y descomenta la `jdbc:mysql`.
- Usuario y clave: por defecto `root` sin clave (XAMPP). Cámbialos si los tuyos son distintos.

También puedes usar variables de entorno `DB_URL`, `DB_USER` y `DB_PASSWORD` sin tocar el archivo.

## 4. Iniciar la API

Abre la carpeta del proyecto en VS Code y, en la terminal (`Ctrl + ñ`):

```
.\mvnw.cmd spring-boot:run
```

(En Mac/Linux: `./mvnw spring-boot:run`. También puedes usar el panel **Spring Boot Dashboard** de VS Code.)

Cuando veas `Started AcisApiApplication`, prueba en el navegador: <http://localhost:8080/api/salud>. Debe responder `{"estado":"ok",...}`.

La primera vez que se inicia con la base vacía, **carga los datos de ejemplo** (los mismos de la app): clientes, locales, equipos, técnicos y 10 OT.

### Usuarios de ejemplo (clave `1234` para todos)

| Perfil | Correo |
|---|---|
| Técnico | `tecnico@acis.cl` (Carlos Muñoz), `jrojas@acis.cl`, `msoto@acis.cl` |
| Planificador | `planificador@acis.cl` |
| Cliente | `cliente@acis.cl` (Supermercado Cordillera, Sucursal Independencia) |

## 5. Probar la API

Abre `api.http` en VS Code (con la extensión **REST Client**) y presiona **Send Request** sobre cada petición, de arriba hacia abajo. Primero inicia sesión con cada perfil: el token queda guardado para las demás.

## 6. Conectar el celular real (Port Forwarding de VS Code)

El *port forwarding* **ya viene integrado en VS Code** (no requiere extensión): se llama *Ports* y usa túneles de desarrollo.

1. Con la API corriendo, abre el panel **Ports** (en la parte de abajo, junto a *Terminal*; o `Ctrl + Shift + P` → *Ports: Focus on Ports View*).
2. **Forward a Port** → escribe `8080`. VS Code puede pedirte iniciar sesión con **GitHub o Microsoft**.
3. En la fila del puerto 8080, clic derecho → **Port Visibility → Public** (así el celular no necesita iniciar sesión).
4. Copia la **Forwarded Address** (empieza con `https://` y termina en `devtunnels.ms`).
5. En el navegador del celular abre `esa-direccion/api/salud`. Si aparece una página de advertencia del túnel, presiona *Continuar*. Si ves `{"estado":"ok"…}`, la app podrá usar esa misma dirección como URL base.

**Ventajas:** funciona aunque el computador y el celular estén en redes distintas (por ejemplo, el Wi-Fi de la institución suele aislar a los dispositivos) y usa **HTTPS**, así Android no pide permisos especiales.
**Ojo:** la dirección cambia si cierras el puerto o reinicias el túnel; necesita internet en el PC y el celular. Cierra el puerto cuando termines de probar, porque queda expuesto a internet mientras esté público.

**Alternativa (misma red Wi-Fi):** usa la IP del PC (`ipconfig` → dirección IPv4), por ejemplo `http://192.168.1.50:8080`. Hay que permitir el puerto 8080 en el Firewall de Windows y la app Android debe permitir tráfico `http` sin cifrar. En el **emulador** de Android Studio, el PC se llama `http://10.0.2.2:8080`.

## 7. Endpoints

Todas las rutas empiezan con `/api`. Salvo las marcadas como públicas, necesitan el encabezado `Authorization: Bearer <token>`.

### Acceso
| Método y ruta | Perfil | Descripción |
|---|---|---|
| `GET /salud` | público | Comprueba que la API está viva. |
| `POST /auth/login` | público | Cuerpo `{correo, clave}`. Devuelve `{token, expiraEn, usuario}`. Error 401 si los datos son incorrectos (HU-01). |
| `POST /auth/logout` | todos | Cierra la sesión. |
| `GET /auth/me` | todos | Datos del usuario con sesión abierta. |

### Catastro
| Método y ruta | Perfil | Descripción |
|---|---|---|
| `GET /clientes` | planificador | Clientes con sus locales y equipos (HU-32). |
| `POST /clientes` | planificador | Nuevo cliente con su SLA en horas (HU-32, HU-36). |
| `POST /clientes/{id}/locales` | planificador | Nuevo local con latitud y longitud (HU-33). |
| `POST /locales/{id}/equipos` | planificador | Nuevo equipo (HU-34). |
| `GET /equipos/{id}/historial` | planificador, técnico | OT cerradas del equipo con su diagnóstico (HU-35). |
| `GET /tecnicos` | planificador | Técnicos y cuántas OT activas tiene cada uno. |
| `POST /tecnicos` | planificador | Nuevo técnico; correo único (HU-02). Clave inicial `1234`. |
| `GET /mi-local` | cliente | Su local y sus equipos, para pedir una mantención. |

### Órdenes de trabajo
| Método y ruta | Perfil | Descripción |
|---|---|---|
| `GET /ordenes?estado=&tecnicoId=&clienteId=&fecha=` | todos | Cada perfil ve lo suyo; el planificador ve todo y puede filtrar (HU-11, HU-12, HU-13, HU-41). |
| `GET /ordenes/{id}` | todos | Detalle (HU-14). |
| `GET /ordenes/{id}/clima` | todos | Clima del local de la OT en su fecha (HU-29, HU-30). |
| `POST /ordenes` | planificador | Crea una OT; con técnico queda *Asignada*, sin técnico *Creada* (HU-06, 07, 08). |
| `PUT /ordenes/{id}/asignacion` | planificador | Asigna o reasigna (HU-08, HU-09). |
| `PUT /ordenes/{id}/fecha` | planificador | Reprograma (HU-10). |
| `POST /ordenes/{id}/aceptar` | técnico | HU-15. |
| `POST /ordenes/{id}/rechazo` | técnico | Motivo obligatorio (HU-16). |
| `POST /ordenes/{id}/check-in` | técnico | Solo si `ubicacionAutorizada` es `true` (HU-05, HU-17). |
| `PUT /ordenes/{id}/registro` | técnico | Diagnóstico, actividades y materiales (HU-18, 19, 22). |
| `POST /ordenes/{id}/imprevisto` | técnico | Motivo obligatorio (HU-23). |
| `POST /ordenes/{id}/check-out` | técnico | Guarda la hora y el tiempo de atención (HU-24). |
| `POST /ordenes/{id}/cierre` | técnico | Cierra la OT si el registro está completo (HU-25). |
| `POST /ordenes/solicitud` | cliente | Solicita una mantención de un equipo de su local (HU-40). |
| `POST /ordenes/{id}/aprobacion` | cliente | Aprueba el trabajo (HU-42). |
| `POST /ordenes/{id}/observacion` | cliente | Rechaza con comentario obligatorio (HU-43). |

### Fotografías
| Método y ruta | Perfil | Descripción |
|---|---|---|
| `POST /ordenes/{id}/fotos` | técnico | `multipart/form-data` con `tipo` (`ANTES` o `DESPUES`) y `archivo` (JPEG, PNG o WEBP). Solo con la OT *En ejecución* (HU-20, HU-21). |
| `GET /ordenes/{id}/fotos` | quien vea la OT | Lista de fotos (sin la imagen). |
| `GET /fotos/{id}` | quien vea la OT | Descarga la imagen. |
| `DELETE /fotos/{id}` | técnico | Elimina una foto mientras la OT está en ejecución. |

### Clima
| Método y ruta | Perfil | Descripción |
|---|---|---|
| `GET /clima?lat=&lon=&fecha=&exterior=` | todos | Pronóstico por coordenadas (HU-29, HU-30). |
| `GET /clima/locales/{id}?fecha=&exterior=` | todos | Pronóstico de un local, para advertir al agendar (HU-31). |

### Errores
Todos los errores tienen el mismo formato: `{"estado":404,"error":"Not Found","mensaje":"La OT no existe.","fecha":"…"}`.
`400` datos inválidos · `401` sin sesión o sesión vencida · `403` perfil sin permiso · `404` no existe · `409` conflicto de estado · `413` foto muy pesada · `415` archivo que no es imagen · `502/503` problema con el servicio del clima.

## 8. Clima real

La app **no** llama al servicio del clima: le pregunta a esta API, y la API consulta **Open-Meteo** (gratis, sin clave, para uso no comercial; su licencia pide dar atribución, por eso cada respuesta trae el campo `fuente`; la app debería mostrarlo).

Reglas (se cambian en `application.properties`):
- **LLUVIA:** precipitación del día de 1 mm o más.
- **VIENTO_FUERTE:** viento máximo de 40 km/h o más, o ráfagas de 60 km/h o más.
- **Alerta:** solo si el equipo está en el **exterior** y el clima no es `DESPEJADO` (misma regla de la app).
- Hay pronóstico desde hoy hasta 15 días después. Las consultas repetidas se guardan 30 minutos en memoria.

## 9. Fotografías

- Se guardan **dentro de la base de datos** (tabla `fotos`, columna `LONGBLOB`), como pidió el profesor.
- Máximo 5 MB por foto (`acis.foto.max-bytes`). Se aceptan JPEG, PNG y WEBP; la API revisa que el contenido sea realmente una imagen.
- **Recomendación para la app:** reducir la foto antes de enviarla (por ejemplo, 1280 px de lado mayor y calidad JPEG 80 %). Pesará unos 200–500 KB y viaja más rápido.
- Si al subir una foto aparece `Packet for query is too large`, sube `max_allowed_packet` de MySQL/MariaDB (ver `crear_bd.sql`).

## 10. Pruebas

```
.\mvnw.cmd test
```

**202 pruebas.** No necesitan MySQL ni internet: la base es H2 en memoria y el clima es una versión falsa.

| Archivo | Qué prueba |
|---|---|
| `servicio/OrdenServiceTest` | Reglas del ciclo de la OT: crear, asignar, reasignar, reprogramar, aceptar, rechazar, check-in/out, registro, imprevisto, cierre, conformidad del cliente y qué ve cada perfil (HU-06 a HU-25, HU-40 a HU-43). |
| `servicio/ClimaServiceTest` | Clasificación del clima, alerta, rango de fechas y memoria de consultas (HU-29 a HU-31). |
| `servicio/FotoServiceTest` | Subida, límites, formatos, verificación del contenido, permisos (HU-20, HU-21). |
| `servicio/AuthServiceTest` | Login, claves cifradas, tokens vigentes y vencidos (HU-01). |
| `servicio/CatastroServiceTest` | Clientes, locales, equipos, técnicos, historial (HU-02, HU-32 a HU-36). |
| `seguridad/AuthInterceptorTest` | Token obligatorio y perfiles permitidos por endpoint (HU-01). |
| `controlador/ControladoresRestTest` | Códigos HTTP y mensajes de error que ve la app (400, 404, 409, 413…). |
| `AcisApiIntegrationTest` | API completa con base en memoria: login, permisos, catastro, clima, fotos reales y el ciclo completo de una OT hasta la aprobación del cliente. |
| `ModeloTest` | Reglas simples del modelo y conversión a DTO. |

**Historias que no se pueden probar con pruebas automáticas:** HU-03 y HU-04 (resguardo de datos personales según la ley: es una revisión de seguridad, no una regla de código), HU-27/28 (la sincronización sin conexión ocurre en la app Android) y HU-44/45 (notificaciones push; hoy el cliente ve el cierre al consultar sus OT). HU-37 a HU-39 (tiempo real, SLA y reportes): la API guarda el tiempo real y el SLA de cada OT; el reporte lo arma la app.

## 11. Estructura del proyecto

```
src/main/java/com/example/acisapi/
├── modelo/        tablas de la base de datos (Usuario, Cliente, Local, Equipo, OrdenTrabajo, FotoOrden…)
├── repositorio/   consultas a la base de datos (Spring Data JPA)
├── dto/           datos que entran y salen por la API (Peticiones, Respuestas)
├── servicio/      reglas de negocio (OrdenService, FotoService, ClimaService, AuthService…)
├── controlador/   endpoints REST
├── seguridad/     token, perfiles permitidos (@Publico, @RequierePerfil)
├── error/         errores con formato uniforme
└── config/        cifrado de claves, interceptor, datos de ejemplo
```

## 12. Problemas frecuentes

| Síntoma | Qué revisar |
|---|---|
| `Communications link failure` o `Connection refused` | MySQL no está encendido en XAMPP, o el puerto no es 3306. |
| `Unknown database 'acis'` | Falta crear la base (paso 2). |
| `Access denied for user 'root'` | La clave de MySQL no es vacía: ponla en `spring.datasource.password`. |
| `No suitable driver` / `Driver … claims to not accept jdbcUrl` | La URL debe empezar con `jdbc:mariadb://` o `jdbc:mysql://` según tu servidor. |
| `Port 8080 was already in use` | Otro programa usa el 8080: ciérralo o cambia `server.port`. |
| `release version 17 not supported` | Tu JDK es anterior al 17: instala uno más nuevo. |
| `Packet for query is too large` al subir una foto | Sube `max_allowed_packet` (ver `crear_bd.sql`) o reduce el tamaño de la foto. |
| 401 al descargar una foto | La descarga también necesita el encabezado `Authorization`. |
| Cambiaste una tabla y falla al iniciar | Borra la base `acis` y créala de nuevo (o deja que `ddl-auto=update` la ajuste). |

## 13. Decisiones tomadas (para comentar con el profesor)

- **Sesión con token propio** (se guarda en la tabla `sesiones`, vence a las 12 horas) en vez de JWT o Spring Security completo. Las claves se guardan cifradas con BCrypt.
- **Cierre de la OT:** igual que la app, exige ubicación autorizada, check-in y check-out, diagnóstico, al menos una actividad, un material y fotos de antes y después (HU-25 pide solo diagnóstico y actividades).
- **Clima:** solo hasta 15 días hacia adelante y para fechas desde hoy.
- **Datos de ejemplo:** las coordenadas de los locales son aproximadas.
