// Immersive Underwater Environment Shader
#define CUSTOM_ENVIRONMENT
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

void applyEnvironment(inout vec4 baseColor, vec3 fPosition, vec3 fNormal, float uTime) {
    // Only apply liquid distortion to the floor (Y-up normal)
    bool isFloor = fNormal.y > 0.5;

    // Apply caustics using 3D world mapping
    vec2 mappingUV = isFloor ? fPosition.xz : (abs(fNormal.x) > 0.5 ? fPosition.zy : fPosition.xy);
    float caustic = getCaustics(mappingUV * 0.1, uTime * 0.01);
    baseColor.rgb += vec3(caustic * 0.6);

    // Static underwater depth tinting (deeper = darker blue)
    float depthFactor = clamp((fPosition.y + 5.0) / 20.0, 0.0, 1.0);
    baseColor.rgb = mix(baseColor.rgb, vec3(0.0, 0.15, 0.3), 0.3 * (1.0 - depthFactor));
}
