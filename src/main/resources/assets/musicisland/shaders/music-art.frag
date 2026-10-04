#version 120
uniform sampler2D artwork;
uniform vec2 crop;
uniform float alpha;
varying vec2 uv;
void main() {
    vec2 q=abs(uv-0.5)-vec2(0.5-0.20);
    float d=length(max(q,0.0))+min(max(q.x,q.y),0.0)-0.20;
    float coverage=1.0-smoothstep(-fwidth(d)*0.5,fwidth(d)*0.5,d);
    gl_FragColor=texture2D(artwork,(uv-0.5)*crop+0.5)*vec4(1.0,1.0,1.0,alpha*coverage);
}
