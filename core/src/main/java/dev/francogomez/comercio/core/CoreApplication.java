package dev.francogomez.comercio.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * VIA_DTO serializa las páginas con una forma estable ({content, page:{...}}) en lugar
 * de volcar el PageImpl interno de Spring Data, cuya estructura no tiene garantía de
 * compatibilidad entre versiones. Sin esto, un upgrade de Spring puede romper clientes.
 */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@EnableScheduling // el publisher del outbox corre en un @Scheduled
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
