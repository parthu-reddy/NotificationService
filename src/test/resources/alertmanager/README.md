# Alertmanager webhook samples

`webhook-v4-firing.json` follows the webhook body Alertmanager sends (read in source on `main`, 2026-10-18):
`notify/webhook/webhook.go` (`Message`: embedded `template.Data`, `version: "4"`, `groupKey`, `truncatedAlerts`) and
`template/template.go` (`Data` and `Alert` JSON names). Labels and annotations mirror the `PartnerApplicationReviewOverdue`
alert firing on Dev on 2026-10-18; the fingerprint and times are illustrative. Re-check the shape against the
Alertmanager version Deployment pins (GatewayScrapeAndAlerting_2026-10-18 Phase 4).
