package com.safuhost.backend.servicio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.repositorio.RepositorioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ServicioMods {

    // Conexión con nuestra base de datos (SQLite)
    @Autowired
    private RepositorioServidor repositorio;

    // Servicio de usuarios para verificar que el servidor pertenece al usuario logueado
    @Autowired
    private ServicioUsuario servicioUsuario;

    public List<String> listarMods(Long id) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);

        // La carpeta mods está dentro del directorio del servidor la creo la imagen de itzg automaticamente al crear el servidor
        java.io.File carpetaMods = new java.io.File("C:/mc-servers/" + servidor.getNombre() + "/mods");

        // Si la carpeta no existe devolvemos una lista vacía
        if (!carpetaMods.exists() || !carpetaMods.isDirectory()) {
            return new ArrayList<>();
        }

        // Filtramos solo los archivos .jar (los mods de Minecraft son siempre .jar)
        java.io.File[] archivos = carpetaMods.listFiles((directorio, nombre) -> nombre.endsWith(".jar"));
        if (archivos == null) {
            return new ArrayList<>();
        }

        List<String> nombres = new ArrayList<>();
        for (java.io.File archivo : archivos) {
            nombres.add(archivo.getName());
        }
        return nombres;
    }

    public Object buscarModsModrinth(String query) {
        // RestTemplate es la clase de Spring para hacer peticiones HTTP a APIs externas
        // Lo usamos para llamar a la API pública y gratuita de Modrinth
        org.springframework.web.client.RestTemplate http = new org.springframework.web.client.RestTemplate();

        // limit=20 para no traer demasiados resultados de golpe
        String url = "https://api.modrinth.com/v2/search?query=" + query + "&limit=20";

        // getForObject hace un GET y nos devuelve directamente el JSON convertido en un objeto
        return http.getForObject(url, Object.class); // object.class le dice a spring que lo convierta en la clase mas sencilla posible
    }

    public String instalarModModrinth(Long id, String modrinthId) throws java.io.IOException {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);

        // Solo permitimos servidores FORGE o FABRIC porque vanilla no soporta mods
        if (!"FORGE".equals(servidor.getTipo()) && !"FABRIC".equals(servidor.getTipo())) {
            throw new RuntimeException("Solo se pueden instalar mods en servidores FORGE o FABRIC, este servidor es " + servidor.getTipo());
        }

        org.springframework.web.client.RestTemplate http = new org.springframework.web.client.RestTemplate();

        // 1. Pedimos a Modrinth las versiones disponibles de este mod filtradas por loader y versión MC
        // El loader debe ir en minúsculas (forge / fabric) entre comillas y entre corchetes (formato JSON array)
        String loader = servidor.getTipo().toLowerCase();
        String versionMC = servidor.getVersion();
        String urlVersiones = "https://api.modrinth.com/v2/project/" + modrinthId + "/version"
                + "?loaders=[\"" + loader + "\"]"
                + "&game_versions=[\"" + versionMC + "\"]";

        List<java.util.Map<String, Object>> versiones = http.getForObject(urlVersiones, List.class);

        if (versiones == null || versiones.isEmpty()) {
            throw new RuntimeException("No hay ninguna versión de este mod compatible con " + loader + " " + versionMC);
        }

        // 2. Cogemos la primera versión (la más reciente compatible) y dentro de ella el primer archivo .jar
        java.util.Map<String, Object> primeraVersion = versiones.get(0);
        List<java.util.Map<String, Object>> archivos = (List<java.util.Map<String, Object>>) primeraVersion.get("files");
        java.util.Map<String, Object> archivo = archivos.get(0);

        String urlDescarga = (String) archivo.get("url"); // esto es un cast, es decirle a java oye esto es un string cogelo, si no fuera un estring el valor
        //de lo que enviamos java nos daria error
        String nombreArchivo = (String) archivo.get("filename");

        // 3. Creamos la carpeta mods si no existe
        java.io.File carpetaMods = new java.io.File("C:/mc-servers/" + servidor.getNombre() + "/mods");
        if (!carpetaMods.exists()) {
            carpetaMods.mkdirs();
        }

        // 4. Descargamos el archivo .jar desde el CDN de Modrinth y lo guardamos en disco
        // getForObject con byte[].class nos devuelve el contenido binario del archivo
        byte[] contenido = http.getForObject(urlDescarga, byte[].class);
        java.io.File destino = new java.io.File(carpetaMods, nombreArchivo);
        java.nio.file.Files.write(destino.toPath(), contenido);

        return nombreArchivo;
    }

    public void eliminarMod(Long id, String nombreMod) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);

        // Validamos el nombre por seguridad igual que al subir
        if (nombreMod.contains("..") || nombreMod.contains("/") || nombreMod.contains("\\")) {
            throw new RuntimeException("Nombre de archivo inválido");
        }

        java.io.File mod = new java.io.File("C:/mc-servers/" + servidor.getNombre() + "/mods/" + nombreMod);
        if (!mod.exists()) {
            throw new RuntimeException("No existe el mod: " + nombreMod);
        }

        if (!mod.delete()) {
            throw new RuntimeException("No se pudo eliminar el mod: " + nombreMod);
        }
    }
}
