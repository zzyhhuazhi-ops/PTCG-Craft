#version 150
in vec2 effectUV;
in vec4 tint;
uniform sampler2D Sampler0;
uniform vec3 AtlasFrames;
uniform float Intensity;
uniform vec3 GoldColor;
uniform vec3 CoreColor;
out vec4 fragColor;
vec4 material(float frame){
    vec2 cell=vec2(mod(frame,4.0),floor(frame/4.0));
    return texture(Sampler0,(cell+clamp(effectUV,vec2(.0009765625),vec2(.9990234375)))/4.0);
}
void main(){
    vec4 a=material(AtlasFrames.x),b=material(AtlasFrames.y);
    // Interpolate premultiplied samples, then return straight alpha for the GUI blend.
    float alpha=mix(a.a,b.a,AtlasFrames.z);
    vec3 rgb=mix(a.rgb*a.a,b.rgb*b.a,AtlasFrames.z)/max(alpha,.00001);
    float white=clamp((rgb.b-.12)/.88,0.0,1.0);
    fragColor=vec4(mix(GoldColor,CoreColor,white),clamp(alpha*Intensity,0.0,1.0))*tint;
}
