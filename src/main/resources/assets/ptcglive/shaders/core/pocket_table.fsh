#version 150
uniform sampler2D Sampler0;
uniform float Material;
uniform vec4 OwnA;
uniform vec4 OwnB;
uniform vec3 OwnC;
uniform vec4 OpponentA;
uniform vec4 OpponentB;
uniform vec3 OpponentC;
uniform vec4 Environment;
uniform vec2 TurnTiles;
in vec2 uv;
in vec4 tint;
out vec4 fragColor;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+1.0),f.x),f.y);}
float hexEdge(vec2 p){
    vec2 r=vec2(1.0,1.73205),a=mod(p,r)-r*.5,b=mod(p-r*.5,r)-r*.5;
    vec2 h=dot(a,a)<dot(b,b)?a:b;return .5-max(abs(h.x),dot(abs(h),vec2(.5,.866025)));
}
float weight(int i,float side){
    if(i<4)return mix(OpponentA[i],OwnA[i],side);
    if(i<8)return mix(OpponentB[i-4],OwnB[i-4],side);
    return mix(OpponentC[i-8],OwnC[i-8],side);
}
// Stable engine type IDs. These are original materials; no commercial game textures are sampled.
vec3 palette(int id){
    if(id==0)return vec3(.20,.77,.40);
    if(id==1)return vec3(1.0,.33,.10);
    if(id==2)return vec3(.12,.64,.98);
    if(id==3)return vec3(1.0,.84,.20);
    if(id==4)return vec3(.69,.36,.98);
    if(id==5)return vec3(.78,.47,.22);
    if(id==6)return vec3(.26,.42,.60);
    if(id==7)return vec3(.64,.78,.86);
    if(id==8)return vec3(.78,.66,.26);
    if(id==10)return vec3(.98,.43,.71);
    return vec3(.67,.77,.88);
}
float pattern(int id,vec2 p,float t){
    vec2 cell=floor(p),q=fract(p)-.5;float seed=hash(cell);
    if(id==0){
        q=mat2(.8,-.6,.6,.8)*q;float leaf=1.0-smoothstep(.20,.30,length(q*vec2(1.4,.7)));
        float vein=1.0-smoothstep(.012,.028,abs(q.x));return leaf*(.22+vein*.40)*(.75+.25*sin(t*.5+seed*6.28));
    }
    if(id==1){float lava=1.0-smoothstep(.018,.075,abs(sin(p.x*2.4+noise(p*1.7)*3.0)*cos(p.y*2.2)));
        return lava*(.35+.4*noise(p*1.2+vec2(0,-t*.13)));}
    if(id==2){float waves=sin(p.x*3.4+t*.4+sin(p.y*2.1-t*.23))*sin(p.y*3.0-t*.3+sin(p.x));
        float bubble=1.0-smoothstep(.008,.027,abs(length(q+vec2(0,sin(t*.23+seed*6.28)*.12))-(.07+seed*.12)));
        return pow(max(0.0,waves),4.0)*.7+bubble*.22;}
    if(id==3){float kink=sin(p.y*3.0)+sin(p.y*7.0)*.32;float arc=1.0-smoothstep(.018,.055,abs(sin(p.x*1.1+kink*.22)));
        return arc*(.24+.28*sin(t*.6+p.y)*sin(t*.6+p.y));}
    if(id==4){float orbit=length(p-vec2(5.0,2.8)),ring=1.0-smoothstep(.025,.09,abs(sin(orbit*2.3-t*.3)));
        return ring*.25+pow(max(0.0,1.0-length(q)/.32),3.0)*(.2+.4*seed);}
    if(id==5){return (1.0-smoothstep(.015,.075,hexEdge(p+noise(p)*.22)))*.5+noise(p*5.0)*.12;}
    if(id==6){return noise(p*.7+vec2(t*.018,-t*.025))*noise(p*1.4-vec2(t*.03,0))*.65;}
    if(id==7){float line=1.0-smoothstep(.006,.018,min(abs(q.x),abs(q.y)));return line*.35+sin(p.x*95.0)*.025;}
    if(id==8){return (1.0-smoothstep(.025,.065,hexEdge(p*1.4)))*(.3+.2*sin(p.y*2.0+t*.25));}
    if(id==10){vec2 s=abs(q);return pow(max(0.0,1.0-(s.x+s.y)*3.5),5.0)*(.35+.2*sin(t*.4+seed*6.28));}
    return noise(p*.8)*.1;
}
vec3 environmentSurface(vec2 p,float vertical,bool mat){
    float side=smoothstep(.34,.66,vertical),t=Environment.x;
    vec3 color=vec3(0),detail=vec3(0);
    for(int i=0;i<11;i++){
        float w=weight(i,side);if(w<.0001)continue;
        vec3 tint=palette(i);color+=tint*w;
        if(Environment.z>0.0)detail+=tint*pattern(i,p,t)*w;
    }
    float lattice=1.0-smoothstep(.006,.021,hexEdge(p*2.2));
    vec3 base=mat?mix(vec3(.54,.68,.80),vec3(.65,.80,.88),side)+color*.10:vec3(.035,.055,.085)+color*.065;
    base+=lattice*(mat?.027:.014);
    base+=detail*(mat?.22:.14)*Environment.z;
    return base;
}
void main(){
    vec4 c=tint;
    float edge=min(min(uv.x,1.0-uv.x),min(uv.y,1.0-uv.y));
    if(Material<0.5)c*=texture(Sampler0,uv);
    else if(Material<1.5){
        vec2 p=(uv-.5)*vec2(1.0,1.4);float ring=1.0-smoothstep(.008,.025,abs(length(p)-.25));
        c.rgb=mix(vec3(.22,.48,.68),vec3(.56,.82,.90),uv.y);
        c.rgb=mix(c.rgb,vec3(.9,.98,1),max(ring,1.0-smoothstep(.012,.022,abs(p.y)))*.75);
        c.rgb=mix(vec3(.94,.97,1),c.rgb,smoothstep(.015,.04,edge));
    }else if(Material<2.5){
        c.rgb=environmentSurface(uv*vec2(18,Environment.w),uv.y,true);
        // The active slots share a low-contrast playing surface. The bevel and the
        // stronger side colours belong to its perimeter, under the bench cards.
        vec2 mat=abs(uv-.5);
        float field=max(mat.y-.264,max(mat.x-.477,mat.x+.42*mat.y-.538));
        float aa=max(fwidth(field),.0008);
        float apron=smoothstep(-aa,aa,field);
        vec3 sideTint=mix(vec3(.48,.20,.23),vec3(.12,.30,.52),smoothstep(.32,.68,uv.y));
        c.rgb=mix(c.rgb,mix(c.rgb,sideTint,.34),apron);
        float metal=(1.0-smoothstep(.004,.010+aa,abs(field-.012)));
        c.rgb=mix(c.rgb,vec3(.53,.60,.67)*(.85+.15*uv.y),metal*.79);
        c.rgb*=1.0-.27*(1.0-smoothstep(.0,.006+aa,abs(field)));
        float center=1.0-smoothstep(.005,.012,abs(uv.y-.5));
        float rim=1.0-smoothstep(.004,.011,edge);
        float radial=length((uv-.5)*vec2(18.0/Environment.w,1));
        float ring=1.0-smoothstep(.002,.006,abs(radial-.18));
        vec3 emblem=mix(vec3(.77,.85,.92),vec3(.91,.96,.99),smoothstep(.49,.51,uv.y));
        c.rgb=mix(c.rgb,emblem,.22*(1.0-smoothstep(.176,.18,radial))*smoothstep(.029,.037,radial));
        float hub=1.0-smoothstep(.025,.031,radial);
        c.rgb=mix(c.rgb,vec3(.95,.98,1.0),max(max(center*.35,ring*.52),max(hub*.74,rim)));
    }else if(Material<3.5){c.a*=mix(.06,.55,1.0-smoothstep(.01,.035,edge));}
    else if(Material<4.5){c.a*=1.0-smoothstep(.68,1.0,length((uv-.5)*2.0));}
    else if(Material>7.5){
        // This quad is 10% wider than the card. Only shade the exposed rim.
        float aa=max(.002,fwidth(edge));
        float rim=smoothstep(.0,aa,edge)*(1.0-smoothstep(.045,.045+aa,edge));
        c.rgb=mix(c.rgb*.32,c.rgb,smoothstep(.014,.014+aa,edge));
        c.a*=rim;
    }else if(Material>6.5){
        vec2 p=uv*vec2(18.0,Environment.w)*.62;
        vec2 lattice=vec2(1.0,1.73205);
        vec2 a=mod(p,lattice)-lattice*.5,b=mod(p-lattice*.5,lattice)-lattice*.5;
        vec2 local=dot(a,a)<dot(b,b)?a:b;
        vec2 tile=p-local;
        float row=clamp(tile.y/(Environment.w*.62),0.0,1.0);
        float sweep=mix(1.0-row,row,TurnTiles.y);
        float age=TurnTiles.x-(.40*sweep+.12*hash(floor(tile/lattice*2.0+.5)));
        float pulse=smoothstep(.0,.13,age)*(1.0-smoothstep(.15,.47,age));
        float rim=1.0-smoothstep(.018,.055,hexEdge(p));
        float reveal=smoothstep(.0,.13,age);
        float side=mix(1.0-TurnTiles.y,TurnTiles.y,reveal);
        vec3 hue=mix(vec3(.87,.45,.74),vec3(.34,.72,.98),side);
        // Keep the incoming side's wash after the front passes. Printed mat art stays visible.
        float motif=.5+.5*cos(uv.x*14.0+mix(-1.0,1.0,side)*uv.y*10.0);
        c=vec4(mix(hue,vec3(1),pulse*rim*.65),(.055+.075*motif+pulse*(.24+.48*rim))*Environment.z);
    }else if(Material>5.5){
        vec2 p=uv*vec2(Environment.y*9.0,9.0);
        c.rgb=environmentSurface(p,uv.y,false);
        vec3 nearColor=vec3(0),farColor=vec3(0);
        for(int i=0;i<11;i++){nearColor+=palette(i)*weight(i,1.0);farColor+=palette(i)*weight(i,0.0);}
        float nearGlow=exp(-dot((uv-vec2(.18,.92))*vec2(Environment.y,1),(uv-vec2(.18,.92))*vec2(Environment.y,1))*4.0);
        float farGlow=exp(-dot((uv-vec2(.82,.08))*vec2(Environment.y,1),(uv-vec2(.82,.08))*vec2(Environment.y,1))*4.0);
        c.rgb+=nearColor*nearGlow*.16+farColor*farGlow*.12;
    }
    if(c.a<.003)discard;
    fragColor=c;
}
