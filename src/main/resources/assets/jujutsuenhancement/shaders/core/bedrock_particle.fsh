#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// Like position_color_tex, but without the alpha < 0.1 cut-off: the Bedrock glows are mostly very faint.
void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = color * ColorModulator;
}
