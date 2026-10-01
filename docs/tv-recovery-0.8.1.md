# SceneVibe TV 0.8.1 — recovery qualification

Date: 2026-10-01  
Branch: `work/tv-recovery-observability-001`  
Qualified head before this record: `e0e7225c3f8c10f0857be8ce3a6df1db563678b9`  
Physical device: Sony BRAVIA, Android TV 12

## Purpose

Close the recovery defect observed during the real 0.9D qualification: after local Cloud
credentials were deleted while the stable installation id survived, the Cloud correctly refused
a new activation because proof for the historical device no longer existed. Recovery previously
required Android **Clear data**.

The fix does not weaken device proof. The explicit destructive **Reset SceneVibe Cloud** action
rotates the local random installation id before deleting Cloud credentials/cache/runtime state.
The next activation is therefore a new TV identity. The historical server-side device remains
account-owned until explicitly removed.

## Automated qualification

GitHub Actions run 84 completed successfully on the recovery head:

- POC boundary tests: PASS
- debug build + lint + JVM tests: PASS
- LAN DEV build: PASS
- Cloud qualification APK build: PASS
- artifact upload: PASS
- stable-signing steps: skipped because signing secrets are not configured

Qualification APK: `0.8.1-tv-recovery`, versionCode 11.

## Physical qualification

1. Fresh qualification APK installed on the Sony. Both overlay and media-access permissions
   reported **Granted**. Cloud initially reported **Not connected**.
2. First Cloud activation/claim succeeded and created device
   `ff53466a-3525-4ef5-9180-df2f25fefce3`. The TV reported **Cloud connected**.
3. Diagnostics before reset showed installation id prefix `be8rvy7C…` and Cloud device prefix
   `ff53466a…`.
4. The explicit Diagnostics Cloud reset was executed. Android **Clear data** was NOT used.
5. Diagnostics immediately after reset showed installation id prefix `U7YfF-OT…`, no
   cloudDeviceId, and **Cloud disconnected**.
6. **Connect to SceneVibe Cloud** generated a new activation normally without reinstall,
   reboot, Clear data, or any device-proof bypass.
7. The second claim succeeded in the same account. A new device
   `5fa9c6fa-ffb0-422c-b233-7b96539bb3d5` appeared automatically in **My TVs** and the Sony
   reported **Cloud connected**.
8. Production runtime logs then showed the new physical TV polling
   `GET /api/v1/devices/5fa9c6fa-ffb0-422c-b233-7b96539bb3d5/assignment`, confirming that the
   recovered client had entered the normal authenticated Cloud polling path.

## Result

**PASS — recovery defect closed.**

The destructive reset is now self-recoverable without weakening proof-of-possession and without
requiring Android Clear data. Normal disconnect/restart/update behavior still preserves the
installation identity.

## Separate follow-ups

These are not blockers for the recovery patch and remain separate release-hardening work:

- configure and qualify stable Android signing;
- Android 15 / API 35 physical runtime qualification (the project already compiles/targets API 35);
- remove stale historical TV entries from the account through the existing account control plane;
- production Clerk live-instance/domain cutover before public release.

The FinalTrack assignment/ACK transport was not modified by this patch. Its physical Cloud
assignment/ACK path was qualified separately during 0.9D.
