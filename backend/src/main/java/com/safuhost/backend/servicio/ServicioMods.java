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

@Service
public class ServicioMods {

    private static final String FABRIC_API_ID = "P7dR8mSH";

    private static final String CARPETA_BASE = "C:/mc-servers/";

    @Autowired
    private RepositorioServidor repositorio;

    @Autowired
    private ServicioUsuario servicioUsuario;

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
        String url = UriComponentsBuilder.fromUriString("https://api.modrinth.com/v2/search")
                .queryParam("query", query)
                .queryParam("limit", 20)
                .toUriString();
        return new RestTemplate().getForObject(url, Object.class);
    }

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

    public String instalarModModrinth(Long id, String modrinthId) throws IOException {
        Servidor servidor = obtenerYVerificar(id);
        if (!esLoaderValido(servidor.getTipo())) {
            throw new RuntimeException("Solo se pueden instalar mods en servidores FORGE o FABRIC");
        }
        return descargarMod(servidor.getNombre(), servidor.getTipo(), servidor.getVersion(), modrinthId);
    }

    public String instalarModEnCarpeta(String nombreServidor, String tipo, String version, String modrinthId) throws IOException {
        if (!esLoaderValido(tipo)) {
            throw new RuntimeException("Solo FORGE y FABRIC soportan mods");
        }
        return descargarMod(nombreServidor, tipo, version, modrinthId);
    }

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

        if (nombreMod.contains("..") || nombreMod.contains("/") || nombreMod.contains("\\")) {
            throw new RuntimeException("Nombre de archivo inválido");
        }

        File mod = new File(CARPETA_BASE + servidor.getNombre() + "/mods/" + nombreMod);
        if (!mod.exists()) throw new RuntimeException("No existe el mod: " + nombreMod);
        if (!mod.delete()) throw new RuntimeException("No se pudo eliminar el mod: " + nombreMod);
    }

    private boolean esLoaderValido(String tipo) {
        return "FORGE".equals(tipo) || "FABRIC".equals(tipo);
    }

    private Servidor obtenerYVerificar(Long id) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);
        return servidor;
    }

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> elegirArchivoInstalable(Map<String, Object> versionModrinth) {
        List<Map<String, Object>> archivos = (List<Map<String, Object>>) versionModrinth.get("files");
        if (archivos == null || archivos.isEmpty()) return null;

        for (Map<String, Object> archivo : archivos) {
            String nombre = (String) archivo.get("filename");
            Boolean primary = (Boolean) archivo.get("primary");
            if (Boolean.TRUE.equals(primary) && nombre != null
                    && (nombre.endsWith(".jar") || nombre.endsWith(".mrpack"))) {
                return archivo;
            }
        }
        for (Map<String, Object> archivo : archivos) {
            String nombre = (String) archivo.get("filename");
            if (nombre != null && nombre.endsWith(".jar")) return archivo;
        }
        for (Map<String, Object> archivo : archivos) {
            String nombre = (String) archivo.get("filename");
            if (nombre != null && nombre.endsWith(".mrpack")) return archivo;
        }
        return null;
    }

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

    private String descargarJarSimple(String nombreServidor, String urlDescarga, String nombreArchivo) throws IOException {
        File carpetaMods = new File(CARPETA_BASE + nombreServidor + "/mods");
        if (!carpetaMods.exists()) carpetaMods.mkdirs();

        byte[] contenido = descargarBinario(urlDescarga);
        Files.write(new File(carpetaMods, nombreArchivo).toPath(), contenido);
        return nombreArchivo;
    }

    private byte[] descargarBinario(String urlDescarga) {
        return new RestTemplate().getForObject(java.net.URI.create(urlDescarga), byte[].class);
    }

    @SuppressWarnings("unchecked")
    private String instalarModpack(String nombreServidor, String urlDescarga, String nombreArchivo) throws IOException {
        byte[] mrpackBytes = descargarBinario(urlDescarga);
        if (mrpackBytes == null) throw new RuntimeException("No se pudo descargar el modpack");

        Map<String, Object> manifest = leerManifestDeMrpack(mrpackBytes);
        List<Map<String, Object>> archivos = (List<Map<String, Object>>) manifest.get("files");
        if (archivos == null) throw new RuntimeException("Manifest del modpack inválido");

        File rutaServidor = new File(CARPETA_BASE + nombreServidor);
        if (!rutaServidor.exists()) rutaServidor.mkdirs();

        int instalados = 0, saltados = 0;

        for (Map<String, Object> archivo : archivos) {
            String ruta = (String) archivo.get("path");
            if (ruta == null || ruta.contains("..")) { saltados++; continue; }

            Map<String, String> env = (Map<String, String>) archivo.get("env");
            if (env != null && "unsupported".equals(env.get("server"))) { saltados++; continue; }

            List<String> urls = (List<String>) archivo.get("downloads");
            if (urls == null || urls.isEmpty()) { saltados++; continue; }

            File destino = new File(rutaServidor, ruta);
            destino.getParentFile().mkdirs();
            Files.write(destino.toPath(), descargarBinario(urls.get(0)));
            instalados++;
        }

        copiarOverridesDelMrpack(mrpackBytes, rutaServidor);

        System.out.println("[Mods] Modpack " + nombreArchivo + " instalado: " + instalados + " archivos, " + saltados + " omitidos");
        return nombreArchivo;
    }

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

    private void copiarOverridesDelMrpack(byte[] mrpackBytes, File rutaServidor) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(mrpackBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String nombre = entry.getName();
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

            String titulo = depId;
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
