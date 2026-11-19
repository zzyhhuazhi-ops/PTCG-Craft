#version 150
in vec2 effectUV;
in vec4 tint;
uniform vec4 Phase;
out vec4 fragColor;
void main(){
    vec2 p=(effectUV-.5)*3.6;float r=length(p),a=atan(p.y,p.x);
    float late=smoothstep(.30,.50,Phase.w);
    float ring=exp(-pow((r-Phase.z)/mix(.025,.012,late),2.));
    ring+=exp(-pow((r-Phase.z*.88)/.018,2.))*.7*(1.-smoothstep(.26,.46,Phase.w));
    float glow=exp(-pow((r-Phase.z)/mix(.15,.075,late),2.));
    float breakup=smoothstep(.24,.39,Phase.w);
    float jagged=1.+breakup*(sin(a*7.+.7)*.10+sin(a*13.-1.2)*.05);
    float core=exp(-pow(r/(mix(.47,.30,breakup)*jagged),6.))*Phase.x;
    float rays=exp(-pow(p.y/.11,2.))*exp(-r*r/.9)*Phase.x*.4;
    rays+=exp(-pow((p.y+p.x*.8)/.12,2.))*exp(-r*r)*Phase.x*.2;
    // The source flash blooms outside the ring, then clears before the debris.
    float bloomLife=smoothstep(.07,.166,Phase.w)*(1.-smoothstep(.27,.43,Phase.w));
    float haze=exp(-r*r/.9)*Phase.x*.40;
    float bloom=exp(-pow(r/1.12,4.))*bloomLife*.70*smoothstep(Phase.z*.80,Phase.z*1.08,r);
    float spokes=pow(max(0.,sin(a*5.+.3)),16.)*exp(-r*r/.85)*bloomLife*.32;
    float life=smoothstep(.28,.4,Phase.w)*(1.-smoothstep(.5,.69,Phase.w));
    float dots=0.;
    for(int i=0;i<12;i++){
        float seed=fract(float(i)*.618034),angle=float(i)*2.399963;
        vec2 center=vec2(cos(angle),sin(angle))*(.1+seed*.60+max(0.,Phase.w-.3)*.2);
        vec2 q=p-center;float radius=(i<3?.16:.04+seed*.055)*mix(1.,.25,smoothstep(.50,.63,Phase.w));
        dots+=exp(-dot(q,q)/(radius*radius))*life;
    }
    float alpha=clamp(core*1.8+rays*.8+haze+bloom+spokes+(ring+glow*.4)*Phase.y+dots,0.,1.);
    vec3 color=mix(vec3(.24,1.,1.),vec3(1.),clamp(core*2.+rays*.8+dots*.45+bloom*1.7+spokes,0.,1.));
    color=mix(color,mix(vec3(.20,1.,1.),vec3(.83,1.,1.),late),clamp(ring*Phase.y,0.,1.));
    fragColor=vec4(color,alpha)*tint;
}
