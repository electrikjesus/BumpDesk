#define MAX_ITER 5
#define TAU 6.28318530718

float getCaustics(vec2 uv, float time) {
    vec2 p = mod(uv * TAU, TAU) - 250.0;
    vec2 i = vec2(p);
    float c = 1.0;
    float inten = .005;

    for (int n = 0; n < MAX_ITER; n++) {
        float t = time * (1.0 - (3.5 / float(n + 1)));
        i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x + t) / inten), p.y / (cos(i.y + t) / inten)));
    }
    c /= float(MAX_ITER);
    c = 1.17 - pow(c, 1.4);
    float colour = pow(abs(c), 8.0);
    return clamp(colour, 0.0, 1.0);
}

// Injected into applyFloor(inout vec4 color, vec2 uv, vec3 pos, float time)
float caustic = getCaustics(uv * 0.8, time * 0.01);
color.rgb += vec3(caustic * 0.7);
float depthFactor = clamp((pos.y + 5.0) / 15.0, 0.0, 1.0);
color.rgb = mix(color.rgb, vec3(0.0, 0.25, 0.4), 0.15 * (1.0 - depthFactor));
