#version 150
in vec2 effectUV;
in vec4 tint;
uniform vec4 Phase;
uniform float FocusBottom;
out vec4 fragColor;
// Vendored unchanged from https://github.com/stegu/psrdnoise/blob/main/src/psrdnoise2.glsl
//
// psrdnoise2.glsl
//
// Authors: Stefan Gustavson (stefan.gustavson@gmail.com)
// and Ian McEwan (ijm567@gmail.com)
// Version 2021-12-02, published under the MIT license (see below)
//
// Copyright (c) 2021 Stefan Gustavson and Ian McEwan.
//
// Permission is hereby granted, free of charge, to any person obtaining a
// copy of this software and associated documentation files (the "Software"),
// to deal in the Software without restriction, including without limitation
// the rights to use, copy, modify, merge, publish, distribute, sublicense,
// and/or sell copies of the Software, and to permit persons to whom the
// Software is furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included
// in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
// THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
// DEALINGS IN THE SOFTWARE.
//

//
// Periodic (tiling) 2-D simplex noise (hexagonal lattice gradient noise)
// with rotating gradients and analytic derivatives.
//
// This is (yet) another variation on simplex noise. Unlike previous
// implementations, the grid is axis-aligned and slightly stretched in
// the y direction to permit rectangular tiling.
// The noise pattern can be made to tile seamlessly to any integer period
// in x and any even integer period in y. Odd periods may be specified
// for y, but then the actual tiling period will be twice that number.
//
// The rotating gradients give the appearance of a swirling motion, and
// can serve a similar purpose for animation as motion along z in 3-D
// noise. The rotating gradients in conjunction with the analytic
// derivatives allow for "flow noise" effects as presented by Ken
// Perlin and Fabrice Neyret.
//


//
// 2-D tiling simplex noise with rotating gradients and analytical derivative.
// "vec2 x" is the point (x,y) to evaluate,
// "vec2 period" is the desired periods along x and y, and
// "float alpha" is the rotation (in radians) for the swirling gradients.
// The "float" return value is the noise value, and
// the "out vec2 gradient" argument returns the x,y partial derivatives.
//
// Setting either period to 0.0 or a negative value will skip the wrapping
// along that dimension. Setting both periods to 0.0 makes the function
// execute about 15% faster.
//
// Not using the return value for the gradient will make the compiler
// eliminate the code for computing it. This speeds up the function
// by 10-15%.
//
// The rotation by alpha uses one single addition. Unlike the 3-D version
// of psrdnoise(), setting alpha == 0.0 gives no speedup.
//
float psrdnoise(vec2 x, vec2 period, float alpha, out vec2 gradient) {

	// Transform to simplex space (axis-aligned hexagonal grid)
	vec2 uv = vec2(x.x + x.y*0.5, x.y);

	// Determine which simplex we're in, with i0 being the "base"
	vec2 i0 = floor(uv);
	vec2 f0 = fract(uv);
	// o1 is the offset in simplex space to the second corner
	float cmp = step(f0.y, f0.x);
	vec2 o1 = vec2(cmp, 1.0-cmp);

	// Enumerate the remaining simplex corners
	vec2 i1 = i0 + o1;
	vec2 i2 = i0 + vec2(1.0, 1.0);

	// Transform corners back to texture space
	vec2 v0 = vec2(i0.x - i0.y * 0.5, i0.y);
	vec2 v1 = vec2(v0.x + o1.x - o1.y * 0.5, v0.y + o1.y);
	vec2 v2 = vec2(v0.x + 0.5, v0.y + 1.0);

	// Compute vectors from v to each of the simplex corners
	vec2 x0 = x - v0;
	vec2 x1 = x - v1;
	vec2 x2 = x - v2;

	vec3 iu, iv;
	vec3 xw, yw;

	// Wrap to periods, if desired
	if(any(greaterThan(period, vec2(0.0)))) {
		xw = vec3(v0.x, v1.x, v2.x);
		yw = vec3(v0.y, v1.y, v2.y);
		if(period.x > 0.0)
			xw = mod(vec3(v0.x, v1.x, v2.x), period.x);
		if(period.y > 0.0)
			yw = mod(vec3(v0.y, v1.y, v2.y), period.y);
		// Transform back to simplex space and fix rounding errors
		iu = floor(xw + 0.5*yw + 0.5);
		iv = floor(yw + 0.5);
	} else { // Shortcut if neither x nor y periods are specified
		iu = vec3(i0.x, i1.x, i2.x);
		iv = vec3(i0.y, i1.y, i2.y);
	}

	// Compute one pseudo-random hash value for each corner
	vec3 hash = mod(iu, 289.0);
	hash = mod((hash*51.0 + 2.0)*hash + iv, 289.0);
	hash = mod((hash*34.0 + 10.0)*hash, 289.0);

	// Pick a pseudo-random angle and add the desired rotation
	vec3 psi = hash * 0.07482 + alpha;
	vec3 gx = cos(psi);
	vec3 gy = sin(psi);

	// Reorganize for dot products below
	vec2 g0 = vec2(gx.x,gy.x);
	vec2 g1 = vec2(gx.y,gy.y);
	vec2 g2 = vec2(gx.z,gy.z);

	// Radial decay with distance from each simplex corner
	vec3 w = 0.8 - vec3(dot(x0, x0), dot(x1, x1), dot(x2, x2));
	w = max(w, 0.0);
	vec3 w2 = w * w;
	vec3 w4 = w2 * w2;

	// The value of the linear ramp from each of the corners
	vec3 gdotx = vec3(dot(g0, x0), dot(g1, x1), dot(g2, x2));

	// Multiply by the radial decay and sum up the noise value
	float n = dot(w4, gdotx);

	// Compute the first order partial derivatives
	vec3 w3 = w2 * w;
	vec3 dw = -8.0 * w3 * gdotx;
	vec2 dn0 = w4.x * g0 + dw.x * x0;
	vec2 dn1 = w4.y * g1 + dw.y * x1;
	vec2 dn2 = w4.z * g2 + dw.z * x2;
	gradient = 10.9 * (dn0 + dn1 + dn2);

	// Scale the return value to fit nicely into the range [-1,1]
	return 10.9 * n;
}

