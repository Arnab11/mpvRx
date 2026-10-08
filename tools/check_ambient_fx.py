#!/usr/bin/env python3
"""Compile/render the shipped ambient template with Mesa EGL (pip install numpy).

Runs without Android or a window server. Use --gles to exercise GLSL ES 3 as well.
The harness supplies mpv's documented BIND/SAVE texture macros, renders every pass,
and checks the final pixels, rather than merely searching generated shader text.
"""
import ctypes as C
import math
import os
from pathlib import Path
import re
import sys

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / 'app/src/main/assets/shaders/ambient/fx_ambient.glsl'
ES = '--gles' in sys.argv
os.environ.setdefault('EGL_PLATFORM', 'surfaceless')
I, U, P, F = C.c_int, C.c_uint, C.c_void_p, C.c_float
EGL = C.CDLL('libEGL.so.1')


def egl(name, result, *args):
    function = getattr(EGL, name)
    function.restype, function.argtypes = result, args
    return function


display = egl('eglGetDisplay', P, P)(None)
major, minor = I(), I()
assert egl('eglInitialize', U, P, P, P)(display, C.byref(major), C.byref(minor))
assert egl('eglBindAPI', U, U)(0x30A0 if ES else 0x30A2)
attrs = (I * 7)(0x3033, 1, 0x3040, 0x40 if ES else 8, 0x3024, 8, 0x3038)
config, count = P(), I()
assert egl('eglChooseConfig', U, P, P, P, I, P)(display, attrs, C.byref(config), 1, C.byref(count))
assert count.value
surface = egl('eglCreatePbufferSurface', P, P, P, P)(display, config, (I * 5)(0x3057, 16, 0x3056, 16, 0x3038))
context_attrs = (I * 3)(0x3098, 3, 0x3038) if ES else (I * 7)(0x3098, 3, 0x30FB, 3, 0x30FD, 1, 0x3038)
context = egl('eglCreateContext', P, P, P, P, P)(display, config, None, context_attrs)
assert context
assert egl('eglMakeCurrent', U, P, P, P, P)(display, surface, surface, context)
get_proc = egl('eglGetProcAddress', P, C.c_char_p)


def gl(name, result, *args):
    pointer = get_proc(name.encode())
    assert pointer, name
    return C.CFUNCTYPE(result, *args)(pointer)


get_string = gl('glGetString', C.c_char_p, U)
print(get_string(0x1F02).decode(), '/', get_string(0x1F01).decode())
create_shader = gl('glCreateShader', U, U)
shader_source = gl('glShaderSource', None, U, I, P, P)
compile_shader = gl('glCompileShader', None, U)
get_shader_iv = gl('glGetShaderiv', None, U, U, P)
get_shader_log = gl('glGetShaderInfoLog', None, U, I, P, P)
create_program = gl('glCreateProgram', U)
attach_shader = gl('glAttachShader', None, U, U)
link_program = gl('glLinkProgram', None, U)
get_program_iv = gl('glGetProgramiv', None, U, U, P)
get_program_log = gl('glGetProgramInfoLog', None, U, I, P, P)
use_program = gl('glUseProgram', None, U)
delete_shader = gl('glDeleteShader', None, U)
delete_program = gl('glDeleteProgram', None, U)
gen_textures = gl('glGenTextures', None, I, P)
bind_texture = gl('glBindTexture', None, U, U)
tex_parameter = gl('glTexParameteri', None, U, U, I)
tex_image = gl('glTexImage2D', None, U, I, I, I, I, I, U, U, P)
active_texture = gl('glActiveTexture', None, U)
delete_textures = gl('glDeleteTextures', None, I, P)
uniform_location = gl('glGetUniformLocation', I, U, C.c_char_p)
uniform_1i = gl('glUniform1i', None, I, I)
uniform_2f = gl('glUniform2f', None, I, F, F)
gen_framebuffers = gl('glGenFramebuffers', None, I, P)
bind_framebuffer = gl('glBindFramebuffer', None, U, U)
framebuffer_texture = gl('glFramebufferTexture2D', None, U, U, U, U, I)
check_framebuffer = gl('glCheckFramebufferStatus', U, U)
viewport = gl('glViewport', None, I, I, I, I)
draw = gl('glDrawArrays', None, U, I, I)
read_pixels = gl('glReadPixels', None, I, I, I, I, U, U, P)
get_error = gl('glGetError', U)
vao, fbo = U(), U()
gl('glGenVertexArrays', None, I, P)(1, C.byref(vao))
gl('glBindVertexArray', None, U)(vao)
gen_framebuffers(1, C.byref(fbo))
bind_framebuffer(0x8D40, fbo)
PREFIX = '#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n' if ES else '#version 330 core\n'


