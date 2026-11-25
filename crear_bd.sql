-- ============================================================================
-- Base de datos de la API de ACIS
-- Pégalo en la pestaña "SQL" de phpMyAdmin y presiona "Continuar".
-- (O crea la base a mano: pestaña "Bases de datos" > nombre "acis" > cotejamiento
--  utf8mb4_unicode_ci.) Las tablas las crea la API sola al iniciar.
-- ============================================================================
CREATE DATABASE IF NOT EXISTS acis
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- OPCIONAL: un usuario propio en vez de "root". Si lo creas, cambia
-- spring.datasource.username y spring.datasource.password en application.properties.
-- CREATE USER IF NOT EXISTS 'acis'@'localhost' IDENTIFIED BY 'acis1234';
-- GRANT ALL PRIVILEGES ON acis.* TO 'acis'@'localhost';
-- FLUSH PRIVILEGES;

-- OPCIONAL: si al subir fotos aparece "Packet for query is too large",
-- sube el límite de paquete (la foto viaja dentro de la base de datos):
-- SET GLOBAL max_allowed_packet = 16777216;   -- 16 MB (se pierde al reiniciar el servidor)
