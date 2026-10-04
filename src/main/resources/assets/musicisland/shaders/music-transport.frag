#version 120
varying vec2 uv;
uniform float playing;
uniform float opacity;

float cross2(vec2 a,vec2 b){return a.x*b.y-a.y*b.x;}
float edge(vec2 p,vec2 a,vec2 b){
    vec2 e=b-a,v=p-a;
    vec2 q=v-e*clamp(dot(v,e)/max(dot(e,e),.00001),0.0,1.0);
    return dot(q,q);
}
float quadDistance(vec2 p,vec2 a,vec2 b,vec2 c,vec2 d){
    float squared=min(min(edge(p,a,b),edge(p,b,c)),min(edge(p,c,d),edge(p,d,a)));
    float inside=step(0.0,cross2(b-a,p-a))*step(0.0,cross2(c-b,p-b))
        *step(0.0,cross2(d-c,p-c))*step(0.0,cross2(a-d,p-d));
    return sqrt(squared)*(1.0-2.0*inside);
}
void main(){
    float t=clamp(playing,0.0,1.0);
    vec2 p=(uv-.5)*20.0;
    // Both filled halves form one triangle, then separate into balanced pause bars.
    // A single union keeps the centre seamless and fully white throughout the morph.
    float left=quadDistance(p,mix(vec2(-4.0,-6.1),vec2(-4.7,-6.1),t),
        mix(vec2(1.3,-3.1),vec2(-1.3,-6.1),t),
        mix(vec2(1.3,3.1),vec2(-1.3,6.1),t),
        mix(vec2(-4.0,6.1),vec2(-4.7,6.1),t));
    float right=quadDistance(p,mix(vec2(1.3,-3.1),vec2(1.3,-6.1),t),
        mix(vec2(6.1,0.0),vec2(4.7,-6.1),t),
        mix(vec2(6.1,0.0),vec2(4.7,6.1),t),
        mix(vec2(1.3,3.1),vec2(1.3,6.1),t));
    float distance=min(left,right)-mix(.32,.5,t);
    float aa=max(fwidth(distance),.04);
    gl_FragColor=vec4(vec3(1.0),(1.0-smoothstep(-aa*.5,aa*.5,distance))*opacity);
}
