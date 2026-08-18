package dev.infrai.appointments.service;

import dev.infrai.appointments.domain.AppointmentExport;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentExportServiceTest {
    @Test
    void exportsOnlyConfirmedAndCompletedAppointmentsWithoutPatientIdentity() {
        AppointmentExportService service = new AppointmentExportService();

        AppointmentExport.Request request = new AppointmentExport.Request("week-34", List.of(
                appointment("A-101", AppointmentExport.WorkflowStatus.CONFIRMED),
                appointment("A-102", AppointmentExport.WorkflowStatus.COMPLETED),
                appointment("A-103", AppointmentExport.WorkflowStatus.CANCELLED),
                appointment("A-104", AppointmentExport.WorkflowStatus.REQUESTED)));

        AppointmentExportService.ExportedCsv result = service.export(request);

        assertThat(result.exportedAppointments()).isEqualTo(2);
        String csv = new String(result.content(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("A-101", "A-102");
        assertThat(csv).doesNotContain("A-103", "A-104");
        assertThat(csv.toLowerCase()).doesNotContain("patient");
    }

    private AppointmentExport.Appointment appointment(String reference, AppointmentExport.WorkflowStatus status) {
        return new AppointmentExport.Appointment(
                reference, OffsetDateTime.parse("2026-08-20T09:30:00Z"), "cardiology", status);
    }
}
