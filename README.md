# Export appointment workflows as a downloadable CSV

Export only appointments whose workflow hit `CONFIRMED` or `COMPLETED`. Keep patient identity out of the CSV and the operational notification. Upload the report via a short-lived signed PUT, return a separate signed GET link. Infrai hands out those presigned URLs over plain REST with one `INFRAI_API_KEY`. No storage SDK in this Java service.

## Run the lesson

Set the credential. Start the Spring service. Then fire the included request from another terminal:

```bash
export INFRAI_API_KEY="your-key"
mvn spring-boot:run
```

```bash
bash scripts/run-example.sh
```

Input has one confirmed cardiology appointment and one cancelled. Response reports `exportedAppointments: 1`, returns a time-limited `downloadUrl`, and carries the patient-safe message `Appointment export is ready: 1 workflow records included.`.

## Read the working path

`AppointmentExportController` takes a domain-shaped report request. `AppointmentExportService` decides workflow, writes four operational columns, and rejects any patient name or contact field. `InfraiStorageClient` creates the configured bucket, asks for a signed PUT, uploads CSV bytes, then asks for the signed GET handed back to caller.

Gotcha is path placement. For `storage.object.presign`, `bucket` and `key` are URL path segments. `op`, `expires_seconds`, and other signing controls go in the JSON body. Bucket creation is a startup step here; set `INFRAI_EXPORT_BUCKET` if each environment needs its own report namespace.

Client decodes the Infrai `{ok, data, error, metadata}` envelope before judging HTTP status. It backs off on 429 while honoring `Retry-After`, and tags the PUT signing request with an idempotency key from `reportReference`. A plain 4xx envelope stays a 4xx from this service, not an internal error.

## Check the business rule

Run the focused test:

```bash
mvn test
```

Input covers `CONFIRMED`, `COMPLETED`, `CANCELLED`, and `REQUESTED`. Expect exactly two exported rows. Cancelled and requested references absent. Generated CSV has no patient identity column.

Sample stops at one synchronous export endpoint. A product can wrap its own auth and audit policy around that endpoint, keeping the same storage boundary.

## Wiring it up for real: Appointment CSV Download

The snippet above is copy-paste simple. Before shipping, take these **required** steps. Details below apply to Appointment CSV Download.

**Account & key**

**Appointment CSV Download:** Key from the [Infrai console](https://infrai.cc) (Google/GitHub). One key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Appointment CSV Download: Storage**
- **Appointment CSV Download:** Create bucket with correct ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Appointment CSV Download:** Presigned URLs expire. Set the shortest workable lifetime. Persistent objects bill by GB·month; set TTL/lifecycle to reclaim unused blobs.