//!HOOK OUTPUT
//!BIND HOOKED
//!SAVE MPVRX_FX_0
//!WIDTH 512
//!HEIGHT 288
//!DESC Ambient FX picture pyramid

// The original frame stays in HOOKED. Named SAVE passes only feed the light in the bars.
#ifdef GL_ES
precision highp float;
precision highp int;
#endif

// FX_CONFIG
// FX_DOWNSAMPLE_TAPS

vec4 hook() {
    vec3 c = vec3(0.0);
    vec2 half_texel = 0.5 / HOOKED_size;
    for (int i = 0; i < FX_SAMPLES; i++) {
        vec2 p = HOOKED_pos + FX_TAPS[i] / vec2(512.0, 288.0);
        c += HOOKED_tex(clamp(p, half_texel, 1.0 - half_texel)).rgb;
    }
    return vec4(c / float(FX_SAMPLES), 1.0);
}

//!HOOK OUTPUT
//!BIND MPVRX_FX_0
//!SAVE MPVRX_FX_1
//!WIDTH 256
//!HEIGHT 144
//!DESC Ambient FX pyramid level 1

vec4 hook() {
    return MPVRX_FX_0_tex(MPVRX_FX_0_pos);
}

//!HOOK OUTPUT
//!BIND MPVRX_FX_1
//!SAVE MPVRX_FX_2
//!WIDTH 128
//!HEIGHT 72
//!DESC Ambient FX pyramid level 2

vec4 hook() {
    return MPVRX_FX_1_tex(MPVRX_FX_1_pos);
}

//!HOOK OUTPUT
//!BIND MPVRX_FX_2
//!SAVE MPVRX_FX_3
//!WIDTH 64
//!HEIGHT 36
//!DESC Ambient FX pyramid level 3

vec4 hook() {
    return MPVRX_FX_2_tex(MPVRX_FX_2_pos);
}

//!HOOK OUTPUT
//!BIND MPVRX_FX_3
//!SAVE MPVRX_FX_4
//!WIDTH 32
//!HEIGHT 18
//!DESC Ambient FX pyramid level 4

vec4 hook() {
    return MPVRX_FX_3_tex(MPVRX_FX_3_pos);
}

//!HOOK OUTPUT
//!BIND MPVRX_FX_4
//!SAVE MPVRX_FX_5
//!WIDTH 16
//!HEIGHT 9
//!DESC Ambient FX pyramid level 5

vec4 hook() {
    return MPVRX_FX_4_tex(MPVRX_FX_4_pos);
}

//!HOOK OUTPUT
//!BIND MPVRX_FX_5
//!SAVE MPVRX_FX_6
//!WIDTH 8
//!HEIGHT 4
//!DESC Ambient FX pyramid level 6

vec4 hook() {
    return MPVRX_FX_5_tex(MPVRX_FX_5_pos);
}

//!HOOK OUTPUT
//!BIND HOOKED
//!BIND MPVRX_FX_0
//!BIND MPVRX_FX_1
//!BIND MPVRX_FX_2
//!BIND MPVRX_FX_3
//!BIND MPVRX_FX_4
//!BIND MPVRX_FX_5
//!BIND MPVRX_FX_6
//!DESC Ambient FX Glow / Mirror

// Spatial effects adapted from the supplied fx_ambient.frag and fx_common.glsl.
#ifdef GL_ES
precision highp float;
precision highp int;
#endif

// FX_CONFIG

float fx_luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

vec3 fx_vibrance(vec3 c, float k) {
    float l = fx_luma(c);
    return max(vec3(l) + (c - vec3(l)) * k, vec3(0.0));
}

vec3 fx_rolloff(vec3 c, float k) { return c / (1.0 + k * fx_luma(c)); }

// Four bilinear taps implement the bundle's C2-smooth cubic B-spline filter.
// The named mpv textures are explicit levels, so no Vulkan-only mip/query API is needed.
vec3 fx_bspline(sampler2D tex, vec2 size, vec2 uv) {
    vec2 st = uv * size - 0.5;
    vec2 i = floor(st);
    vec2 f = st - i;
    vec2 f2 = f * f;
    vec2 f3 = f2 * f;
    vec2 w0 = (-f3 + 3.0 * f2 - 3.0 * f + 1.0) / 6.0;
    vec2 w1 = (3.0 * f3 - 6.0 * f2 + 4.0) / 6.0;
    vec2 w2 = (-3.0 * f3 + 3.0 * f2 + 3.0 * f + 1.0) / 6.0;
    vec2 w3 = f3 / 6.0;
    vec2 g0 = w0 + w1;
    vec2 g1 = w2 + w3;
    vec2 lo = 0.5 / size;
    vec2 hi = 1.0 - lo;
    vec2 p0 = clamp((i + w1 / g0 - 0.5) / size, lo, hi);
    vec2 p1 = clamp((i + w3 / g1 + 1.5) / size, lo, hi);
    vec3 c00 = textureLod(tex, vec2(p0.x, p0.y), 0.0).rgb;
    vec3 c10 = textureLod(tex, vec2(p1.x, p0.y), 0.0).rgb;
    vec3 c01 = textureLod(tex, vec2(p0.x, p1.y), 0.0).rgb;
    vec3 c11 = textureLod(tex, vec2(p1.x, p1.y), 0.0).rgb;
    return (c00 * g0.x + c10 * g1.x) * g0.y + (c01 * g0.x + c11 * g1.x) * g1.y;
}

