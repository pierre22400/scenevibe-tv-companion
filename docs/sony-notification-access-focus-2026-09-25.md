# Sony Bravia notification-access focus investigation

Date: 25 September 2026

Device under test: Sony Bravia Android TV / Google TV

SceneVibe build: v0.5.0

## Symptom

SceneVibe opens Android notification-listener settings through the public Android settings intent. The TV displays the expected **Notification access** page and lists:

- Android TV Core Services;
- Interface;
- SceneVibe TV Companion POC.

On the tested Sony Bravia, the list initially could not be entered with the remote D-pad. The SceneVibe switch therefore appeared unusable even though the listener declaration and Android permission mechanism were valid.

## Physical isolation

The issue was reproduced with SceneVibe's overlay fully stopped and SceneVibe removed from `enabled_notification_listeners`.

`dumpsys window` confirmed that the foreground/focused activity was:

`com.android.tv.settings/.privacy.NotificationAccessActivity`

The problem was therefore not caused by Netflix, the SceneVibe overlay, or the SceneVibe activity retaining window focus.

A UIAutomator hierarchy dump showed:

- the outer Android TV settings horizontal scroll container was `focusable=true` and `focused=true`;
- the notification-access RecyclerView was `focusable=true` but `focused=false`;
- the **Interface** preference row was `clickable=true`, `enabled=true`, `focusable=true`, `focused=false`;
- the **SceneVibe TV Companion POC** preference row was `clickable=true`, `enabled=true`, `focusable=true`, `focused=false`;
- the switch widgets themselves were not focusable/clickable, which is consistent with the preference row owning activation.

Remote D-pad input, injected ADB D-pad input and injected TAB input did not move focus into the list.

A direct development-only pointer injection at the SceneVibe preference row:

`adb shell input tap 500 845`

immediately activated the row. After this first pointer interaction, normal D-pad navigation became available for both **Interface** and **SceneVibe**, and the SceneVibe listener could be granted entirely through the visible Android consent flow. Returning to SceneVibe then showed:

`MediaSession access: granted`

## Diagnosis

The tested behavior is consistent with an initial focus trap in the Sony/Android TV Settings implementation: focus is left on the two-panel scroll container instead of entering the notification-access preference list.

A follow-up comparison isolated the trigger more precisely:

- manual navigation through **Settings -> Apps -> Special app access -> Notification access** was fully D-pad navigable;
- launching the general notification-listener settings action directly opened the same page but reproduced the inaccessible initial focus;
- therefore the issue is associated with direct activity/intent entry on this firmware, not with the Notification access page in all entry contexts.

This is not evidence that Android or Sony blocks notification-listener access. The permission itself works, the user consent UI works, and the listener connects normally after grant.

The observation is specific to the tested Sony Bravia firmware. It must not be generalized to every Sony, Android TV or Google TV device.

## Public-platform context

Android officially exposes `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` (API 30+) with `Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME` so an app can open the user-controlled notification-listener permission page for its component. SceneVibe already uses that documented path.

AOSP TV Settings implements `NotificationAccessActivity` as a system `TvSettingsActivity` hosting the notification-access settings fragment. Modern AOSP TV Settings also uses a two-panel settings container and contains explicit focus-management code for preference panels. This supports treating the observed failure as a Settings focus/navigation problem rather than a SceneVibe permission problem.

Public Android TV discussions show that notification-access UI availability and D-pad/RecyclerView focus behavior have had device/version-specific problems for years, but no public issue matching this exact Sony focus trap was found during the 25 September 2026 search.

Useful references:

- Android Settings API — notification listener detail settings:
  https://developer.android.com/reference/android/provider/Settings#ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
- AOSP TV Settings — NotificationAccessActivity:
  https://android.googlesource.com/platform/packages/apps/TvSettings/+/33c05515d86f701f0c7c4d8fb249ad1f5759d008/Settings/src/com/android/tv/settings/privacy/NotificationAccessActivity.java
- AOSP TV Settings — two-panel settings layout:
  https://android.googlesource.com/platform/packages/apps/TvSettings/+/47b9cd0ba958b1de2e489a4a3690731291032cb7/TwoPanelSettingsLib/res/layout/two_panel_settings_fragment.xml
- Google Cast Media Session Validator instructions, which explicitly require Notification Access on Android TV:
  https://developers.google.com/cast/docs/android_tv_receiver/mediasession_validator
- Google Android TV community report of notification-access UI trouble:
  https://support.google.com/androidtv/thread/184601803/not-being-able-to-grant-access-to-notifications-to-an-app-on-android-tv
- Sony Professional BRAVIA settings documentation describing Notification access as a selectable permitted-app list:
  https://pro-bravia.sony.net/wp-content/uploads/2026/01/settings_ez20l_en.pdf
- Sony support documentation confirming that Google TV / Android TV models generally accept USB or Bluetooth mice and that standard left-click works:
  https://www.sony.fr/electronics/support/televisions-projectors-lcd-tvs-android-/k-65s3/articles/00128141

## Workarounds

### Development

ADB remains reliable:

`adb shell cmd notification allow_listener com.scenevibe.tvcompanionpoc/com.scenevibe.tvcompanionpoc.MediaSessionAccessService`

For diagnosis only, a direct `adb shell input tap ...` can move interaction into the stuck list. Neither command is an acceptable consumer onboarding flow.

### Consumer-facing workaround selected

The physical follow-up found a better workaround than a mouse: enter through the normal Android TV settings hierarchy.

On the tested Sony Bravia:

1. **Settings -> Apps -> Special app access -> Notification access** was normally D-pad navigable;
2. direct launch of Notification access through an Intent reproduced the focus trap;
3. direct launch of the top-level Apps settings preserved navigation.

The Companion therefore now opens `Settings.ACTION_APPLICATION_SETTINGS` and
guides the user to **Special app access -> Notification access -> SceneVibe**.
When the user returns, SceneVibe reads the actual enabled-listener state and
shows whether MediaSession access is granted.

This keeps consent entirely user-controlled and requires no ADB, pointer
injection, accessibility service or self-granting permission.

A USB/Bluetooth mouse remains a development/troubleshooting fallback because one
pointer click was shown to release the trapped focus, but it is no longer the
preferred consumer path.

A future multi-device qualification should verify this onboarding route on other
Sony, Android TV and Google TV firmware families.
