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
  private static final float SWEEP_AMPLITUDE = radians(2.5f);
  private static final float SWEEP_PERIOD_SECONDS = 32;

  private static final float SEED_SPACING = 22;
  private static final int MAX_OFFSET = 140;
  private static final int BAND_COUNT = 48;
  private static final float QUALITY_FALLOFF = 6;
  private static final int PICKS_PER_BAND = 4;
  private static final int MAX_FAMILIES = 14;
  private static final float MIN_OPACITY = 0.02f;
  private static final int OPACITY_STEPS = 14;
  private static final float MAX_STROKE_OPACITY = 0.85f;
  private static final float STROKE_WIDTH = 1.3f;

  private float centreX;
  private float centreY;
  private int seedCount;

  private float[] seedX;
  private float[] seedY;

  private int[] seedAtBand;
  private int[] bandOfSeed;
  private float[] blendTowardNextBand;

  private float[] qualityByOffsetAndBand;
  private float[] distanceAtOffset;
  private float[] peakQualityOfOffset;
  private int[] bandPicks;

  private int[] families;
  private int familyCount;
  private boolean[] isFamily;

  private float[][] segmentX1;
  private float[][] segmentY1;
  private float[][] segmentX2;
  private float[][] segmentY2;
  private int[] segmentCount;

  private float sweepSeconds;
  private float previousSeconds;
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
    qualityByOffsetAndBand = new float[(MAX_OFFSET + 1) * BAND_COUNT];
    distanceAtOffset = new float[MAX_OFFSET + 1];
    peakQualityOfOffset = new float[MAX_OFFSET + 1];
    bandPicks = new int[PICKS_PER_BAND];
    families = new int[MAX_OFFSET + 1];
    isFamily = new boolean[MAX_OFFSET + 1];

    buildBands();
    allocateSegmentBuffers();

    strokeCap(ROUND);
    strokeWeight(STROKE_WIDTH);
    noFill();
    previousSeconds = millis() / 1000f;
  }

  private int countSeedsToFillCanvas() {
    float reach = 0;
    reach = max(reach, dist(0, 0, centreX, centreY));
    reach = max(reach, dist(CANVAS_WIDTH, 0, centreX, centreY));
    reach = max(reach, dist(0, CANVAS_HEIGHT, centreX, centreY));
    reach = max(reach, dist(CANVAS_WIDTH, CANVAS_HEIGHT, centreX, centreY));
    return (int) ceil(sq(reach * 1.06f / SEED_SPACING));
  }

  private void buildBands() {
    seedAtBand = new int[BAND_COUNT];
    int outermostBandSeed = max(2, seedCount - MAX_OFFSET - 1);
    for (int band = 0; band < BAND_COUNT; band++) {
      seedAtBand[band] = round(pow(outermostBandSeed, (float) band / (BAND_COUNT - 1)));
    }

    bandOfSeed = new int[seedCount + 1];
    blendTowardNextBand = new float[seedCount + 1];
    for (int n = 1; n <= seedCount; n++) {
      int band = 0;
      while (band < BAND_COUNT - 2 && seedAtBand[band + 1] <= n) {
        band++;
      }
      int lower = seedAtBand[band];
      int upper = seedAtBand[band + 1];
      bandOfSeed[n] = band;
      blendTowardNextBand[n] = upper > lower ? constrain((float) (n - lower) / (upper - lower), 0, 1) : 0;
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
    float elapsed = min(0.1f, now - previousSeconds);
    previousSeconds = now;
    if (running) {
      sweepSeconds += elapsed;
    }

    float alpha = sweepAngle(sweepSeconds);
    placeSeeds(alpha);
    measureFamilies();
    collectSegments();
    paintSegments();
  }

  private float sweepAngle(float seconds) {
    float wave = sin(seconds / SWEEP_PERIOD_SECONDS * TWO_PI);
    return GOLDEN_ANGLE + SWEEP_AMPLITUDE * wave * wave * wave;
  }

  private void placeSeeds(float alpha) {
    int seedLimit = seedCount + MAX_OFFSET + 1;
    for (int n = 1; n < seedLimit; n++) {
      float radius = SEED_SPACING * sqrt(n);
      float angle = (float) ((n * (double) alpha) % TWO_PI);
      seedX[n] = centreX + cos(angle) * radius;
      seedY[n] = centreY + sin(angle) * radius;
    }
  }

  private void measureFamilies() {
    familyCount = 0;
    for (int k = 0; k <= MAX_OFFSET; k++) {
      isFamily[k] = false;
      peakQualityOfOffset[k] = 0;
    }

    for (int band = 0; band < BAND_COUNT; band++) {
      int n = seedAtBand[band];
      float x = seedX[n];
      float y = seedY[n];

      float closest = Float.MAX_VALUE;
      for (int k = 1; k <= MAX_OFFSET; k++) {
        distanceAtOffset[k] = dist(seedX[n + k], seedY[n + k], x, y);
        closest = min(closest, distanceAtOffset[k]);
      }
      for (int k = 1; k <= MAX_OFFSET; k++) {
        float offsetQuality = exp(-QUALITY_FALLOFF * (distanceAtOffset[k] / closest - 1));
        qualityByOffsetAndBand[k * BAND_COUNT + band] = offsetQuality;
        peakQualityOfOffset[k] = max(peakQualityOfOffset[k], offsetQuality);
      }
      pickBestOffsetsOfBand(band);
    }
    sortFamiliesByPeak();
    familyCount = min(familyCount, MAX_FAMILIES);
  }

  private void pickBestOffsetsOfBand(int band) {
    for (int pick = 0; pick < PICKS_PER_BAND; pick++) {
      int winner = -1;
      float best = -1;
      for (int k = 1; k <= MAX_OFFSET; k++) {
        if (isAlreadyPicked(k, pick)) {
          continue;
        }
        float offsetQuality = qualityByOffsetAndBand[k * BAND_COUNT + band];
        if (offsetQuality > best) {
          best = offsetQuality;
          winner = k;
        }
      }
      if (winner < 0) {
        return;
      }
      bandPicks[pick] = winner;
      if (!isFamily[winner]) {
        isFamily[winner] = true;
        families[familyCount++] = winner;
      }
    }
  }

  private boolean isAlreadyPicked(int k, int pickCount) {
    for (int p = 0; p < pickCount; p++) {
      if (bandPicks[p] == k) {
        return true;
      }
    }
    return false;
  }

  private void sortFamiliesByPeak() {
    for (int i = 0; i < familyCount; i++) {
      for (int j = i + 1; j < familyCount; j++) {
        if (peakQualityOfOffset[families[j]] > peakQualityOfOffset[families[i]]) {
          int swap = families[i];
          families[i] = families[j];
          families[j] = swap;
        }
      }
    }
  }

  private void collectSegments() {
    for (int opacityStep = 0; opacityStep < OPACITY_STEPS; opacityStep++) {
      segmentCount[opacityStep] = 0;
    }
    for (int familyIndex = 0; familyIndex < familyCount; familyIndex++) {
      int k = families[familyIndex];
      for (int start = 1; start <= k; start++) {
        int previous = -1;
        for (int n = start; n <= seedCount; n += k) {
          if (previous > 0) {
            float weight = (segmentWeightAt(k, previous) + segmentWeightAt(k, n)) * 0.5f;
            if (weight >= MIN_OPACITY) {
              storeSegment(min(OPACITY_STEPS - 1, (int) (weight * OPACITY_STEPS)), previous, n);
            }
          }
          previous = n;
        }
      }
    }
  }

  private float segmentWeightAt(int k, int n) {
    int band = bandOfSeed[n];
    float lower = qualityByOffsetAndBand[k * BAND_COUNT + band];
    float upper = qualityByOffsetAndBand[k * BAND_COUNT + min(BAND_COUNT - 1, band + 1)];
    return lower + (upper - lower) * blendTowardNextBand[n];
  }

  private void storeSegment(int opacityStep, int from, int to) {
    int nextSlot = segmentCount[opacityStep];
    if (nextSlot >= segmentX1[opacityStep].length) {
      return;
    }
    segmentX1[opacityStep][nextSlot] = seedX[from];
    segmentY1[opacityStep][nextSlot] = seedY[from];
    segmentX2[opacityStep][nextSlot] = seedX[to];
    segmentY2[opacityStep][nextSlot] = seedY[to];
    segmentCount[opacityStep] = nextSlot + 1;
  }

  private void paintSegments() {
    background(PAPER_R, PAPER_G, PAPER_B);
    for (int opacityStep = 0; opacityStep < OPACITY_STEPS; opacityStep++) {
      int segmentsInStep = segmentCount[opacityStep];
      if (segmentsInStep == 0) {
        continue;
      }
      float opacity = ((opacityStep + 0.5f) / OPACITY_STEPS) * MAX_STROKE_OPACITY * 255;
      stroke(INK_R, INK_G, INK_B, opacity);
      for (int segment = 0; segment < segmentsInStep; segment++) {
        line(segmentX1[opacityStep][segment], segmentY1[opacityStep][segment],
             segmentX2[opacityStep][segment], segmentY2[opacityStep][segment]);
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
