#version 150
uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FoilTime;
uniform vec2 FoilTilt;
uniform float FoilTier;
uniform float FoilAlpha;
in vec2 cardUV;
in vec4 vertexColor;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(41.31, 289.17))) * 9512.173);
}
void main() {
    vec2 uv = cardUV;
    vec2 q = abs(uv - 0.5) - vec2(0.466, 0.476);
    float edge = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.024;
    float mask = 1.0 - smoothstep(-0.0015, 0.0015, edge);
    if (mask < 0.01) discard;
    vec4 base = texture(Sampler0, uv);
    float motion = min(1.0, length(FoilTilt));
    vec2 lightPos = vec2(0.42, 0.32) + FoilTilt.yx * vec2(0.32, -0.32);
    float specular = exp(-dot((uv - lightPos) * vec2(2.0, 1.3), (uv - lightPos) * vec2(2.0, 1.3)) * 13.0);
    float rim = 1.0 - smoothstep(0.0, 0.025, min(min(uv.x, 1.0-uv.x), min(uv.y, 1.0-uv.y)));
    vec3 color = base.rgb * (0.97 + 0.03 * (1.0 - length(uv - 0.5)));
    color += specular * 0.055;
    if (FoilTier > 0.5) {
        color += vec3(0.78, 0.86, 1.0) * (specular * 0.09 + rim * 0.06);
    }
    if (FoilTier > 1.5) {
        // Rare cards confine the foil to the illustration; full-art tiers use the full surface.
        float artMask = smoothstep(0.13, 0.17, uv.y) * (1.0 - smoothstep(0.48, 0.53, uv.y));
        float coverage = FoilTier < 2.5 ? artMask : 1.0;
        float phase = uv.x * 2.3 + uv.y * 1.4 + dot(FoilTilt, vec2(0.7, -0.9));
        vec3 rainbow = 0.5 + 0.5 * cos(6.2831853 * (phase + vec3(0.0, 0.333, 0.667)));
        float sweep = pow(0.5 + 0.5 * sin((uv.x - uv.y * 0.65 + FoilTilt.y * 0.5) * 10.0 + FoilTime * 0.75), 10.0);
        // Screen blend preserves the printed artwork rather than replacing it with a rainbow.
        vec3 foil = rainbow * (0.07 + 0.18 * motion + 0.12 * sweep) * coverage;
        color = 1.0 - (1.0 - color) * (1.0 - foil);
        color += specular * 0.13 + rim * rainbow * 0.12;
        if (FoilTier > 3.5) {
            vec2 cells = uv * vec2(85.0, 120.0);
            vec2 cell = floor(cells);
            float seed = hash(cell);
            vec2 center = vec2(hash(cell + 17.0), hash(cell + 39.0)) * 0.65 + 0.175;
            vec2 point = fract(cells) - center;
            float grain = exp(-dot(point, point) * 140.0);
            float twinkle = pow(max(0.0, sin(FoilTime * 2.4 + seed * 35.0 + dot(FoilTilt, cell) * 0.22)), 16.0);
            color += vec3(0.7, 0.85, 1.0) * grain * twinkle * step(0.73, seed) * 0.65;
        }
        if (FoilTier > 4.5) {
            float ripple = sin(length((uv - lightPos) * vec2(1.0, 0.72)) * 65.0 - FoilTime * 0.8);
            color += rainbow * pow(max(0.0, ripple), 14.0) * 0.045;
        }
        if (FoilTier > 5.5) {
            float grain = hash(floor(uv * 720.0));
            vec3 gold = vec3(1.0, 0.77, 0.27);
            color = mix(color, color * vec3(1.08, 0.96, 0.72), 0.22);
            color += gold * (sweep * 0.13 + specular * 0.18 + rim * 0.16 + grain * 0.025);
        }
    }
    fragColor = vec4(clamp(color, 0.0, 1.0), base.a * mask * FoilAlpha) * vertexColor * ColorModulator;
}
