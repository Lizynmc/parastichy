# parastichy

The image is the spiral arms of a phyllotactic field, drawn without the seeds. A seed at index n sits at angle n * alpha and radius proportional to sqrt(n). Connecting seed n to seed n + k, over and over, traces k spiral arms through the field. For most k those arms are stretched and invisible; for a few they run through the nearest neighbours and the eye picks them out.

![Spiral arms of a phyllotactic field forming and coming apart as the angle sweeps](docs/parastichy.gif)

## Measured, not assumed

Which k survive is not assumed. For a grid of radii the program measures the real distance between seed n and seed n + k, and every family is drawn with an opacity falling off from how close it came to being the shortest. Small families hold the centre, large ones the rim, and the bands shift outward as the field grows, which is how a sunflower head is organised.

At the golden angle the surviving families are 2, 3, 5, 8, 13, 21, 34, 55 and 89. No Fibonacci number appears anywhere in the program, so the sequence is a result of the measurement rather than something built into the drawing.

## The sweep

Alpha sweeps slowly through a band two and a half degrees either side of the golden angle, lingering at the golden angle and crossing the extremes quickly. Away from it the weave holds near the centre and comes apart at the rim. The clip above is one half of the 32 second cycle, from the golden angle out to an extreme and back.

The centre of the field follows the mouse. Click to hold a frame.

## Running

The sketch needs `core.jar` from Processing.

```
javac -cp <core.jar> -d out src/Parastichy.java
java  -cp <core.jar>:out Parastichy
```

On Windows use `;` instead of `:` in the classpath.
