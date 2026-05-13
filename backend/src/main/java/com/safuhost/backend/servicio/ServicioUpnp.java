package com.safuhost.backend.servicio;

import jakarta.annotation.PostConstruct;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;
import org.springframework.stereotype.Service;

@Service
public class ServicioUpnp {

    private GatewayDevice gateway;

    @PostConstruct
    public void inicializar() {
        new Thread(() -> {
            try {
                GatewayDiscover discover = new GatewayDiscover();
                discover.discover();
                gateway = discover.getValidGateway();
                if (gateway != null) {
                    System.out.println("[UPnP] Gateway: " + gateway.getFriendlyName() + " | IP externa: " + gateway.getExternalIPAddress());
                } else {
                    System.err.println("[UPnP] No se encontró ningún gateway UPnP. Los servidores no serán accesibles desde internet automáticamente.");
                }
            } catch (Exception e) {
                System.err.println("[UPnP] Error al inicializar: " + e.getMessage());
            }
        }, "upnp-discovery").start();
    }

    public void abrirPuerto(int puerto) {
        if (gateway == null) return;
        try {
            PortMappingEntry existente = new PortMappingEntry();
            boolean yaMapeado = gateway.getSpecificPortMappingEntry(puerto, "TCP", existente);
            if (!yaMapeado) {
                String ipLocal = gateway.getLocalAddress().getHostAddress();
                boolean exito = gateway.addPortMapping(puerto, puerto, ipLocal, "TCP", "Safuhost-" + puerto);
                if (exito) {
                    System.out.println("[UPnP] Puerto TCP " + puerto + " abierto correctamente");
                } else {
                    System.err.println("[UPnP] El router rechazó la apertura del puerto " + puerto);
                }
            }
        } catch (Exception e) {
            System.err.println("[UPnP] Error al abrir puerto " + puerto + ": " + e.getMessage());
        }
    }

    public void cerrarPuerto(int puerto) {
        if (gateway == null) return;
        try {
            gateway.deletePortMapping(puerto, "TCP");
            System.out.println("[UPnP] Puerto TCP " + puerto + " cerrado");
        } catch (Exception e) {
            System.err.println("[UPnP] Error al cerrar puerto " + puerto + ": " + e.getMessage());
        }
    }
}
