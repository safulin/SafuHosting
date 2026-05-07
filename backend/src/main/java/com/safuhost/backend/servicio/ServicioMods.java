package com.safuhost.backend.servicio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.repositorio.RepositorioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// Servicio que gestiona los mods de los servidores. Habla con la API publica de Modrinth (la "tienda" de mods de MC)
// para buscar, verificar compatibilidad e instalar.
//
// Como funciona la instalacion: los mods son archivos .jar que se colocan en la carpeta C:/mc-servers/{nombre}/mods/
// del host. Esa carpeta esta montada como volumen en /data/mods dentro del contenedor de Docker, asique cuando
// itzg/minecraft-server arranca, ve los mods y los carga.
//
// Tambien sabemos extraer modpacks (.mrpack), que son zips con un manifest que lista mods + configs y los descarga
// todos a sus rutas correctas.
//
// Lo llaman ServicioServidor (delega los metodos del controlador aqui) y tambien directamente desde
// ServicioServidor.crearServidor() para el auto-install de Fabric API + mods iniciales antes del primer arranque.

@Service
public class ServicioMods {

    // ID del proyecto Fabric API en Modrinth. Lo necesitan casi todos los mods de Fabric
    // así que lo instalamos automáticamente al crear cualquier servidor FABRIC
    private static final String FABRIC_API_ID = "P7dR8mSH";

    private static final String CARPETA_BASE = "C:/mc-servers/";

    @Autowired
    private RepositorioServidor repositorio;

    @Autowired
    private ServicioUsuario servicioUsuario;

    // ---------------------------------------------------------------
    // ZONA: API PÚBLICA — los métodos que llama el ControladorServidor
    // ---------------------------------------------------------------

    public List<String> listarMods(Long id) {
        Servidor servidor = obtenerYVerificar(id);
        File carpetaMods = new File(CARPETA_BASE + servidor.getNombre() + "/mods");

        if (!carpetaMods.exists() || !carpetaMods.isDirectory()) {
            return new ArrayList<>();
        }

        File[] archivos = carpetaMods.listFiles((dir, nombre) -> nombre.endsWith(".jar"));
        if (archivos == null) return new ArrayList<>();

        List<String> nombres = new ArrayList<>();
        for (File archivo : archivos) {
            nombres.add(archivo.getName());
        }
        return nombres;
    }

    public Object buscarModsModrinth(String query) {
        // Buscamos en Modrinth con UriComponentsBuilder para que codifique bien los espacios y demás
        String url = UriComponentsBuilder.fromUriString("https://api.modrinth.com/v2/search")
                .queryParam("query", query)
                .queryParam("limit", 20)
                .toUriString();
        return new RestTemplate().getForObject(url, Object.class);
    }

    // Verifica si un mod tiene versión compatible y devuelve sus dependencias requeridas
    public Map<String, Object> verificarCompatibilidadMod(String modId, String tipo, String version) {
        if (!esLoaderValido(tipo)) {
            return Map.of("compatible", false, "motivo", "Solo FORGE y FABRIC soportan mods");
        }

        try {
            List<Map<String, Object>> versiones = consultarVersionesModrinth(modId, tipo.toLowerCase(), version);
            if (versiones.isEmpty()) {
                return Map.of("compatible", false,
                        "motivo", "No hay versión compatible con " + tipo.toLowerCase() + " " + version);
            }

            Map<String, Object> primeraVersion = versiones.get(0);
            Map<String, Object> archivo = elegirArchivoInstalable(primeraVersion);

            if (archivo == null) {
                return Map.of("compatible", false,
                        "motivo", "Esta versión no tiene un archivo .jar o .mrpack instalable");
            }

            String nombreArchivo = (String) archivo.get("filename");
            boolean esModpack = nombreArchivo.endsWith(".mrpack");
            boolean esJar = nombreArchivo.endsWith(".jar");

            // Las dependencias declaradas en Modrinth solo aplican a mods individuales.
            // Un modpack ya trae su lista interna de mods en el manifest, así que no las pedimos
            List<Map<String, Object>> dependencias = esJar
                    ? extraerDependenciasRequeridas(primeraVersion)
                    : new ArrayList<>();

            Map<String, Object> resultado = new HashMap<>();
            resultado.put("compatible", true);
            resultado.put("archivo", nombreArchivo);
            resultado.put("modpack", esModpack);
            resultado.put("dependencias", dependencias);
            return resultado;
        } catch (Exception e) {
            return Map.of("compatible", false, "motivo", "Error consultando Modrinth: " + e.getMessage());
        }
    }

