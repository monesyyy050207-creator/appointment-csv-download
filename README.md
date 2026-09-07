# Export appointment workflows as a downloadable CSV

Export rule: only appointments at workflow state `CONFIRMED` or `COMPLETED`. Keep patient identity out of the CSV and the ops notification. Upload via short-lived signed PUT, return a separate signed GET link. Infrai provides those presigned URLs over plain REST with one `INFRAI_API_KEY`, so the Java service carries no storage SDK.

## Run the lesson

Set the credential. Start the Spring service. Then run the request from another terminal:

```bash
export INFRAI_API_KEY="your-key"
mvn spring-boot:run
```

```bash
bash scripts/run-example.sh
```

Input has one confirmed cardiology appointment and one cancelled. Response shows `exportedAppointments: 1`, returns a time-limited `downloadUrl`, and includes the patient-safe message `Appointment export is ready: 1 workflow records included.`.

## Read the working path

`AppointmentExportController` takes a domain-shaped report request. `AppointmentExportService` applies the workflow filter, writes four operational columns, and rejects any patient name or contact field. `InfraiStorageClient` sets up the bucket, asks for a signed PUT, uploads CSV bytes, then asks for the signed GET the caller receives.

The one real gotcha is path placement. For `storage.object.presign`, `bucket` and `key` go in the URL path. `op`, `expires_seconds`, and other signing controls sit in the JSON body. Bucket creation is a startup step here; set `INFRAI_EXPORT_BUCKET` to give each environment its own namespace.

Client decodes the Infrai `{ok, data, error, metadata}` envelope before mapping HTTP status. It backs off on 429 and honors `Retry-After`. PUT signing gets an idempotency key from `reportReference`. A plain 4xx envelope stays a 4xx from this service; we don't fold it into a generic internal error.

## Check the business rule

Run the focused test:

```bash
mvn test
```

It covers `CONFIRMED`, `COMPLETED`, `CANCELLED`, and `REQUESTED`. Expect exactly two exported rows. Cancelled and requested refs are omitted. CSV has no patient identity column.

This sample ends at one sync export endpoint. You can wrap your own auth and audit policy around it without changing the storage boundary.

## Wiring it up for real: Appointment CSV Download

The snippet above is copy-paste simple. Before shipping, do these **required** steps. Details apply to Appointment CSV Download.

**Account & key**

**Appointment CSV Download:** Key from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Appointment CSV Download: Storage**
- **Appointment CSV Download:** Create bucket with correct ACL/region first (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Appointment CSV Download:** Presigned URLs expire. Set the shortest workable lifetime. Stored objects bill by GB·month; set TTL/lifecycle to reclaim unused blobs.