float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}
float cloud(vec2 p){return noise(p)*.57+noise(p*2.03+5.1)*.28+noise(p*4.07-2.9)*.15;}
// GLSL pow is undefined for a negative base even with an integer exponent.
// Signed distances on either side of a flame/arc must use multiplication.
float square(float x){return x*x;}
float arc(float angle,float center,float width){float d=atan(sin(angle-center),cos(angle-center));return exp(-square(square(d/(width*(d<0.0?1.35:.75)))));}
float tongues(float angle,float outer,float t){
    float around=angle*23.0/6.2831853+t*1.2,cell=floor(around),result=0.0;
    for(int j=-1;j<=1;j++){
        float index=cell+float(j),seed=hash(vec2(mod(index,23.0),2.3));
        float reach=(.09+.19*seed)*(.78+.22*sin(t*17.0+seed*22.0));
        float along=max(0.0,outer)/reach;
        float bend=(seed-.2)*1.2*along*along+sin(along*5.0-t*13.0+seed*22.0)*.13*along;
        float across=around-index-.5-(seed-.5)*.25-bend;
        float width=(.16+.15*seed)*max(0.0,1.0-along);
        float body=1.0-smoothstep(width*.18,width+.06,abs(across));
        result=max(result,body*(1.0-smoothstep(.7,1.0,along))*(.6+.4*seed));
    }
    return result*smoothstep(-.015,.015,outer);
}
// The gameplay plume rises from the shell, rather than radiating uniformly.
// Fixed analytic sheets keep the motion deterministic without particle allocation.
float risingFlames(vec2 p,float radius,float t){
    float result=0.0;
    // Warp the material domain, not the sheet geometry: a large displacement
    // of p folds the distance field and creates detached closed loops.
    vec2 gradient, detail;
    float flow=psrdnoise(p*3.4+vec2(0.0,t*1.3),vec2(0.0),t*2.4,gradient);
    float fine=psrdnoise(p*7.0-.18*gradient+vec2(t*.2,t*2.0),vec2(0.0),t*3.1,detail);
    float turbulence=clamp(.5+.32*flow+.18*fine,0.0,1.0);
    float grain=cloud(p*vec2(32.0,21.0)+vec2(t*.5,t*8.0));
    for(int i=0;i<32;i++){
        float seed=hash(vec2(float(i),4.7));
        bool inner=i>=23;
        // Place the reveal sheets along the upper inside edge. Distributing
        // these over a whole circle hides most of them behind the card face.
        float angle=inner ? -2.95+(float(i-23)+seed*.6)*2.75/9.0
                          : (float(i)+seed*.8)*6.2831853/23.0;
        vec2 radial=vec2(cos(angle),sin(angle));
        vec2 direction=normalize(vec2(radial.x*.7,radial.y*.2-1.0));
        // Nine inner sheets rise behind the reveal face. An outer-only corona
        // leaves the reference's 383-500 ms gap above the card empty.
        vec2 delta=p-radius*(inner?.76:1.0)*radial;
        float along=dot(delta,direction);
        float reach=(.20+.33*seed)*(.85+.15*sin(t*12.0+seed*21.0))*(1.0+.30*max(0.0,-radial.y));
        // As the ring opens, its exterior sheets shorten rather than keeping
        // the initial burst height. The inner reveal plume has its own reach.
        reach*=inner?.72:mix(.78,.48,smoothstep(.267,.42,t));
        float progress=clamp(along/reach,0.0,1.0);
        // A continuous centerline indexed by height keeps every sheet rooted.
        // Its independent moving inflection bends the tip without folding it
        // backwards into the isolated glass-like rings of the previous field.
        float bend=(seed-.5)*.24*progress*progress;
        bend+=(.055+.065*seed)*sin(progress*(3.5+seed*2.0)-t*6.0+seed*24.0)*progress*progress;
        float across=dot(delta,vec2(-direction.y,direction.x))-bend;
        // Small-scale edge erosion never reduces the width/coverage to zero,
        // retaining an attached translucent body beneath the broken bright edge.
        float width=.001+(.034+.072*seed)*pow(1.0-progress,.8)*(.8+.4*turbulence)*(.50+grain)*(inner?.65:1.0);
        float edge=abs(across)/width;
        float body=.18*exp(-square(edge))+.62*(1.0-smoothstep(.35,1.0,edge));
        // Brighter broken edges over a translucent body, not uniformly soft smoke.
        body+=.70*exp(-square((edge-.70)/.13))*smoothstep(.3,.65,grain);
        body*=smoothstep(-.025,.015,along)*(1.0-smoothstep(.72,1.0,progress));
        result=max(result,body*(.42+.40*seed)*(.65+.35*turbulence)*(.65+.35*grain)*(inner?1.3:1.0));
    }
    return result;
}
void main(){
    vec2 p=(effectUV-.5)*2.0;float t=Phase.x,r=length(p),angle=r>0.00001?atan(p.y,p.x):0.0;
    // The reference mat darkens and shifts blue during the reveal, independently
    // of the emissive shell. Keep the hand UI outside this focus treatment.
    float focus=Phase.w*(1.0-smoothstep(1.15,1.6,r))*(1.0-smoothstep(FocusBottom-.02,FocusBottom,p.y));
    vec3 focusColor=vec3(.12,0.0,.56);
    if(Phase.y<=0.0||r>=1.2){
        fragColor=vec4(focusColor*focus*tint.rgb*tint.a,focus*tint.a);
        return;
    }
    float n=cloud(p*9.0+vec2(t*.9,-t*.6));
    float late=smoothstep(.35,.55,t);
    float flameLife=1.0-smoothstep(.42,.64,t);
    float residueLife=1.0-smoothstep(.50,.82,t);
    // Long, thin remnants remain through 900 ms. Their angular span must not
    // collapse with opacity into the short dashes of the previous version.
    float arcWidth=max(.85,.05+Phase.y*1.6);
    float crescent=max(arc(angle,-1.05,arcWidth),max(.9*arc(angle,.85,arcWidth*.70),.6*arc(angle,2.55,arcWidth*.35)));
    float breakup=mix(crescent,1.0,smoothstep(.65,.9,Phase.y));
    float shape=.715*Phase.z+.003*sin(angle*7.0+t*5.0)+late*.004*(n-.5);
    // The reference shell thins before its last arc disappears. Fading a thick
    // ring alone loses the visible white crescent in the 650-900 ms interval.
    // Each late crescent retains a bright belly and narrows toward its own tail.
    // At 500 ms the upper rim is already thin while the lower rim remains wide.
    // A uniform early fade turns this into a neon hoop absent from the source.
    float upperWidth=.030*(1.0-smoothstep(.40,.54,t));
    float lowerWidth=.046*(1.0-smoothstep(.50,.62,t));
    float thickness=.003+mix(upperWidth,lowerWidth,smoothstep(-.4,.65,p.y))
        +.021*late*crescent*smoothstep(.52,.65,t)*mix(1.0,.18,smoothstep(.67,.90,t));
    float d=abs(r-shape),rim=exp(-square(d/thickness)),halo=exp(-d*13.0);
    float fragments=square(max(0.0,cloud(p*17.0+vec2(-t*1.9,t*.3))-.46)*2.7);
    float corona=exp(-abs(r-shape-.045)*13.0)*fragments;
    // Radial advection bends each filament around the shell instead of scaling a starburst.
    // Use Cartesian noise to keep the -pi/pi angular seam continuous.
    float outer=r-shape,flow=cloud(p*5.0+vec2(t*.6,-t*.8));
    float curlA=sin(angle*11.0+t*2.1+outer*18.0+flow*5.0);
    float curlB=sin(angle*17.0-t*1.3-outer*13.0+n*4.0);
    float strands=exp(-curlA*curlA*24.0)*.8+exp(-curlB*curlB*45.0)*.45;
    float extent=.055+.09*cloud(p*3.0+vec2(-t*.7,t*.2));
    float wisps=strands*exp(-abs(outer)/extent);
    wisps*=smoothstep(.12,.55,Phase.y)*(.35+.65*flow);
    // Broad curling sheets straddle a clean white ring. Domain distortion keeps
    // the blue flame irregular without turning the rim into electric needles.
    // Filled tongues narrow outward. A radial sine isoline repeats into nested
    // electric loops; evolution needs broad sheets with one tapering tip.
    float curl=angle*11.0+t*2.0+outer*(2.0+flow*5.0)+flow*5.0;
    float lobes=pow(max(0.0,.5+.5*cos(curl)),1.5+28.0*max(0.0,outer));
    float reach=.10+.20*flow;
    float sheet=(.12+.88*lobes)*smoothstep(.27,.59,n)*exp(-square((outer+.025)/reach));
    // This branch is uniform across the draw; stop evaluating the two gradient
    // noise fields once their sheets have disappeared.
    float flame=residueLife>0.0 ? .3*tongues(angle,outer,t)+.2*tongues(angle+1.3,-outer,t*.85)+risingFlames(p,shape,t)*1.15 : 0.0;
    float plume=sheet*.28+flame*(.22+.48*n);
    float upper=1.0-smoothstep(-.4,.5,p.y);
    plume*=smoothstep(.15,.7,Phase.y)*Phase.y*Phase.y*(.40+.60*upper)*flameLife;
    // Faint upward fragments outlive the filled sheet. Do not multiply this
    // tail by the peak's alpha squared, which erases it before it can be seen.
    plume+=flame*.24*residueLife*smoothstep(.50,.64,t)*upper;
    float trailing=exp(-square((r-shape-.018-.006*flow)/.0025))*late*breakup;
    // Gameplay sheds the broad peak sheets before the thin shell breaks into
    // crescents. Do not keep peak flame opacity until the overall orbit fades.
    corona*=flameLife;
    // The broad sheets die first; broken fine filaments remain above the last
    // crescents for another few frames, without resurrecting an opaque shell.
    wisps*=max(flameLife,.32*residueLife*smoothstep(.50,.72,flow));
    float light=halo*.50*mix(1.0,breakup,late)+corona*.65+wisps*.28+plume*2.35;
    vec3 blue=vec3(.15,.65,1.0),white=mix(vec3(.84,.98,1.0),vec3(.96,1.0,1.0),late);
    // Matches the extended procedural canvas in EvolutionGlowShader; the shell
    // retains its original radius, while rising tips may reach beyond r=1.
    float canvas=1.0-smoothstep(1.12,1.2,r);
    float coverage=clamp((rim*1.4*breakup+trailing*.24)*smoothstep(0.0,.25,Phase.y)+plume*2.35*Phase.y,0.0,1.0);
    float emission=clamp(light*Phase.y,0.0,1.0)*.60*(1.0-.85*coverage);
    vec3 material=mix(blue,white,clamp(rim*1.4+trailing*.4+corona*.8+wisps*.7+plume*2.35,0.0,1.0));
    // ONE / ONE_MINUS_SRC_ALPHA composes the covered material and unoccluding
    // light in one pass. RGB is premultiplied exactly once, including vertex tint.
    vec3 color=(material*coverage+mix(blue,white,.35)*emission)*canvas;
    float a=coverage*canvas;
    color+=focusColor*focus*(1.0-a);
    fragColor=vec4(color*tint.rgb*tint.a,(a+focus*(1.0-a))*tint.a);
}