vec3 fx_level(vec2 uv, int level) {
    if (level == 0) return fx_bspline(MPVRX_FX_0_raw, MPVRX_FX_0_size, uv) * MPVRX_FX_0_mul;
    if (level == 1) return fx_bspline(MPVRX_FX_1_raw, MPVRX_FX_1_size, uv) * MPVRX_FX_1_mul;
    if (level == 2) return fx_bspline(MPVRX_FX_2_raw, MPVRX_FX_2_size, uv) * MPVRX_FX_2_mul;
    if (level == 3) return fx_bspline(MPVRX_FX_3_raw, MPVRX_FX_3_size, uv) * MPVRX_FX_3_mul;
    if (level == 4) return fx_bspline(MPVRX_FX_4_raw, MPVRX_FX_4_size, uv) * MPVRX_FX_4_mul;
    if (level == 5) return fx_bspline(MPVRX_FX_5_raw, MPVRX_FX_5_size, uv) * MPVRX_FX_5_mul;
    return fx_bspline(MPVRX_FX_6_raw, MPVRX_FX_6_size, uv) * MPVRX_FX_6_mul;
}

vec3 fx_bspline_f(vec2 uv, float lod) {
    lod = clamp(lod, 0.0, 5.999);
    int l0 = int(lod);
    return mix(fx_level(uv, l0), fx_level(uv, l0 + 1), lod - float(l0));
}

float fx_ign(vec2 p) {
    return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
}

vec3 fx_dither(vec3 c, vec2 p) {
    float amp = clamp(max(c.r, max(c.g, c.b)) * 255.0, 0.0, 1.0);
    return max(c + (fx_ign(p) - 0.5) * (amp / 255.0), vec3(0.0));
}

vec4 hook() {
    vec2 uv = HOOKED_pos;
    vec2 screen = HOOKED_size;
    vec2 size = screen / vec2(SCALE_X, SCALE_Y);
    vec2 rect_min = (screen - size) * 0.5;
    vec2 rect_max = rect_min + size;
    vec2 p = uv * screen;
    vec2 video_uv = (p - rect_min) / size;
    vec2 half_texel = 0.5 / screen;
    vec2 safe_uv = clamp(video_uv, half_texel, 1.0 - half_texel);
    bool inside = all(greaterThanEqual(video_uv, vec2(0.0))) &&
                  all(lessThanEqual(video_uv, vec2(1.0)));
    float video_weight = 0.0;
    if (inside) {
        float blend_width = EDGE_BLEND * min(size.x, size.y);
        float inside_dist = blend_width;
        if (SCALE_X > 1.0) inside_dist = min(inside_dist, min(video_uv.x, 1.0 - video_uv.x) * size.x);
        if (SCALE_Y > 1.0) inside_dist = min(inside_dist, min(video_uv.y, 1.0 - video_uv.y) * size.y);
        // No shading or downsampling of the film, except for the user's optional seam blend.
        if (blend_width <= 0.0 || inside_dist >= blend_width) return HOOKED_tex(safe_uv);
        video_weight = smoothstep(0.0, blend_width, inside_dist);
    }

    vec2 q = clamp(p, rect_min, rect_max);
    float d = length(p - q);
    float tx = p.x < rect_min.x ? rect_min.x : (p.x > rect_max.x ? screen.x - rect_max.x : 0.0);
    float ty = p.y < rect_min.y ? rect_min.y : (p.y > rect_max.y ? screen.y - rect_max.y : 0.0);
    float thick = max(max(tx, ty), 1.0);
    bool top_bottom = ty >= tx;
    vec3 c;
    float fade;

#if FX_MODE == 4
    // MIRROR: fold the picture at each edge, then soften and darken the reflection.
    float reach = min(thick, thick * mix(0.5, 1.25, REACH));
    float dn = clamp(d / reach, 0.0, 1.0);
    vec2 reflected = 1.0 - abs(mod(video_uv, 2.0) - 1.0);
    c = fx_rolloff(fx_vibrance(fx_bspline_f(reflected, mix(1.0, 4.6, pow(dn, 0.7))), 0.92 * SAT_BOOST), 0.3);
    fade = pow(1.0 - dn, 2.0 * FADE_CURVE) * (0.55 + 0.45 * exp(-6.0 * dn));
    if (p.y < rect_min.y) fade *= 0.7;
    fade *= INTENSITY * 0.5;
#else
    // GLOW: light comes from a progressively wider and deeper strip of the nearest edge.
    float reach = min(thick, thick * mix(0.45, 1.3, REACH));
    float dn = clamp(d / reach, 0.0, 1.0);
    vec2 source_uv = mix((q - rect_min) / size, vec2(0.5), 0.03 + 0.10 * dn);
    float span = top_bottom ? size.x : size.y;
    float texels = top_bottom ? MPVRX_FX_3_size.x : MPVRX_FX_3_size.y;
    float lod = log2(max((18.0 + 0.85 * d) / (span / texels), 1.0));
    // Level 3 is the supplied 64x36 Glow source; the saved levels replace its mip chain.
    c = fx_rolloff(fx_vibrance(fx_bspline_f(source_uv, 3.0 + lod), 1.18 * SAT_BOOST), 0.55);
    fade = pow(1.0 - dn, 2.4 * FADE_CURVE) * (0.6 + 0.4 * exp(-5.0 * dn));
    fade *= INTENSITY;
#endif

    // Multiplicative warmth keeps a black frame black, even with a warm/cool preference.
    c *= max(vec3(0.0), vec3(1.0) + WARMTH * vec3(0.12, 0.05, -0.16));
    float vig_r = length(uv - 0.5) * 2.0;
    float vignette = 1.0 - smoothstep(0.1, 1.3, vig_r);
    c *= fade * mix(1.0, vignette, VIGNETTE_STR) * OPACITY;
    // OUTPUT is already in the target color space; mpv performs any final sRGB encoding.
    vec4 ambient = vec4(fx_dither(c, p), 1.0);
    return inside ? mix(ambient, HOOKED_tex(safe_uv), video_weight) : ambient;
}
