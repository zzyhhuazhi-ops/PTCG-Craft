#version 150
in vec2 effectUV;
in vec4 tint;
uniform vec4 Phase;
uniform float Water;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+1.),f.x),f.y);}
float cloud(vec2 p){return noise(p)*.57+noise(p*2.03+5.1)*.28+noise(p*4.07-2.9)*.15;}
void main(){
    vec2 p=(effectUV-.5)*2.;float t=Phase.x,r=length(p),a=atan(p.y,p.x);
    float spin=t*(Water>.5?-1.7:3.2),cs=cos(spin),sn=sin(spin);
    vec2 flow=mat2(cs,-sn,sn,cs)*p;
    float n=cloud(flow*7.+vec2(t*.5,-t*1.3));
    float fine=cloud(flow*19.+vec2(-t*2.,t));
    float front=.44+.035*Phase.w+.025*sin(a*5.-t*8.)+.06*(n-.5);
    float rim=exp(-pow((r-front)/.055,2.));
    float flash=exp(-pow(r/.33,4.))*Phase.y;
    vec3 rgb;float alpha;
    if(Water>.5){
        // Translucent volume, wrinkled pressure front and separate white foam fragments.
        flash=exp(-pow(r/.64,6.))*Phase.y;
        float body=(1.-smoothstep(front-.10,front+.07,r))*(.80+.15*n);
        body*=1.-smoothstep(.45,.85,t)*(1.-smoothstep(.18,.40,r));
        float foam=pow(smoothstep(.40,.8,fine),2.)*exp(-pow((r-front)/.14,2.));
        float wave=exp(-pow(sin(a*3.+n*2.+r*18.-t*8.)*5.,2.))*exp(-pow((r-front)/.16,2.))*(1.-smoothstep(front,front+.06,r));
        float spray=pow(max(0.,sin(a*8.+n*1.2)),10.)*exp(-pow((r-front-.13-Phase.w*.035)/.075,2.));
        float bubbles=0.;
        for(int i=0;i<10;i++){
            float seed=hash(vec2(float(i),7.));
            vec2 center=vec2((hash(vec2(float(i),3.))-.5)*.65,fract(seed-t*.32)*.76-.38);
            float radius=.025+.035*seed,d=length(p-center);
            float edge=exp(-pow((d-radius)/.006,2.));
            float highlight=exp(-dot(p-center+vec2(radius*.3),p-center+vec2(radius*.3))/.00012);
            bubbles+=(edge*.6+highlight*.8)*(1.-smoothstep(.32,.41,length(center)));
        }
        float white=clamp(flash+body*.55+rim*.3+foam*.5+wave*.5+bubbles*.7,0.,1.);
        rgb=mix(vec3(.10,.76,1.),vec3(.92,1.,1.),white);
        alpha=clamp(flash+(body+rim*.6+foam+wave*.3+spray*.95+bubbles*.3)*Phase.z,0.,1.);
    }else{
        // Filled flash -> thick rotating flame sheets -> detached orange wisps.
        // All phases are continuous in real time; fixed seeds keep frame seeking reproducible.
        float coarse=cloud(flow*4.8+vec2(t*.4,-t*1.5));
        float late=smoothstep(.32,.70,t);
        float orbit=.48+.035*Phase.w+.045*sin(a*3.-t*8.)+.14*(coarse-.5);
        float width=.055+.065*coarse;
        float band=exp(-pow((r-orbit)/width,2.));
        float curl=exp(-pow((r-orbit*.76-.055*sin(a*2.+t*7.))/(width*.75),2.));
        float tongues=smoothstep(.34,.61,coarse)*exp(-pow((r-orbit-.04)/.18,2.));
        float holes=smoothstep(.25,.50,coarse+.07*sin(a*7.-t*6.));
        float sheet=(band*.9+curl*.65+tongues*1.4)*mix(1.,holes,late);
        float halo=exp(-pow((r-orbit)/.24,2.))*.35;
        float trace=exp(-pow((r-orbit*.94-.025*sin(a*4.+t*9.))/.014,2.))
                   +.7*exp(-pow((r-orbit*.77-.035*sin(a*3.-t*8.))/.018,2.));
        float plume=(sheet*.68+halo+trace*.6*(1.-late))*Phase.z;
        float coreRadius=.30+.025*sin(a*5.+.4)+.018*sin(a*9.-t*2.);
        float solid=(1.-smoothstep(coreRadius-.045,coreRadius+.035,r))*clamp(Phase.y*1.55,0.,1.);
        float rays=0.;
        for(int i=0;i<15;i++){
            float seed=hash(vec2(float(i),4.));
            float angle=float(i)*2.399963+.24*seed;
            vec2 axis=vec2(cos(angle),sin(angle));
            float along=dot(p,axis),across=abs(dot(p,vec2(-axis.y,axis.x)));
            float reach=.48+.38*seed;
            float taper=.022+.075*hash(vec2(float(i),9.));
            float edge=taper*clamp(1.-along/reach,0.,1.);
            rays+=smoothstep(0.,.10,along)*(1.-smoothstep(reach-.035,reach,along))
                 *(1.-smoothstep(edge*.7,edge+.006,across));
        }
        rays=clamp(rays,0.,1.)*Phase.y;
        float heat=clamp(band*.7+curl*.55+coarse*.55,0.,1.);
        rgb=mix(vec3(1.,.25,.008),vec3(1.,.86,.04),heat);
        rgb=mix(rgb,vec3(1.,.95,.20),(1.-late)*.75);
        rgb=mix(rgb,vec3(1.,1.,.83),clamp((band+curl)*.25+trace,0.,1.)*(1.-late));
        rgb=mix(rgb,vec3(1.,1.,.96),clamp(solid*1.5+rays*.85,0.,1.));
        alpha=clamp(solid+plume+rays,0.,1.);
    }
    alpha*=1.-smoothstep(.88,1.,r);
    fragColor=vec4(rgb,alpha)*tint;
}
