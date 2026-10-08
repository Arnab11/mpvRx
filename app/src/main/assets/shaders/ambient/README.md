# Ambient FX

`fx_ambient.glsl` adapts **Glow** and **Mirror** from the supplied `fx_ambient.frag`
and `fx_common.glsl` in `fx_ambient.zip`. `AmbientShaderBuilder` fills the compile-time
configuration and sample table before loading it through mpv's `glsl-shaders`.

- Glow uses edge color, distance-dependent B-spline blur, highlight roll-off, and
  the FX emitter falloff. Mirror folds the frame across its edges, progressively
  blurs the reflection, and dims the top reflection.
- Seven named `SAVE` passes form a 512x288-to-8x4 picture pyramid. They replace the
  native Vulkan mip textures using mpv hooks supported by OpenGL and gpu-next.
  The quality/battery/thermal sample budget applies to the initial downsample;
  each bar pixel uses two four-tap B-spline samples, independent of that budget.
- Only the final OUTPUT pass remaps the original picture; the picture bypasses
  the effect except within the explicitly configured edge-blend strip. All color
  grading, roll-off, vignette and dithering apply to the ambient area.
- Spread maps the existing 0.05–0.80 slider to the FX reach control. Both modes
  share the existing effect controls, with separately persisted edge blending.
- The port uses current-frame spatial filtering. The bundle's native `fx_ema.comp`
  temporal smoothing and Echo ring buffer require persistent renderer resources;
  mpv's saved hook textures are not previous-frame history. Echo, Cinema, and
  the unrelated Enhance pass are not installed. **YouTube's existing capture,
  smoothing, presentation, and preferences are unchanged.**
- mpv owns the target color-space conversion. The Vulkan swapchain's `SRGB_TARGET`
  conversion is not repeated in this OUTPUT hook.

## Verification

With Mesa EGL and Python/numpy available:

```sh
python tools/check_ambient_fx.py
python tools/check_ambient_fx.py --gles
```

These checks compile and render the actual template passes, including minimum
and maximum sample budgets. They check picture preservation, portrait/landscape
bars, corners, matching aspect ratios, black frames, opacity, seam blending, and
falloff. They do not replace an Android build or on-device playback checks.

On Android, test Glow → Mirror → YouTube → Off while playing; rotate, reopen the
player with Mirror selected, and change a slider. Check that one ambient entry
remains in `glsl-shaders`, other user/HDR shaders remain present, and returning
to YouTube/Off restores the fitted picture. Also check PiP and audio-only playback.