    // Instala un mod en un servidor existente (lo busca por id en BD y verifica propiedad)
    public String instalarModModrinth(Long id, String modrinthId) throws IOException {
        Servidor servidor = obtenerYVerificar(id);
        if (!esLoaderValido(servidor.getTipo())) {
            throw new RuntimeException("Solo se pueden instalar mods en servidores FORGE o FABRIC");
        }
        return descargarMod(servidor.getNombre(), servidor.getTipo(), servidor.getVersion(), modrinthId);
    }

    // Instala un mod directamente en la carpeta de un servidor que aún no está en BD.
    // Se usa al crear el servidor para meter mods antes del primer arranque
    public String instalarModEnCarpeta(String nombreServidor, String tipo, String version, String modrinthId) throws IOException {
        if (!esLoaderValido(tipo)) {
            throw new RuntimeException("Solo FORGE y FABRIC soportan mods");
        }
        return descargarMod(nombreServidor, tipo, version, modrinthId);
    }

    // Instala Fabric API en un servidor recién creado. Si falla devuelve null en lugar de explotar
    // para que la creación del servidor no se rompa por esto
    public String instalarFabricApi(String nombreServidor, String version) {
        try {
            String archivo = descargarMod(nombreServidor, "FABRIC", version, FABRIC_API_ID);
            System.out.println("[Mods] Fabric API instalada en " + nombreServidor + ": " + archivo);
            return archivo;
        } catch (Exception e) {
            System.err.println("[Mods] FALLO instalando Fabric API en " + nombreServidor + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void eliminarMod(Long id, String nombreMod) {
        Servidor servidor = obtenerYVerificar(id);

        // Validación de seguridad: nadie puede pasar una ruta tipo "../otra-cosa"
        if (nombreMod.contains("..") || nombreMod.contains("/") || nombreMod.contains("\\")) {
            throw new RuntimeException("Nombre de archivo inválido");
        }

        File mod = new File(CARPETA_BASE + servidor.getNombre() + "/mods/" + nombreMod);
        if (!mod.exists()) throw new RuntimeException("No existe el mod: " + nombreMod);
        if (!mod.delete()) throw new RuntimeException("No se pudo eliminar el mod: " + nombreMod);
    }

    // ---------------------------------------------------------------
    // ZONA: HELPERS PRIVADOS — la lógica común reusada por los métodos de arriba
    // ---------------------------------------------------------------

    private boolean esLoaderValido(String tipo) {
        return "FORGE".equals(tipo) || "FABRIC".equals(tipo);
    }

    private Servidor obtenerYVerificar(Long id) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);
        return servidor;
    }

    // Pregunta a Modrinth qué versiones de un mod son compatibles con el loader y MC indicados.
    // Modrinth espera los filtros como JSON-array codificado en URL: ?loaders=%5B%22fabric%22%5D
    // Construimos los valores ya codificados manualmente y pasamos la URL final como java.net.URI
    // para que RestTemplate no la vuelva a procesar como URI template
    private List<Map<String, Object>> consultarVersionesModrinth(String modId, String loader, String version) {
        String loadersParam = java.net.URLEncoder.encode("[\"" + loader + "\"]", java.nio.charset.StandardCharsets.UTF_8);
        String versionParam = java.net.URLEncoder.encode("[\"" + version + "\"]", java.nio.charset.StandardCharsets.UTF_8);

        String url = "https://api.modrinth.com/v2/project/" + modId + "/version"
                + "?loaders=" + loadersParam
                + "&game_versions=" + versionParam;

        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resultado = new RestTemplate().getForObject(java.net.URI.create(url), List.class);
            return resultado != null ? resultado : new ArrayList<>();
        } catch (Exception e) {
            System.err.println("[Mods] Error consultando Modrinth: " + url + " → " + e.getMessage());
            throw e;
        }
    }

