import processing.core.PApplet;

/**
 * A seed at index n sits at angle n * alpha and radius proportional to sqrt(n).
 * Connecting seed n to seed n + k, over and over, traces k spiral arms through
 * the field. For most k those arms are stretched and invisible; for a few they
 * run through the nearest neighbours and the eye picks them out.
 *
 * This sketch draws only the arms, never the seeds. Which k survive is not
 * assumed: for a grid of radii the program measures the real distance between
 * seed n and seed n + k, and every family is drawn with an opacity falling off
 * from how close it came to being the shortest. Small families hold the centre,
 * large ones the rim, and the bands shift outward as the field grows, which is
 * how a sunflower head is organised.
 *
 * Alpha sweeps slowly through a band two and a half degrees wide, lingering at
 * the golden angle and crossing the extremes quickly. At the golden angle the
 * surviving families are 2, 3, 5, 8, 13, 21, 34, 55, 89. Away from it the weave
 * holds near the centre and comes apart at the rim.
 *
 * Canvas proportions are 1200 by 742, which is a golden rectangle.
 *
 * Click to hold a frame.
 */
public class Parastichy extends PApplet {

  private static final int CANVAS_WIDTH = 1200;
  private static final int CANVAS_HEIGHT = 742;

  private static final float PAPER_R = 247, PAPER_G = 248, PAPER_B = 249;
  private static final float INK_R = 22, INK_G = 26, INK_B = 30;

  private static final double PHI = (1 + Math.sqrt(5)) / 2;
  private static final float GOLDEN_ANGLE = (float) (TWO_PI / (PHI * PHI));
  private static final float SWEEP = radians(2.5f);
  private static final float SWEEP_PERIOD = 32;

  private static final float SPACING = 22;
  private static final int MAX_OFFSET = 140;
  private static final int BANDS = 48;
  private static final float FALLOFF = 6;
  private static final int PICKS_PER_BAND = 4;
  private static final int MAX_FAMILIES = 14;
  private static final float MIN_OPACITY = 0.02f;
  private static final int OPACITY_STEPS = 14;
  private static final float STROKE_ALPHA = 0.85f;
  private static final float STROKE_WIDTH = 1.3f;

  private float centreX;
  private float centreY;
  private int seedCount;

  private float[] seedX;
  private float[] seedY;

  private int[] bandSeed;
  private int[] bandOf;
  private float[] bandBlend;

  private float[] quality;
  private float[] gap;
  private float[] peakQuality;
  private int[] picks;

  private int[] families;
  private int familyCount;
  private boolean[] inFamilies;

  private float[][] segmentX1;
  private float[][] segmentY1;
  private float[][] segmentX2;
  private float[][] segmentY2;
  private int[] segmentCount;

  private float clock;
  private float previousInstant;
  private boolean running = true;

  public void settings() {
    size(CANVAS_WIDTH, CANVAS_HEIGHT);
  }

  public void setup() {
    centreX = CANVAS_WIDTH * 0.32f;
    centreY = CANVAS_HEIGHT * 0.46f;
    seedCount = countSeedsToFillCanvas();

    seedX = new float[seedCount + MAX_OFFSET + 2];
    seedY = new float[seedCount + MAX_OFFSET + 2];
    quality = new float[(MAX_OFFSET + 1) * BANDS];
    gap = new float[MAX_OFFSET + 1];
    peakQuality = new float[MAX_OFFSET + 1];
    picks = new int[PICKS_PER_BAND];
    families = new int[MAX_OFFSET + 1];
    inFamilies = new boolean[MAX_OFFSET + 1];

    buildBands();
    allocateSegmentBuffers();

    strokeCap(ROUND);
    strokeWeight(STROKE_WIDTH);
    noFill();
    previousInstant = millis() / 1000f;
  }

  private int countSeedsToFillCanvas() {
    float reach = 0;
    reach = max(reach, dist(0, 0, centreX, centreY));
    reach = max(reach, dist(CANVAS_WIDTH, 0, centreX, centreY));
    reach = max(reach, dist(0, CANVAS_HEIGHT, centreX, centreY));
    reach = max(reach, dist(CANVAS_WIDTH, CANVAS_HEIGHT, centreX, centreY));
    return (int) ceil(sq(reach * 1.06f / SPACING));
  }

  private void buildBands() {
    bandSeed = new int[BANDS];
    int last = max(2, seedCount - MAX_OFFSET - 1);
    for (int j = 0; j < BANDS; j++) {
      bandSeed[j] = round(pow(last, (float) j / (BANDS - 1)));
    }

    bandOf = new int[seedCount + 1];
    bandBlend = new float[seedCount + 1];
    for (int n = 1; n <= seedCount; n++) {
      int j = 0;
      while (j < BANDS - 2 && bandSeed[j + 1] <= n) {
        j++;
      }
      int lower = bandSeed[j];
      int upper = bandSeed[j + 1];
      bandOf[n] = j;
      bandBlend[n] = upper > lower ? constrain((float) (n - lower) / (upper - lower), 0, 1) : 0;
    }
  }

