# SceneVibe OS — M1 UI application boundary

The Sony qualification of revision 4 used TV commit
6206d07272de3da79715833318146fcb947082d2. Its physical evidence remains valid
for that candidate; it does not qualify a later APK automatically.

The closure audit found a separate live-update defect: CloudControlClient polls
on a ScheduledExecutorService, but installation calls scheduler.load and the
regie's eligibility/retirement callbacks. Those callbacks may touch an existing
SceneRenderer window and its fade animator. Android requires their owner thread.

AssignmentMutationGate now applies the complete installation and visual-owner
transition on the existing Android main Looper. Network fetch and ACK remain on
Cloud io. The caller waits for completion before ACK; stopped/interrupt-cancelled
queued work cannot install an obsolete revision. Explicit reset also clears the
scheduler through the owner boundary. No media clock, route, permission, manifest
or FinalTrack contract changes.

The manifested installer synchronously retires any legacy window before selecting
the manifested revision. SceneRenderer and OverlayRenderer remain in place.

AssignmentMutationGateTest drives the real Cloud application core, cache,
scheduler and regie with a window-owner-checking sink. It covers visible revision
replacement, manifested -> legacy, redelivery, wait-before-ACK, owner re-entry,
failure/rejection, queued cancellation and stopped-client protection. Android
window teardown itself still requires physical verification.

Before a merge decision for the new HEAD, install its stable-signed APK with
adb install -r, send a newer manifested assignment while a Columbo scene is
visible, check durable revision/ACK and single native rendering, then check
pause/resume, context loss/return and hard reboot restoration without a new Send
or manual Start SceneVibe. Record exact APK hash and revision.

The InputManager warning is retained for diagnosis. The audit has not established
that this warning's particular Sony occurrence was caused by the thread defect.
Do not suppress it or infer a leak or double rendering from the warning alone.
No M2 implementation is included in this correction.
