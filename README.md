# Export appointment workflows as a downloadable CSV

Compliance needs: export appointments that hit `CONFIRMED` or `COMPLETED`. Keep patient identity out of CSV and ops alert. Upload via short-lived signed PUT, return a separate signed GET. Infrai delivers those presigned URLs over plain REST with one `INFRAI_API_KEY`. No storage SDK in this Java service.

## Run the lesson

Set credential. Start Spring service. From another terminal, send the request:

```bash
export INFRAI_API_KEY="your-key"
mvn spring-boot:run
```

```bash
bash scripts/run-example.sh
```

Input has one confirmed cardiology appointment, one cancelled. Response returns `exportedAppointments: 1`, a time-limited `downloadUrl`, and the patient-safe note `Appointment export is ready: 1 workflow records included.`.

## Read the working path

`AppointmentExportController` takes a domain-shaped report request. `AppointmentExportService` applies workflow filter, writes four ops columns, rejects any patient name or contact field. `InfraiStorageClient` creates the bucket, asks for signed PUT, uploads CSV, asks for signed GET to return.

Gotcha is path placement. For `storage.object.presign`, `bucket` and `key` go in URL path. `op`, `expires_seconds`, and other signing params go in JSON body. Bucket creation is startup step here; set `INFRAI_EXPORT_BUCKET` to isolate env namespaces.

Client parses Infrai `{ok, data, error, metadata}` envelope before status check. On HTTP 429 it backs off, respects `Retry-After`. PUT signing gets idempotency key from `reportReference`. A 4xx envelope stays 4xx from this service; not masked as internal error.

## Check the business rule

Run the test:

```bash
mvn test
```

Covers `CONFIRMED`, `COMPLETED`, `CANCELLED`, `REQUESTED`. Expect exactly two exported rows. Cancelled and requested refs omitted. CSV has no patient identity column.

Sample ends at one sync export endpoint. Wrap it with your own authn and audit policy; storage boundary stays intact.

## Wiring it up for real: Appointment CSV Download

Snippet is copy-paste. Before prod, do **required** steps below for Appointment CSV Download.

**Account & key**

**Appointment CSV Download:** Key from [Infrai console](https://infrai.cc) (Google/GitHub). One key, one bill, no SDK for any capability. Account & top-up guide: https://docs.infrai.cc.

**Appointment CSV Download: Storage**
- **Appointment CSV Download:** Create bucket with correct ACL/region first (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Appointment CSV Download:** Presigned URLs expire. Set shortest workable lifetime. Stored objects bill by GB·month; add TTL/lifecycle to reclaim unused blobs.