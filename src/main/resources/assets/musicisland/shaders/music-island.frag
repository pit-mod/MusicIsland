#version 120
uniform sampler2D content;
uniform vec2 bounds;
uniform vec2 body;
uniform float radius;
uniform vec2 bubble;
uniform float bubbleRadius;
uniform float neck;
uniform float opacity;
varying vec2 uv;
float box(vec2 p, vec2 halfSize, float r) {
    vec2 q=abs(p)-halfSize+vec2(r);
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
}
void main() {
    vec2 p=(uv-vec2(0.5,0.0))*bounds-vec2(0.0,12.0);
    float a=box(p-vec2(0.0,body.y*0.5),body*0.5,radius);
    float b=length(p-bubble)-bubbleRadius;
    float d=a;
    if(bubbleRadius>0.02) {
        float k=max(neck,0.001);
        float h=clamp(0.5+0.5*(b-a)/k,0.0,1.0);
        d=mix(b,a,h)-k*h*(1.0-h);
    }
    float aa=max(fwidth(d),0.12);
    float coverage=1.0-smoothstep(-aa*0.5,aa*0.5,d);
    float shadow=exp(-max(d,0.0)*0.65)*0.22*(1.0-coverage);
    vec4 c=texture2D(content,vec2(uv.x,1.0-uv.y));
    // The content FBO stores premultiplied RGB. Black body stays opaque.
    float alpha=(coverage+shadow)*opacity;
    gl_FragColor=vec4(c.rgb*coverage/max(coverage+shadow,0.001),alpha);
}
