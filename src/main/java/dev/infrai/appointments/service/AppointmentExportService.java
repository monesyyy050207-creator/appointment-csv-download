package dev.infrai.appointments.service;

import dev.infrai.appointments.domain.AppointmentExport;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class AppointmentExportService {
    public ExportedCsv export(AppointmentExport.Request request) {
        List<AppointmentExport.Appointment> reportable = request.appointments().stream()
                .filter(this::isReportable)
                .toList();
        byte[] csv = renderCsv(reportable).getBytes(StandardCharsets.UTF_8);
        return new ExportedCsv(csv, reportable.size());
    }

    boolean isReportable(AppointmentExport.Appointment appointment) {
        return appointment.status() == AppointmentExport.WorkflowStatus.CONFIRMED
                || appointment.status() == AppointmentExport.WorkflowStatus.COMPLETED;
    }

    String renderCsv(List<AppointmentExport.Appointment> appointments) {
        StringBuilder csv = new StringBuilder("appointment_reference,scheduled_at,service_line,status\n");
        appointments.forEach(appointment -> csv
                .append(cell(appointment.appointmentReference())).append(',')
                .append(cell(appointment.scheduledAt().toString())).append(',')
                .append(cell(appointment.serviceLine())).append(',')
                .append(appointment.status()).append('\n'));
        return csv.toString();
    }

    private String cell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public record ExportedCsv(byte[] content, int exportedAppointments) {
    }
}
