package com.safuhost.backend.servicio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.repositorio.RepositorioServidor;
import jakarta.annotation.PostConstruct;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ServicioUpnp {

    @Autowired
    private RepositorioServidor repositorio;

    private final CompletableFuture<GatewayDevice> gatewayFuture = new CompletableFuture<>();
    private volatile String ipExternaCache = null;

    @PostConstruct
    public void inicializar() {
        new Thread(() -> {
            try {
                GatewayDiscover discover = new GatewayDiscover();
                discover.discover();
                GatewayDevice gw = discover.getValidGateway();
                if (gw != null) {
                    ipExternaCache = gw.getExternalIPAddress();
                    System.out.println("[UPnP] Gateway: " + gw.getFriendlyName() + " | IP externa: " + ipExternaCache);
                    gatewayFuture.complete(gw);
                    restaurarPuertos(gw);
                } else {
                    System.err.println("[UPnP] No se encontró ningún gateway UPnP. Los servidores no serán accesibles desde internet automáticamente.");
                    gatewayFuture.complete(null);
                }
            } catch (Exception e) {
                System.err.println("[UPnP] Error al inicializar: " + e.getMessage());
                gatewayFuture.complete(null);
            }
        }, "upnp-discovery").start();
    }

    private GatewayDevice obtenerGateway() {
        try {
            return gatewayFuture.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        }
    }

    private void restaurarPuertos(GatewayDevice gw) {
        List<Servidor> activos = repositorio.findByEstadoIn(List.of("INICIANDO", "EN_LINEA"));
        if (activos.isEmpty()) return;
        System.out.println("[UPnP] Restaurando puertos de " + activos.size() + " servidor(es) activo(s)...");
        for (Servidor s : activos) {
            abrirPuertoConGateway(gw, s.getPuerto());
        }
    }

    public void abrirPuerto(int puerto) {
        GatewayDevice gw = obtenerGateway();
        if (gw == null) return;
        abrirPuertoConGateway(gw, puerto);
    }

    private void abrirPuertoConGateway(GatewayDevice gw, int puerto) {
        try {
            String ipLocal = gw.getLocalAddress().getHostAddress();
            boolean exito = gw.addPortMapping(puerto, puerto, ipLocal, "TCP", "Safuhost-" + puerto);
            if (exito) {
                System.out.println("[UPnP] Puerto TCP " + puerto + " abierto correctamente");
            } else {
                System.err.println("[UPnP] El router rechazó la apertura del puerto " + puerto);
            }
        } catch (Exception e) {
            System.err.println("[UPnP] Error al abrir puerto " + puerto + ": " + e.getMessage());
        }
    }

    public String getIpExterna() {
        if (ipExternaCache != null) return ipExternaCache;
        try {
            GatewayDevice gw = gatewayFuture.get(15, TimeUnit.SECONDS);
            if (gw != null) return ipExternaCache;
        } catch (Exception ignored) {}
        return null;
    }

    public void cerrarPuerto(int puerto) {
        GatewayDevice gw = obtenerGateway();
        if (gw == null) return;
        try {
            gw.deletePortMapping(puerto, "TCP");
            System.out.println("[UPnP] Puerto TCP " + puerto + " cerrado");
        } catch (Exception e) {
            System.err.println("[UPnP] Error al cerrar puerto " + puerto + ": " + e.getMessage());
        }
    }
}
