package dev.infrai.appointments.web;

import dev.infrai.appointments.domain.AppointmentExport;
import dev.infrai.appointments.service.AppointmentExportService;
import dev.infrai.appointments.storage.InfraiStorageClient;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/appointment-exports")
public class AppointmentExportController {
    private final AppointmentExportService exports;
    private final InfraiStorageClient storage;

    public AppointmentExportController(AppointmentExportService exports, InfraiStorageClient storage) {
        this.exports = exports;
        this.storage = storage;
    }

    @PostMapping
    public ResponseEntity<AppointmentExport.Result> create(@Valid @RequestBody AppointmentExport.Request request) {
        AppointmentExportService.ExportedCsv export = exports.export(request);
        String key = "appointment-exports/" + request.reportReference() + ".csv";
        String idempotencyPrefix = "appointment-export-" + request.reportReference();

        storage.createBucket(idempotencyPrefix + "-bucket");
        InfraiStorageClient.SignedUrl upload = storage.presignPut(
                key, "text/csv; charset=utf-8", export.content().length, idempotencyPrefix + "-put");
        storage.upload(upload, export.content());
        InfraiStorageClient.SignedUrl download = storage.presignGet(
                key, "attachment; filename=appointments.csv");

        int expiresSeconds = storage.downloadExpiresSeconds();
        AppointmentExport.Result result = new AppointmentExport.Result(
                request.reportReference(),
                export.exportedAppointments(),
                download.url(),
                OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(expiresSeconds),
                "Appointment export is ready: " + export.exportedAppointments() + " workflow records included.");
        return ResponseEntity.ok(result);
    }
}
