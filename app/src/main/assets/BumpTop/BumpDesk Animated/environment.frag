// Procedural Caustics (from Shadertoy wXVSzt)
float getCaustics(vec3 pos, float time) {
    vec3 p = pos * 0.15;
    float t = time * 0.02;
    vec3 s = p + vec3(sin(t), cos(t), sin(t * 0.5));
    float c = 0.0;
    for(int n=1; n<4; n++) {
        float i = float(n);
        s += cos(s.zxy + vec3(t, t * 1.3, t * 1.7)) / i;
        c += length(vec2(cos(s.x), sin(s.y))) / i;
    }
    return pow(c * 0.25, 4.0);
}

void applyEnvironment(inout vec4 baseColor, vec3 fPosition, vec3 fNormal, float uTime) {
    // Apply caustics using WORLD position for seamless environment mapping
    float caustic = getCaustics(fPosition, uTime);
    baseColor.rgb += vec3(caustic * 0.6);

    // Static underwater depth tinting
    float depthFactor = clamp((fPosition.y + 5.0) / 20.0, 0.0, 1.0);
    baseColor.rgb = mix(baseColor.rgb, vec3(0.0, 0.15, 0.3), 0.3 * (1.0 - depthFactor));
}
