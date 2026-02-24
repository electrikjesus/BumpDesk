// Implementation of Shadertoy 4ljXWh (Sunrays/Godrays)
float getSunrays(vec3 pos, float time) {
    float rayCoord = pos.x + pos.z;
    float rays = sin(rayCoord * 0.5 + time * 0.5) * 0.5 + 0.5;
    rays *= sin(rayCoord * 1.2 - time * 0.3) * 0.5 + 0.5;
    rays *= sin(rayCoord * 2.5 + time * 0.8) * 0.5 + 0.5;

    float verticalFade = clamp(pos.y / 20.0, 0.0, 1.0);
    return pow(rays, 3.0) * verticalFade;
}

// Injected into applyWall(inout vec4 color, vec2 uv, vec3 pos, vec3 normal, float time)
float rays = getSunrays(pos, time * 0.02);
color.rgb += vec3(rays * 0.4);
float depthFactor = clamp((pos.y + 5.0) / 15.0, 0.0, 1.0);
color.rgb = mix(color.rgb, vec3(0.0, 0.25, 0.4), 0.15 * (1.0 - depthFactor));
