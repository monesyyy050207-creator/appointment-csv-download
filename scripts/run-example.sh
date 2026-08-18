#!/usr/bin/env bash
set -euo pipefail

curl --request POST "http://localhost:8080/appointment-exports" \
  --header "Content-Type: application/json" \
  --data '{
    "reportReference": "clinic-day-2026-08-20",
    "appointments": [
      {"appointmentReference":"APT-201","scheduledAt":"2026-08-20T09:30:00Z","serviceLine":"cardiology","status":"CONFIRMED"},
      {"appointmentReference":"APT-202","scheduledAt":"2026-08-20T10:00:00Z","serviceLine":"cardiology","status":"CANCELLED"}
    ]
  }'
