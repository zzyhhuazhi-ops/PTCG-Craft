#version 150
in vec2 effectUV;
in vec4 tint;
uniform vec4 Phase;
uniform vec4 Echo;
// Center opening, white-border width, outer-border width, horizontal opening ratio.
uniform vec4 Contour;
uniform vec4 Hue;
// Item: clear aperture, cyan scattering gain, outer-frame gain, aperture height.
uniform vec4 Material;
// Outer corner radius, core opacity, white echo gain, gold emitted-light gain.
uniform vec4 Finish;
out vec4 fragColor;
float box(vec2 p,vec2 b){vec2 q=abs(p)-b+.025;return length(max(q,0.))+min(max(q.x,q.y),0.)-.025;}
float outerBox(vec2 p,vec2 b){vec2 q=abs(p)-b+Finish.x;return length(max(q,0.))+min(max(q.x,q.y),0.)-Finish.x;}
void main(){
    vec2 p=(effectUV-.5)*vec2(1.8,1.8*88./63.);
    vec2 halfCard=vec2(.5,44./63.);
    float d=box(p,halfCard*Phase.z),outer=outerBox(p,halfCard*Echo.y);
    // The white panel opens from its center; a broad border survives the center flash.
    float opening=Contour.x;
    float hole=1.-smoothstep(-.055,.07,box(p,halfCard*opening*vec2(Contour.w,mix(.98,Material.w,Material.x))));
    float core=(1.-smoothstep(-.025,.045,d))*(1.-hole*mix(opening,smoothstep(.12,.40,opening),Material.x))*Phase.x;
    float edgeWidth=max(.001,Contour.y)*(1.+.08*sin(p.y*11.+p.x*8.+Phase.w*9.));
    float rim=exp(-pow(abs(d)/edgeWidth,2.))*Phase.y;
    float glow=exp(-pow(abs(d)/max(.15,edgeWidth*1.35),2.))*Phase.y*.80;
    float echo=exp(-pow(abs(outer)/max(.001,Contour.z),2.))*Echo.x*.70*Material.z;
    float sparks=0.,moteGlow=0.,streaks=0.;
    for(int i=0;i<18;i++){
        if(float(i)>=4.+(8.+6.*Material.x)*Echo.w)break;
        float seed=fract(float(i)*.618034+.23),seed2=fract(float(i)*.414214+.51);
        float age=max(0.,Phase.w-(1.10+seed2*.20)),life=.26+seed*.22;
        vec2 origin;
        if(i%4==0)origin=vec2((seed-.5)*1.4,-.60-age*.32);
        else if(i%4==1)origin=vec2(.60+age*.15,(seed-.5)*1.8-age*.30);
        else if(i%4==2)origin=vec2((seed-.5)*1.4,.60+age*.40);
        else origin=vec2(-.60-age*.15,(seed-.5)*1.8-age*.20);
        vec2 q=p-origin;
        float a=Echo.z*smoothstep(0.,.04,age)*(1.-smoothstep(life*.5,life,age));
        float twinkle=exp(-abs(q.x)/.004-abs(q.y)/.020)+exp(-abs(q.x)/.015-abs(q.y)/.005);
        sparks+=(exp(-dot(q,q)/(.00005+seed2*.00007))*.7+twinkle*.8)*a;
        moteGlow+=exp(-dot(q,q)/(.0012+seed2*.0005))*a*.55;
        // Fine stationary vertical strands are separate from the drifting glints.
        if(i<6){
            float x=(seed-.5)*1.24;
            float strand=exp(-pow(abs(p.x-x)/max(.0018,fwidth(p.x)*.65),2.));
            strand*=exp(-pow(abs(p.y+(seed2-.5)*.3)/(.65+seed*.3),4.));
            float strandLife=smoothstep(1.10+seed*.04,1.18+seed*.04,Phase.w)*(1.-smoothstep(1.28,1.48,Phase.w));
            streaks+=strand*strandLife*Phase.y*(.12+seed2*.16);
        }
    }
    float white=1.-(1.-clamp(core*Finish.y,0.,1.))*(1.-clamp(rim*1.1,0.,1.));
    float echoWhite=exp(-pow(abs(outer)/max(.008,Contour.z*.7),2.))*Echo.x*Finish.z*Material.x;
    white=1.-(1.-white)*(1.-clamp(echoWhite,0.,1.));
    // Colored scattering retains the outer frame on pale mats; only glints emit extra light.
    float colored=clamp(glow*.65*Material.y+echo+moteGlow*.5,0.,mix(.65,.92,Material.x))*(1.-white);
    float coverage=white+colored;
    vec3 radiance=mix(vec3(1.),Hue.rgb,Material.x*.65)*sparks*.45+vec3(streaks*.45)+Hue.rgb*moteGlow*mix(.15,.3,Material.x);
    radiance+=Hue.rgb*(glow*.2+echo*.3)*Finish.w;
    vec3 whiteColor=mix(vec3(1.),Hue.rgb,clamp(Hue.a,0.,1.));
    fragColor=vec4((whiteColor*white+Hue.rgb*colored+radiance)*tint.rgb*tint.a,coverage*tint.a);
}
