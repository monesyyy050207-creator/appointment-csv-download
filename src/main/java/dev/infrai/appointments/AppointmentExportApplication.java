package dev.infrai.appointments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AppointmentExportApplication {
    public static void main(String[] args) {
        SpringApplication.run(AppointmentExportApplication.class, args);
    }
}