    // De los archivos de una versión, escoge el más apropiado para instalar:
    //   1. Prioriza el "primario" (campo "primary":true) si es .jar o .mrpack
    //   2. Si no hay primario válido, busca cualquier .jar
    //   3. Si no hay .jar, busca cualquier .mrpack
    // Devuelve null si no hay ningún archivo instalable
    @SuppressWarnings("unchecked")
    private Map<String, Object> elegirArchivoInstalable(Map<String, Object> versionModrinth) {
        List<Map<String, Object>> archivos = (List<Map<String, Object>>) versionModrinth.get("files");
        if (archivos == null || archivos.isEmpty()) return null;

        // Primero buscamos el archivo marcado como "primary" si es jar o mrpack
        for (Map<String, Object> archivo : archivos) {
            String nombre = (String) archivo.get("filename");
            Boolean primary = (Boolean) archivo.get("primary");
            if (Boolean.TRUE.equals(primary) && nombre != null
                    && (nombre.endsWith(".jar") || nombre.endsWith(".mrpack"))) {
                return archivo;
            }
        }
        // Sin primary válido: cualquier .jar
        for (Map<String, Object> archivo : archivos) {
            String nombre = (String) archivo.get("filename");
            if (nombre != null && nombre.endsWith(".jar")) return archivo;
        }
        // Último recurso: cualquier .mrpack
        for (Map<String, Object> archivo : archivos) {
            String nombre = (String) archivo.get("filename");
            if (nombre != null && nombre.endsWith(".mrpack")) return archivo;
        }
        return null;
    }

    // Lógica central: consulta Modrinth, descarga y según el tipo de archivo:
    //   .jar    → mod individual, lo coloca en /mods
    //   .mrpack → modpack, lo extrae y descarga todos los mods que lista su manifest
    private String descargarMod(String nombreServidor, String tipo, String version, String modrinthId) throws IOException {
        List<Map<String, Object>> versiones = consultarVersionesModrinth(modrinthId, tipo.toLowerCase(), version);
        if (versiones.isEmpty()) {
            throw new RuntimeException("No hay versión compatible para " + tipo + " " + version);
        }

        Map<String, Object> primeraVersion = versiones.get(0);
        Map<String, Object> archivo = elegirArchivoInstalable(primeraVersion);
        if (archivo == null) {
            throw new RuntimeException("La versión no tiene un archivo .jar o .mrpack instalable");
        }

        String nombreArchivo = (String) archivo.get("filename");
        String urlDescarga = (String) archivo.get("url");

        if (nombreArchivo.endsWith(".mrpack")) {
            return instalarModpack(nombreServidor, urlDescarga, nombreArchivo);
        }
        return descargarJarSimple(nombreServidor, urlDescarga, nombreArchivo);
    }

    // Descarga un mod individual (.jar) y lo coloca en la carpeta /mods del servidor
    private String descargarJarSimple(String nombreServidor, String urlDescarga, String nombreArchivo) throws IOException {
        File carpetaMods = new File(CARPETA_BASE + nombreServidor + "/mods");
        if (!carpetaMods.exists()) carpetaMods.mkdirs();

        byte[] contenido = descargarBinario(urlDescarga);
        Files.write(new File(carpetaMods, nombreArchivo).toPath(), contenido);
        return nombreArchivo;
    }

    // Descarga binario sin que Spring vuelva a codificar la URL (Modrinth ya las devuelve codificadas).
    // Pasamos URI en vez de String para evitar el doble encoding (%2B → %252B) que rompe descargas
    // de archivos con + en el nombre como fabric-api-0.102.0+1.21.jar
    private byte[] descargarBinario(String urlDescarga) {
        return new RestTemplate().getForObject(java.net.URI.create(urlDescarga), byte[].class);
    }

