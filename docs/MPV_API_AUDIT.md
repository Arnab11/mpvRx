# MPV integration review — 2026-10-09

Static review of the app-owned MPV calls, including the session wrapper, startup,
surface handoff, playback service, seeking, track/subtitle controls, audio/video
filters, HDR/shaders, screenshots, statistics/console, and generated script bridge.
No build, compilation, test cases, or device playback were run for this change.

## YTDL regression and restoration

Compared with tag `v2.7.2` (`9d5a27c2`), the bridge environment and extractor
preferences had moved out of core startup. A later change configured the hook
only at startup, while local/direct playback still set `ytdl=no`. That option
unloads the built-in Lua script; the following web load no longer enabled it.
The supplied failure log also showed fallback attempts to execute `yt-dlp`,
`yt-dlp_x86`, and `youtube-dl` instead of the bundled `libytdl.so`.

- Restore the 2.7.2 startup configuration of Python, TLS, QuickJS and yt-dlp options.
- Keep the hook enabled between media loads; its URL checks/exclusions handle
  local files and direct streams.
- Refresh the bundled hook options before web playback through `change-list
  script-opts append`, preserving unrelated script options and allowing the
  hook's option observer to reset executable discovery.
- Remove the unsupported `ytdl_hook-user_agent` option. The user agent is supplied
  through MPV's `user-agent` and yt-dlp's raw options.
- Preserve the selected format and the existing video-selection fixes. Native
  `ytdl_wrapper.c`, installer `setup.py`, and `YtdlpOptions.kt` already match
  v2.7.2 and do not need a rollback.

## Confirmed call-site corrections

| Area | Correction |
| --- | --- |
| Native lifecycle | Commands and runtime property access require an initialized core; pre-init option/observer setup remains available. |
| Runtime options | Integration writes use properties after init. `script-opts-append` uses the list command, not a nonexistent runtime property. |
| Surface control | Runtime `force-window`, `idle`, and `vo` writes use properties. Read `wid` without the JNI Int accessor's 32-bit truncation. |
| Integer properties | The app's Long accessor no longer widens an already truncated Int. |
| Startup policy | Set keep-open, seek policy, logging, input defaults, and screenshot directory before the first loadfile. |
| Statistics | Apply the startup toggle once per native core, not on every replacement load. |
| Console | Open with the registered `console/enable` script binding; there is no `console` script message named `enable`. |
| HTTP defaults | Refresh the stored default user agent when extractor preferences change, so later headerless items restore the current value. |

Other reviewed calls retain their supported syntax: loadfile options follow the
index argument; seeks use seconds and valid flags; track disabling uses `no`;
subtitle delays/speed and scales use numeric properties; shader lists use
`change-list`; screenshots use `screenshot-to-file`; generated script messages
match the app's registered handlers. Integer setters for floating-point options
are supported by libmpv's numeric conversion. HDR/ambient `memory://` config
loads and fixed-length quoted values are supported by the native config parser.

User-authored Lua/JavaScript, input bindings and mpv.conf remain user code. Native
decoder/renderer behavior and extraction on current websites require device
validation; this review does not claim runtime success.

## Failed release job

Job `113962103190` failed at `minifyStandardReleaseWithR8` because SMBJ-RPC refers
to `java.rmi.UnmarshalException`, unavailable on Android. The packaging visitor
remaps those dependency references to `SmbRpcDecodeException`, an IOException
with matching constructors. This preserves catchable decode failures without
adding a missing-class suppression or removing optional-path SMB share browsing.

## References

- [Failed job](https://github.com/Riteshp2001/mpvRx/actions/runs/37972249853/job/113962103190)
- [libmpv client API](https://github.com/mpv-player/mpv/blob/v0.41.0/include/mpv/client.h)
- [MPV command/property manual](https://mpv.io/manual/master/)
- [Built-in hook and script sources reviewed](https://github.com/mpv-player/mpv/tree/b2c255c13e8e37952dbac6c34da56a690560378b/player/lua)
- [Android JNI property implementation reviewed](https://github.com/Riteshp2001/mpvlibAndroid/blob/14bd675e181d7de915dcadb1f5b82c888440f970/app/src/main/jni/property.cpp)
- [AGP ASM instrumentation API](https://developer.android.com/reference/tools/gradle-api/8.13/com/android/build/api/instrumentation/AsmClassVisitorFactory)