  private void allocateSegmentBuffers() {
    segmentX1 = new float[OPACITY_STEPS][seedCount];
    segmentY1 = new float[OPACITY_STEPS][seedCount];
    segmentX2 = new float[OPACITY_STEPS][seedCount];
    segmentY2 = new float[OPACITY_STEPS][seedCount];
    segmentCount = new int[OPACITY_STEPS];
  }

  public void draw() {
    float now = millis() / 1000f;
    float step = min(0.1f, now - previousInstant);
    previousInstant = now;
    if (running) {
      clock += step;
    }

    float alpha = sweepAngle(clock);
    placeSeeds(alpha);
    measureFamilies();
    collectSegments();
    paint();
  }

  private float sweepAngle(float t) {
    float s = sin(t / SWEEP_PERIOD * TWO_PI);
    return GOLDEN_ANGLE + SWEEP * s * s * s;
  }

  private void placeSeeds(float alpha) {
    int highest = seedCount + MAX_OFFSET + 1;
    for (int n = 1; n < highest; n++) {
      float radius = SPACING * sqrt(n);
      float angle = (float) ((n * (double) alpha) % TWO_PI);
      seedX[n] = centreX + cos(angle) * radius;
      seedY[n] = centreY + sin(angle) * radius;
    }
  }

  private void measureFamilies() {
    familyCount = 0;
    for (int k = 0; k <= MAX_OFFSET; k++) {
      inFamilies[k] = false;
      peakQuality[k] = 0;
    }

    for (int j = 0; j < BANDS; j++) {
      int n = bandSeed[j];
      float x = seedX[n];
      float y = seedY[n];

      float closest = Float.MAX_VALUE;
      for (int k = 1; k <= MAX_OFFSET; k++) {
        gap[k] = dist(seedX[n + k], seedY[n + k], x, y);
        closest = min(closest, gap[k]);
      }
      for (int k = 1; k <= MAX_OFFSET; k++) {
        float q = exp(-FALLOFF * (gap[k] / closest - 1));
        quality[k * BANDS + j] = q;
        peakQuality[k] = max(peakQuality[k], q);
      }
      takeBestOfBand(j);
    }
    sortFamiliesByPeak();
    familyCount = min(familyCount, MAX_FAMILIES);
  }

  private void takeBestOfBand(int band) {
    for (int pick = 0; pick < PICKS_PER_BAND; pick++) {
      int winner = -1;
      float best = -1;
      for (int k = 1; k <= MAX_OFFSET; k++) {
        if (alreadyPicked(k, pick)) {
          continue;
        }
        float q = quality[k * BANDS + band];
        if (q > best) {
          best = q;
          winner = k;
        }
      }
      if (winner < 0) {
        return;
      }
      picks[pick] = winner;
      if (!inFamilies[winner]) {
        inFamilies[winner] = true;
        families[familyCount++] = winner;
      }
    }
  }

  private boolean alreadyPicked(int k, int upTo) {
    for (int p = 0; p < upTo; p++) {
      if (picks[p] == k) {
        return true;
      }
    }
    return false;
  }

  private void sortFamiliesByPeak() {
    for (int i = 0; i < familyCount; i++) {
      for (int j = i + 1; j < familyCount; j++) {
        if (peakQuality[families[j]] > peakQuality[families[i]]) {
          int swap = families[i];
          families[i] = families[j];
          families[j] = swap;
        }
      }
    }
  }

  private void collectSegments() {
    for (int step = 0; step < OPACITY_STEPS; step++) {
      segmentCount[step] = 0;
    }
    for (int f = 0; f < familyCount; f++) {
      int k = families[f];
      for (int start = 1; start <= k; start++) {
        int previous = -1;
        for (int n = start; n <= seedCount; n += k) {
          if (previous > 0) {
            float weight = (weightAt(k, previous) + weightAt(k, n)) * 0.5f;
            if (weight >= MIN_OPACITY) {
              store(min(OPACITY_STEPS - 1, (int) (weight * OPACITY_STEPS)), previous, n);
            }
          }
          previous = n;
        }
      }
    }
  }

  private float weightAt(int k, int n) {
    int j = bandOf[n];
    float lower = quality[k * BANDS + j];
    float upper = quality[k * BANDS + min(BANDS - 1, j + 1)];
    return lower + (upper - lower) * bandBlend[n];
  }

  private void store(int step, int from, int to) {
    int at = segmentCount[step];
    if (at >= segmentX1[step].length) {
      return;
    }
    segmentX1[step][at] = seedX[from];
    segmentY1[step][at] = seedY[from];
    segmentX2[step][at] = seedX[to];
    segmentY2[step][at] = seedY[to];
    segmentCount[step] = at + 1;
  }

  private void paint() {
    background(PAPER_R, PAPER_G, PAPER_B);
    for (int step = 0; step < OPACITY_STEPS; step++) {
      int total = segmentCount[step];
      if (total == 0) {
        continue;
      }
      float opacity = ((step + 0.5f) / OPACITY_STEPS) * STROKE_ALPHA * 255;
      stroke(INK_R, INK_G, INK_B, opacity);
      for (int i = 0; i < total; i++) {
        line(segmentX1[step][i], segmentY1[step][i],
             segmentX2[step][i], segmentY2[step][i]);
      }
    }
  }

  public void mousePressed() {
    running = !running;
  }

  public static void main(String[] args) {
    PApplet.main("Parastichy");
  }
}
