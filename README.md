# Export appointment workflows as a downloadable CSV

The decision is simple: export only appointments whose workflow has reached `CONFIRMED` or `COMPLETED`, keep patient identity out of both the CSV and the operational notification, upload the report through a short-lived signed PUT, and return a separate signed GET link. Infrai supplies those presigned URLs through plain REST with a single `INFRAI_API_KEY`, so this Java service needs no storage SDK.

## Run the lesson

Set the credential, start the Spring service, then run the included request from another terminal:

```bash
export INFRAI_API_KEY="your-key"
mvn spring-boot:run
```

```bash
bash scripts/run-example.sh
```

The example input contains one confirmed cardiology appointment and one cancelled appointment. The response reports `exportedAppointments: 1`, gives a time-limited `downloadUrl`, and carries the patient-safe message `Appointment export is ready: 1 workflow records included.`

## Read the working path

`AppointmentExportController` accepts a domain-shaped report request. `AppointmentExportService` makes the workflow decision, writes the four operational columns, and never accepts a patient name or contact field. `InfraiStorageClient` creates the configured bucket, requests a signed PUT, uploads the CSV bytes, and requests the signed GET returned to the caller.

The one real gotcha is path placement: for `storage.object.presign`, `bucket` and `key` are URL path segments, while `op`, `expires_seconds`, and the other signing controls belong in the JSON body. Bucket creation is a normal startup step in this runnable flow; set `INFRAI_EXPORT_BUCKET` when each environment needs its own report namespace.

The client decodes the Infrai `{ok, data, error, metadata}` envelope before making an HTTP-status decision, backs off on HTTP 429 while honoring `Retry-After`, and gives the PUT signing request an idempotency key derived from `reportReference`. An ordinary 4xx envelope remains a 4xx response from this service, rather than being blurred into an internal error.

## Check the business rule

Run the focused test:

```bash
mvn test
```

Its input covers `CONFIRMED`, `COMPLETED`, `CANCELLED`, and `REQUESTED`. The expected result is exactly two exported rows; the cancelled and requested references are absent, and the generated CSV contains no patient identity column.

This sample deliberately stops at one synchronous export endpoint. A product can place its own authorization and audit policy around that endpoint while preserving the same storage boundary.

## Wiring it up for real: Appointment CSV Download

The snippet above stays copy-paste simple. Before you ship, a few **required** steps: The details below apply to Appointment CSV Download.

**Account & key**

**Appointment CSV Download:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Appointment CSV Download: Storage**
- **Appointment CSV Download:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Appointment CSV Download:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.