    // Procesa un modpack .mrpack:
    //   1. Descarga el .mrpack (es un zip)
    //   2. Extrae el manifest interno (modrinth.index.json) que lista todos los mods, configs, etc
    //   3. Por cada archivo del manifest, lo descarga y lo coloca en su ruta correcta dentro del servidor
    //   4. Salta archivos marcados como "unsupported" para servidor (solo para cliente)
    @SuppressWarnings("unchecked")
    private String instalarModpack(String nombreServidor, String urlDescarga, String nombreArchivo) throws IOException {
        byte[] mrpackBytes = descargarBinario(urlDescarga);
        if (mrpackBytes == null) throw new RuntimeException("No se pudo descargar el modpack");

        // El .mrpack es un ZIP. Lo recorremos hasta encontrar modrinth.index.json
        Map<String, Object> manifest = leerManifestDeMrpack(mrpackBytes);
        List<Map<String, Object>> archivos = (List<Map<String, Object>>) manifest.get("files");
        if (archivos == null) throw new RuntimeException("Manifest del modpack inválido");

        File rutaServidor = new File(CARPETA_BASE + nombreServidor);
        if (!rutaServidor.exists()) rutaServidor.mkdirs();

        int instalados = 0, saltados = 0;

        for (Map<String, Object> archivo : archivos) {
            String ruta = (String) archivo.get("path");
            // Seguridad: evitar rutas como "../config/server.properties" que se salgan del servidor
            if (ruta == null || ruta.contains("..")) { saltados++; continue; }

            // Saltar archivos no soportados en el servidor (por ejemplo shaders, mods solo de cliente)
            Map<String, String> env = (Map<String, String>) archivo.get("env");
            if (env != null && "unsupported".equals(env.get("server"))) { saltados++; continue; }

            List<String> urls = (List<String>) archivo.get("downloads");
            if (urls == null || urls.isEmpty()) { saltados++; continue; }

            File destino = new File(rutaServidor, ruta);
            destino.getParentFile().mkdirs();
            Files.write(destino.toPath(), descargarBinario(urls.get(0)));
            instalados++;
        }

        // El modpack también puede traer una carpeta /overrides/ con configs y archivos extra
        copiarOverridesDelMrpack(mrpackBytes, rutaServidor);

        System.out.println("[Mods] Modpack " + nombreArchivo + " instalado: " + instalados + " archivos, " + saltados + " omitidos");
        return nombreArchivo;
    }

    // Lee el modrinth.index.json de dentro del .mrpack (que es un ZIP)
    private Map<String, Object> leerManifestDeMrpack(byte[] mrpackBytes) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(mrpackBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("modrinth.index.json".equals(entry.getName())) {
                    byte[] data = zis.readAllBytes();
                    return new ObjectMapper().readValue(data, Map.class);
                }
            }
        }
        throw new RuntimeException("El modpack no contiene modrinth.index.json");
    }

    // Si el modpack trae overrides/ los copia tal cual al servidor
    // (sirven para configs, ajustes, mundos predefinidos, etc.)
    private void copiarOverridesDelMrpack(byte[] mrpackBytes, File rutaServidor) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(mrpackBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String nombre = entry.getName();
                // Solo nos interesan overrides/ y server-overrides/ (no client-overrides/ que es solo cliente)
                if (!nombre.startsWith("overrides/") && !nombre.startsWith("server-overrides/")) continue;
                if (entry.isDirectory()) continue;

                String rutaRelativa = nombre.startsWith("server-overrides/")
                        ? nombre.substring("server-overrides/".length())
                        : nombre.substring("overrides/".length());

                if (rutaRelativa.contains("..")) continue;

                File destino = new File(rutaServidor, rutaRelativa);
                destino.getParentFile().mkdirs();
                Files.write(destino.toPath(), zis.readAllBytes());
            }
        }
    }

    // Saca de la versión de Modrinth la lista de mods que esta versión declara como dependencias requeridas,
    // y para cada uno consulta Modrinth para obtener su nombre legible (no solo el id críptico)
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extraerDependenciasRequeridas(Map<String, Object> versionModrinth) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        List<Map<String, Object>> deps = (List<Map<String, Object>>) versionModrinth.get("dependencies");
        if (deps == null) return resultado;

        RestTemplate http = new RestTemplate();
        for (Map<String, Object> dep : deps) {
            if (!"required".equals(dep.get("dependency_type"))) continue;
            String depId = (String) dep.get("project_id");
            if (depId == null) continue;

            String titulo = depId;  // por defecto si falla la consulta del nombre
            try {
                Map<String, Object> proyecto = http.getForObject(
                        "https://api.modrinth.com/v2/project/" + depId, Map.class);
                if (proyecto != null && proyecto.get("title") != null) {
                    titulo = (String) proyecto.get("title");
                }
            } catch (Exception ignored) {}

            Map<String, Object> info = new HashMap<>();
            info.put("id", depId);
            info.put("titulo", titulo);
            resultado.add(info);
        }
        return resultado;
    }
}
