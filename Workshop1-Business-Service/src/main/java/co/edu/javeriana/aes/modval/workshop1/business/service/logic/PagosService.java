/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package co.edu.javeriana.aes.modval.workshop1.business.service.logic;

import co.edu.javeriana.aes.modval.workshop1.soap.client.Pago;
import co.edu.javeriana.aes.modval.workshop1.soap.client.PagosInerface;
import co.edu.javeriana.aes.modval.workshop1.soap.client.PagosServiceService;
import co.edu.javeriana.aes.modval.workshop1.soap.client.ReferenciaFactura;
import co.edu.javeriana.aes.modval.workshop1.soap.client.Resultado;
import co.edu.javeriana.aes.modval.workshop1.soap.client.ResultadoConsulta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

/**
 *
 * @author sebastianpacheco
 */
@RestController
public class PagosService {

    private Logger log = LoggerFactory.getLogger(PagosService.class);

    private final String SERVICIO_LUZ = "luz";

    private final String SERVICIO_GAS = "gas";

    private final String SERVICIO_ETB = "etb";

    private RestTemplate rt = new RestTemplate();

    private final String REST_SERVICE = "http://localhost:8082/payments/%s";

    @RequestMapping(value = "/pagos/{servicio}/{idfactura}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public Object consultarFactura(@PathVariable String servicio, @PathVariable int idfactura) throws JsonProcessingException {
        if (validarServicio(servicio)) {
            if (servicio.equals(SERVICIO_LUZ)) {
                return consultarServicioSOAP(idfactura);
            } else {
                String url = String.format(REST_SERVICE, idfactura);
                log.info(url);
                try {
                    HttpEntity<ObjectNode> on = rt.getForEntity(url, ObjectNode.class);
                    on.getHeaders().entrySet().iterator().forEachRemaining(entry -> log.info(entry.getKey() + entry.getValue()));
                    return on;
                } catch (Exception e) {
                    return ResponseEntity.status(404).build();
                }
            }
        }
        return getRespuestaServicioDesconocido();
    }

    @RequestMapping(value = "/pagos/{servicio}/{idfactura}", method = RequestMethod.POST, produces = MediaType.APPLICATION_JSON_VALUE)
    public Object pagarFactura(@PathVariable String servicio, @PathVariable int idfactura, @RequestBody ObjectNode objectNode) {
        if (validarServicio(servicio)) {
            if (servicio.equals(SERVICIO_LUZ)) {
                return pagarServicioSOAP(idfactura, Integer.parseInt(objectNode.get("totalPagar").asText()));
            } else {
                ObjectMapper om = new ObjectMapper();
                ObjectNode on = om.createObjectNode();
                on.put("valorFactura", objectNode.get("totalPagar").asText());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                try {
                    HttpEntity<ObjectNode> entity = new HttpEntity<>(on, headers);
                    log.debug(String.format("Request Body : %s", entity.getBody().toString()));
                    return rt.postForEntity(String.format(REST_SERVICE, idfactura), entity, ObjectNode.class);
                } catch (Exception e) {
                    return ResponseEntity.status(404).build();
                }
            }
        }
        return getRespuestaServicioDesconocido();
    }

    @RequestMapping(value = "/pagos/{servicio}/{idfactura}", method = RequestMethod.DELETE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Object compensarPago(@PathVariable String servicio, @PathVariable int idfactura, @RequestBody ObjectNode objectNode) {
        if (validarServicio(servicio)) {
            if (servicio.equals(SERVICIO_LUZ) || servicio.equals(SERVICIO_GAS)) {
                return compensarServicioSOAP(idfactura, Integer.parseInt(objectNode.get("totalPagar").asText()));
            } else {
                ObjectMapper om = new ObjectMapper();
                ObjectNode on = om.createObjectNode();
                on.put("valorFactura", objectNode.get("totalPagar").asText());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                try {
                    HttpEntity<ObjectNode> entity = new HttpEntity<>(on, headers);
                    log.info(String.format("Request Body : %s", entity.getBody().toString()));
                    String location = String.format(REST_SERVICE, idfactura);
                    log.info(String.format("location: %s", entity.getBody().toString()));
                    return rt.exchange(location,HttpMethod.DELETE, entity, ObjectNode.class);
                } catch (Exception e) {
                    return ResponseEntity.status(404).build();
                }
            }
        }
        return getRespuestaServicioDesconocido();
    }

    private ObjectNode getRespuestaServicioDesconocido() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode objectNode1 = objectMapper.createObjectNode();
        objectNode1.put("status", "servicio desconocido");
        return objectNode1;
    }

    private boolean validarServicio(String servicio) {
        if (servicio.equals(SERVICIO_LUZ) || servicio.equals(SERVICIO_GAS) || servicio.equals(SERVICIO_ETB)) {
            return true;
        }
        return false;
    }

    private ObjectNode consultarServicioSOAP(int referencia) {
        PagosServiceService pss = new PagosServiceService();
        PagosInerface psp = pss.getPagosServicePort();
        ReferenciaFactura rf = new ReferenciaFactura();
        rf.setReferenciaFactura(String.format("%d", referencia));
        ResultadoConsulta rc = psp.cosultar(rf);
        log.debug(String.format("Recibida respuesta consulta servicio %s", rc.toString()));
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode objectNode1 = objectMapper.createObjectNode();
        objectNode1.put("refencia", rc.getReferenciaFactura().getReferenciaFactura());
        objectNode1.put("totlaPagar", rc.getTotalPagar());

        return objectNode1;
    }

    private ObjectNode pagarServicioSOAP(int referencia, int totalPagar) {
        PagosServiceService pss = new PagosServiceService();
        PagosInerface psp = pss.getPagosServicePort();
        ReferenciaFactura rf = new ReferenciaFactura();
        rf.setReferenciaFactura(String.format("%d", referencia));
        Pago pago = new Pago();
        pago.setReferenciaFactura(rf);
        pago.setTotalPagar(totalPagar);
        Resultado res = psp.pagar(pago);
        log.debug(String.format("Recibida respuesta pagoservicio %s", res.toString()));
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode objectNode1 = objectMapper.createObjectNode();
        objectNode1.put("refencia", pago.getReferenciaFactura().getReferenciaFactura());
        objectNode1.put("mensaje", res.getMensaje());

        return objectNode1;
    }

    private ObjectNode compensarServicioSOAP(int referencia, int totalPagar) {
        PagosServiceService pss = new PagosServiceService();
        PagosInerface psp = pss.getPagosServicePort();
        ReferenciaFactura rf = new ReferenciaFactura();
        rf.setReferenciaFactura(String.format("%d", referencia));
        Pago pago = new Pago();
        pago.setReferenciaFactura(rf);
        pago.setTotalPagar(totalPagar);
        Resultado res = psp.compensar(pago);
        log.debug(String.format("Recibida respuesta pagoservicio %s", res.toString()));
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode objectNode1 = objectMapper.createObjectNode();
        objectNode1.put("refencia", pago.getReferenciaFactura().getReferenciaFactura());
        objectNode1.put("mensaje", res.getMensaje());

        return objectNode1;
    }

}