def shader(kind, text):
    handle = create_shader(kind)
    source = C.c_char_p(text.encode())
    shader_source(handle, 1, C.byref(source), None)
    compile_shader(handle)
    status = I()
    get_shader_iv(handle, 0x8B81, C.byref(status))
    if not status.value:
        log = C.create_string_buffer(16384)
        get_shader_log(handle, len(log), None, log)
        raise AssertionError(log.value.decode() + '\n' + text)
    return handle


VERTEX = shader(0x8B31, PREFIX + '''out vec2 texcoord;
void main() {
    vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
    texcoord = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
''')


def texture(width, height, pixels=None):
    handle = U()
    gen_textures(1, C.byref(handle))
    bind_texture(0x0DE1, handle)
    for key, value in [(0x2801, 0x2601), (0x2800, 0x2601), (0x2802, 0x812F), (0x2803, 0x812F)]:
        tex_parameter(0x0DE1, key, value)
    tex_image(0x0DE1, 0, 0x8814, width, height, 0, 0x1908, 0x1406, None if pixels is None else pixels.ctypes.data)
    return handle.value, width, height


def shader_text(mode, sx, sy, edge=0.0, opacity=1.0, samples=12, reach=0.6):
    values = dict(FX_SAMPLES=samples, FX_MODE=mode, SCALE_X=sx, SCALE_Y=sy, REACH=reach,
                  INTENSITY=1.2, SAT_BOOST=1.0, WARMTH=0.4, FADE_CURVE=1.0,
                  VIGNETTE_STR=0.5, OPACITY=opacity, EDGE_BLEND=edge)
    config = '\n'.join(f'#define {key} {value}' for key, value in values.items())
    taps = []
    for index in range(samples):
        radius = math.sqrt((index + 0.5) / samples) * 0.70710678
        theta = (index + 0.5) * 2.399963229728653
        taps.append(f'vec2({math.cos(theta) * radius:.8f}, {math.sin(theta) * radius:.8f})')
    table = f'const vec2 FX_TAPS[{samples}] = vec2[{samples}](' + ','.join(taps) + ');'
    return TEMPLATE.read_text().replace('// FX_CONFIG', config).replace('// FX_DOWNSAMPLE_TAPS', table)


def render(pixels, text):
    height, width, _ = pixels.shape
    textures = {'HOOKED': texture(width, height, pixels)}
    allocated = [textures['HOOKED'][0]]
    stages = re.split(r'(?=^//!HOOK OUTPUT)', text, flags=re.M)
    stages = [stage for stage in stages if stage.strip()]
    assert len(stages) == 8
    for stage in stages:
        bindings = re.findall(r'^//!BIND (\w+)', stage, re.M)
        save = re.search(r'^//!SAVE (\w+)', stage, re.M)
        size_w = re.search(r'^//!WIDTH (\d+)', stage, re.M)
        size_h = re.search(r'^//!HEIGHT (\d+)', stage, re.M)
        out_w, out_h = (int(size_w[1]), int(size_h[1])) if save else (width, height)
        declarations = 'in vec2 texcoord;\nout vec4 color;\n'
        for name in bindings:
            assert name in textures, f'Missing input {name}'
            declarations += f'''uniform sampler2D {name}_raw;
uniform vec2 {name}_size;
#define {name}_mul 1.0
#define {name}_pos texcoord
#define {name}_pt (1.0 / {name}_size)
#define {name}_tex(p) textureLod({name}_raw, p, 0.0)
'''
        fragment = shader(0x8B30, PREFIX + declarations + stage + '\nvoid main() { color = hook(); }\n')
        program = create_program()
        attach_shader(program, VERTEX)
        attach_shader(program, fragment)
        link_program(program)
        status = I()
        get_program_iv(program, 0x8B82, C.byref(status))
        if not status.value:
            log = C.create_string_buffer(16384)
            get_program_log(program, len(log), None, log)
            raise AssertionError(log.value.decode())
        use_program(program)
        # Allocate before binding samplers: texture() binds on the current active unit.
        output = texture(out_w, out_h)
        allocated.append(output[0])
        for unit, name in enumerate(bindings):
            handle, tw, th = textures[name]
            active_texture(0x84C0 + unit)
            bind_texture(0x0DE1, handle)
            uniform_1i(uniform_location(program, (name + '_raw').encode()), unit)
            uniform_2f(uniform_location(program, (name + '_size').encode()), tw, th)
        framebuffer_texture(0x8D40, 0x8CE0, 0x0DE1, output[0], 0)
        assert check_framebuffer(0x8D40) == 0x8CD5
        viewport(0, 0, out_w, out_h)
        draw(0x0004, 0, 3)
        assert get_error() == 0
        textures[save[1] if save else 'OUTPUT'] = output
        delete_shader(fragment)
        delete_program(program)
    result = np.empty_like(pixels)
    read_pixels(0, 0, width, height, 0x1908, 0x1406, result.ctypes.data)
    assert get_error() == 0
    delete_textures(len(allocated), (U * len(allocated))(*allocated))
    assert np.isfinite(result).all()
    return result


