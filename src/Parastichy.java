import processing.core.PApplet;

public class Parastichy extends PApplet {

  private static final int CANVAS_WIDTH = 1200;
  private static final int CANVAS_HEIGHT = 742;
  private static final float CENTRE_X_RATIO = 0.32f;
  private static final float CENTRE_Y_RATIO = 0.46f;
  private static final float CANVAS_REACH_MARGIN = 1.06f;

  private static final float PAPER_R = 247, PAPER_G = 248, PAPER_B = 249;
  private static final float INK_R = 22, INK_G = 26, INK_B = 30;

  private static final double PHI = (1 + Math.sqrt(5)) / 2;
  static final float GOLDEN_ANGLE = (float) (TWO_PI / (PHI * PHI));
  private static final float SWEEP_AMPLITUDE = radians(2.5f);
  static final float SWEEP_PERIOD_SECONDS = 32;

  static final float SEED_SPACING = 22;
  static final int MAX_OFFSET = 140;
  private static final int OFFSET_SLOTS = MAX_OFFSET + 1;
  private static final int SEED_BUFFER_PADDING = MAX_OFFSET + 2;
  private static final int BAND_COUNT = 48;
  static final float QUALITY_FALLOFF = 6;
  private static final int PICKS_PER_BAND = 4;
  private static final int MAX_FAMILIES = 14;
  static final float MIN_OPACITY = 0.02f;
  private static final int OPACITY_STEPS = 14;
  private static final float MAX_STROKE_OPACITY = 0.85f;
  private static final float STROKE_WIDTH = 1.3f;
  private static final float MIDPOINT = 0.5f;
  private static final int MAX_COLOR_CHANNEL = 255;
  private static final float MILLIS_PER_SECOND = 1000f;
  private static final float MAX_ELAPSED_SECONDS = 0.1f;
  private static final int MIN_OUTERMOST_BAND_SEED = 2;

  private float centreX = CANVAS_WIDTH * CENTRE_X_RATIO;
  private float centreY = CANVAS_HEIGHT * CENTRE_Y_RATIO;
  final int measuredSeedCount = countSeedsToCover(reachFromStartingCentre());
  final int seedCount = countSeedsToCover(dist(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT));

  private final float[] seedX = new float[seedCount + SEED_BUFFER_PADDING];
  private final float[] seedY = new float[seedCount + SEED_BUFFER_PADDING];

  private final int[] seedAtBand = new int[BAND_COUNT];
  private final int[] bandOfSeed = new int[seedCount + 1];
  private final float[] blendTowardNextBand = new float[seedCount + 1];

  private final float[] qualityByOffsetAndBand = new float[OFFSET_SLOTS * BAND_COUNT];
  private final float[] distanceAtOffset = new float[OFFSET_SLOTS];
  private final float[] peakQualityOfOffset = new float[OFFSET_SLOTS];
  private final int[] bandPicks = new int[PICKS_PER_BAND];

  final int[] families = new int[OFFSET_SLOTS];
  int familyCount;
  private final boolean[] isFamily = new boolean[OFFSET_SLOTS];

  private final float[][] segmentX1 = new float[OPACITY_STEPS][seedCount];
  private final float[][] segmentY1 = new float[OPACITY_STEPS][seedCount];
  private final float[][] segmentX2 = new float[OPACITY_STEPS][seedCount];
  private final float[][] segmentY2 = new float[OPACITY_STEPS][seedCount];
  private final int[] segmentCount = new int[OPACITY_STEPS];

  private float sweepSeconds;
  private float previousSeconds;
  private boolean running = true;

  public void settings() {
    size(CANVAS_WIDTH, CANVAS_HEIGHT);
  }

  public void setup() {
    buildBands();

    strokeCap(ROUND);
    strokeWeight(STROKE_WIDTH);
    noFill();
    previousSeconds = millis() / MILLIS_PER_SECOND;
  }

  private float reachFromStartingCentre() {
    float reach = 0;
    reach = max(reach, dist(0, 0, centreX, centreY));
    reach = max(reach, dist(CANVAS_WIDTH, 0, centreX, centreY));
    reach = max(reach, dist(0, CANVAS_HEIGHT, centreX, centreY));
    reach = max(reach, dist(CANVAS_WIDTH, CANVAS_HEIGHT, centreX, centreY));
    return reach;
  }

  private int countSeedsToCover(float reach) {
    return ceil(sq(reach * CANVAS_REACH_MARGIN / SEED_SPACING));
  }

  void buildBands() {
    int outermostBandSeed = max(MIN_OUTERMOST_BAND_SEED, measuredSeedCount - OFFSET_SLOTS);
    for (int band = 0; band < BAND_COUNT; band++) {
      seedAtBand[band] = round(pow(outermostBandSeed, (float) band / (BAND_COUNT - 1)));
    }

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

  public void draw() {
    float now = millis() / MILLIS_PER_SECOND;
    float elapsed = min(MAX_ELAPSED_SECONDS, now - previousSeconds);
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

  float sweepAngle(float seconds) {
    float wave = sin(seconds / SWEEP_PERIOD_SECONDS * TWO_PI);
    return GOLDEN_ANGLE + SWEEP_AMPLITUDE * wave * wave * wave;
  }

  void placeSeeds(float alpha) {
    int seedLimit = seedCount + OFFSET_SLOTS;
    for (int n = 1; n < seedLimit; n++) {
      float radius = SEED_SPACING * sqrt(n);
      float angle = (float) ((n * (double) alpha) % TWO_PI);
      seedX[n] = centreX + cos(angle) * radius;
      seedY[n] = centreY + sin(angle) * radius;
    }
  }

  void measureFamilies() {
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
            float weight = (segmentWeightAt(k, previous) + segmentWeightAt(k, n)) * MIDPOINT;
            if (weight >= MIN_OPACITY) {
              storeSegment(min(OPACITY_STEPS - 1, (int) (weight * OPACITY_STEPS)), previous, n);
            }
          }
          previous = n;
        }
      }
    }
  }

  float segmentWeightAt(int k, int n) {
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
      float opacity = ((opacityStep + MIDPOINT) / OPACITY_STEPS) * MAX_STROKE_OPACITY * MAX_COLOR_CHANNEL;
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

  public void mouseMoved() {
    centreOnMouse();
  }

  public void mouseDragged() {
    centreOnMouse();
  }

  private void centreOnMouse() {
    centreX = mouseX;
    centreY = mouseY;
  }

  public static void main(String[] args) {
    PApplet.main("Parastichy");
  }
}