def reference(pixels, sx, sy):
    h, w, _ = pixels.shape
    y, x = np.mgrid[:h, :w]
    u, v = ((x + .5) / w - .5) * sx + .5, ((y + .5) / h - .5) * sy + .5
    inside = (u >= 0) & (u <= 1) & (v >= 0) & (v <= 1)
    xx, yy = np.clip(u * w - .5, 0, w - 1), np.clip(v * h - .5, 0, h - 1)
    x0, y0 = xx.astype(int), yy.astype(int)
    x1, y1 = np.minimum(x0 + 1, w - 1), np.minimum(y0 + 1, h - 1)
    a, b = (xx - x0)[..., None], (yy - y0)[..., None]
    expected = (pixels[y0, x0] * (1-a) + pixels[y0, x1] * a) * (1-b) + (pixels[y1, x0] * (1-a) + pixels[y1, x1] * a) * b
    return inside, expected


def check():
    for name, w, h, sx, sy in [('pillarbox', 320, 180, 1.5, 1.0), ('letterbox', 160, 288, 1.0, 3.2),
                               ('corners', 240, 180, 1.7, 1.5), ('no-bars', 256, 144, 1.0, 1.0)]:
        y, x = np.mgrid[:h, :w]
        source = np.empty((h, w, 4), np.float32)
        source[..., 0] = (x + .5) / w
        source[..., 1] = (y + .5) / h
        source[..., 2] = .2
        source[..., 3] = 1
        inside, expected = reference(source, sx, sy)
        results = []
        for mode in [1, 4]:
            result = render(source, shader_text(mode, sx, sy))
            np.testing.assert_allclose(result[inside], expected[inside], atol=2e-5)
            if (~inside).any():
                assert result[~inside, :3].max() > .01, (name, mode, 'unlit bars')
            black = source.copy()
            black[..., :3] = 0
            dark = render(black, shader_text(mode, sx, sy, edge=.1))
            assert np.max(np.abs(dark[..., :3])) < 1e-7, 'Black frame was lifted'
            hidden = render(source, shader_text(mode, sx, sy, opacity=0.0, samples=5))
            np.testing.assert_allclose(hidden[inside], expected[inside], atol=2e-5)
            assert np.max(np.abs(hidden[~inside, :3]), initial=0) < 1e-7
            blended = render(source, shader_text(mode, sx, sy, edge=.1, samples=64, reach=1.0))
            np.testing.assert_allclose(blended[h//2, w//2], expected[h//2, w//2], atol=2e-5)
            # Uniform source: falloff is monotonic and reaches true black before the screen edge.
            if name in ['pillarbox', 'letterbox']:
                solid = np.full_like(source, .5)
                solid[..., 3] = 1
                falloff = render(solid, shader_text(mode, sx, sy))
                line = falloff[h//2, :w//2, :3].mean(axis=1) if sx > 1 else falloff[:h//2, w//2, :3].mean(axis=1)
                bar = line[:int((w * (1 - 1/sx) if sx > 1 else h * (1 - 1/sy)) / 2)]
                assert abs(bar[0]) < 1e-7, 'Outer bar did not fade to black'
                assert np.min(np.diff(bar), initial=0) > -.004, 'Brightness increased toward the screen edge'
            results.append(result)
        if (~inside).any():
            assert np.max(np.abs(results[0] - results[1])) > .01, 'Modes look identical'
        print(f'PASS {name}: Glow/Mirror, picture preservation, black, opacity, edge blend, quality bounds')
    print('Ambient FX shader rendering checks passed.')


if __name__ == '__main__':
    check()
