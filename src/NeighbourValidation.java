import java.util.Arrays;
import java.util.Locale;

public class NeighbourValidation {

  private static final int ALPHA_SAMPLES = 33;
  private static final float MIDPOINT = 0.5f;
  private static final int NEIGHBOUR_RANKS = 3;
  private static final int CONTACT_RANKS = 2;
  private static final double CONTACT_FAMILY_SHARE = 0.02;
  private static final double MAX_WEIGHT_ERROR = 0.03;
  private static final double PERCENT = 100;

  private final Parastichy sketch = new Parastichy();
  private final int seedCount = sketch.seedCount;
  private final int viewSeedCount = sketch.measuredSeedCount;
  private final int[][] neighbours = new int[seedCount + 1][NEIGHBOUR_RANKS];
  private final double[] nearestDistance = new double[seedCount + 1];
  private final boolean[] isDrawnFamily = new boolean[Parastichy.MAX_OFFSET + 1];
  private double alpha;

  public static void main(String[] args) {
    System.exit(new NeighbourValidation().run() ? 0 : 1);
  }

  private boolean run() {
    sketch.buildBands();
    System.out.printf(Locale.ROOT, "Starting view: seeds 1 to %d. Whole field: seeds 1 to %d.%n",
        viewSeedCount, seedCount);
    System.out.printf(Locale.ROOT, "Columns give the share of each seed's true 1st, 2nd and 3rd nearest neighbour%n");
    System.out.printf(Locale.ROOT, "links that the sketch draws, in the starting view.%n%n");
    System.out.printf(Locale.ROOT, "%9s %7s %7s %7s %10s %10s   %s%n",
        "alpha", "1st", "2nd", "3rd", "precision", "weight err", "drawn families | exact contact families");

    Tally viewTotal = new Tally();
    Tally fieldTotal = new Tally();
    for (int sample = 0; sample < ALPHA_SAMPLES; sample++) {
      float seconds = ((float) sample / (ALPHA_SAMPLES - 1) - MIDPOINT) * Parastichy.SWEEP_PERIOD_SECONDS / 2;
      prepare(sketch.sweepAngle(seconds));

      Tally view = measure(viewSeedCount);
      Tally field = measure(seedCount);
      viewTotal.add(view);
      fieldTotal.add(field);

      System.out.printf(Locale.ROOT, "%+8.3f° %7s %7s %7s %10s %10.3f   %s | %s%n",
          Math.toDegrees(alpha - Parastichy.GOLDEN_ANGLE),
          percent(view.recall(0)), percent(view.recall(1)), percent(view.recall(2)),
          percent(view.precision()), view.weightError(),
          drawnFamilies(), contactFamilies(viewSeedCount));
    }

    System.out.printf(Locale.ROOT, "%nAll samples       %7s %7s %7s %10s %10s   %s%n",
        "1st", "2nd", "3rd", "precision", "weight err",
        "missed contact links: offset > " + Parastichy.MAX_OFFSET + " / not picked / faded");
    printTotal("starting view", viewTotal);
    printTotal("whole field", fieldTotal);
    long lostByBands = viewTotal.missNotPicked + viewTotal.missFaded;
    double weightError = viewTotal.weightError();
    System.out.printf(Locale.ROOT, "%nStarting view: contact links lost by the bands %d (required 0),"
        + " weight error %.3f (required at most %.3f).%n", lostByBands, weightError, MAX_WEIGHT_ERROR);
    return lostByBands == 0 && weightError <= MAX_WEIGHT_ERROR;
  }

  private void printTotal(String name, Tally total) {
    System.out.printf(Locale.ROOT, "  %-15s %7s %7s %7s %10s %10.3f   %d / %d / %d%n", name,
        percent(total.recall(0)), percent(total.recall(1)), percent(total.recall(2)),
        percent(total.precision()), total.weightError(),
        total.missBeyondMaxOffset, total.missNotPicked, total.missFaded);
  }

  private void prepare(float sweepAlpha) {
    alpha = sweepAlpha;
    sketch.placeSeeds(sweepAlpha);
    sketch.measureFamilies();
    Arrays.fill(isDrawnFamily, false);
    for (int i = 0; i < sketch.familyCount; i++) {
      isDrawnFamily[sketch.families[i]] = true;
    }
    for (int n = 1; n <= seedCount; n++) {
      findNearestNeighbours(n);
    }
  }

  private Tally measure(int lastSeed) {
    Tally tally = new Tally();
    for (int n = 1; n <= lastSeed; n++) {
      for (int rank = 0; rank < NEIGHBOUR_RANKS; rank++) {
        int m = neighbours[n][rank];
        if (m > lastSeed) {
          continue;
        }
        tally.checked[rank]++;
        int k = Math.abs(m - n);
        if (drawnWeight(Math.min(n, m), Math.max(n, m)) > 0) {
          tally.drawn[rank]++;
        } else if (rank < CONTACT_RANKS) {
          if (k > Parastichy.MAX_OFFSET) {
            tally.missBeyondMaxOffset++;
          } else if (!isDrawnFamily[k]) {
            tally.missNotPicked++;
          } else {
            tally.missFaded++;
          }
        }
      }
    }

    for (int k = 1; k <= Parastichy.MAX_OFFSET; k++) {
      if (!isDrawnFamily[k]) {
        continue;
      }
      for (int a = 1; a + k <= lastSeed; a++) {
        float weight = drawnWeight(a, a + k);
        tally.drawnOpacity += weight;
        if (isNeighbour(a, a + k) || isNeighbour(a + k, a)) {
          tally.opacityOnNeighbourLinks += weight;
        }
      }
    }

    for (int n = 1; n <= lastSeed; n++) {
      for (int k = 1; k <= Parastichy.MAX_OFFSET; k++) {
        double exact = Math.exp(-Parastichy.QUALITY_FALLOFF * (distance(n, n + k) / nearestDistance[n] - 1));
        double measured = isDrawnFamily[k] ? sketch.segmentWeightAt(k, n) : 0;
        if (Math.max(exact, measured) >= Parastichy.MIN_OPACITY) {
          tally.weightErrorSum += Math.abs(exact - measured);
          tally.weightPairs++;
        }
      }
    }
    return tally;
  }

  private void findNearestNeighbours(int n) {
    double[] bestDistance = new double[NEIGHBOUR_RANKS];
    int[] bestSeed = neighbours[n];
    Arrays.fill(bestDistance, Double.MAX_VALUE);
    double radius = radius(n);

    for (int m = n - 1; m >= 1 && radius - radius(m) <= bestDistance[NEIGHBOUR_RANKS - 1]; m--) {
      offerCandidate(bestDistance, bestSeed, m, distance(n, m));
    }
    for (int m = n + 1; radius(m) - radius <= bestDistance[NEIGHBOUR_RANKS - 1]; m++) {
      offerCandidate(bestDistance, bestSeed, m, distance(n, m));
    }
    nearestDistance[n] = bestDistance[0];
  }

  private static void offerCandidate(double[] bestDistance, int[] bestSeed, int seed, double d) {
    int slot = NEIGHBOUR_RANKS;
    while (slot > 0 && d < bestDistance[slot - 1]) {
      slot--;
    }
    if (slot == NEIGHBOUR_RANKS) {
      return;
    }
    for (int i = NEIGHBOUR_RANKS - 1; i > slot; i--) {
      bestDistance[i] = bestDistance[i - 1];
      bestSeed[i] = bestSeed[i - 1];
    }
    bestDistance[slot] = d;
    bestSeed[slot] = seed;
  }

  private float drawnWeight(int a, int b) {
    int k = b - a;
    if (k > Parastichy.MAX_OFFSET || !isDrawnFamily[k]) {
      return 0;
    }
    float weight = (sketch.segmentWeightAt(k, a) + sketch.segmentWeightAt(k, b)) / 2;
    return weight >= Parastichy.MIN_OPACITY ? weight : 0;
  }

  private boolean isNeighbour(int n, int m) {
    for (int rank = 0; rank < NEIGHBOUR_RANKS; rank++) {
      if (neighbours[n][rank] == m) {
        return true;
      }
    }
    return false;
  }

  private String drawnFamilies() {
    StringBuilder text = new StringBuilder();
    for (int k = 1; k <= Parastichy.MAX_OFFSET; k++) {
      if (isDrawnFamily[k]) {
        text.append(text.length() > 0 ? " " : "").append(k);
      }
    }
    return text.toString();
  }

  private String contactFamilies(int lastSeed) {
    int[] links = new int[lastSeed + 1];
    for (int n = 1; n <= lastSeed; n++) {
      for (int rank = 0; rank < CONTACT_RANKS; rank++) {
        links[Math.abs(neighbours[n][rank] - n)]++;
      }
    }
    StringBuilder text = new StringBuilder();
    for (int k = 1; k <= lastSeed; k++) {
      if (links[k] >= CONTACT_FAMILY_SHARE * CONTACT_RANKS * lastSeed) {
        text.append(text.length() > 0 ? " " : "").append(k);
      }
    }
    return text.toString();
  }

  private double radius(int n) {
    return Parastichy.SEED_SPACING * Math.sqrt(n);
  }

  private double distance(int n, int m) {
    double rn = radius(n);
    double rm = radius(m);
    return Math.sqrt(rn * rn + rm * rm - 2 * rn * rm * Math.cos((m - n) * alpha));
  }

  private static String percent(double fraction) {
    return String.format(Locale.ROOT, "%.1f%%", fraction * PERCENT);
  }

  private static final class Tally {
    final long[] checked = new long[NEIGHBOUR_RANKS];
    final long[] drawn = new long[NEIGHBOUR_RANKS];
    long missBeyondMaxOffset;
    long missNotPicked;
    long missFaded;
    double drawnOpacity;
    double opacityOnNeighbourLinks;
    double weightErrorSum;
    long weightPairs;

    double recall(int rank) {
      return checked[rank] > 0 ? (double) drawn[rank] / checked[rank] : 1;
    }

    double precision() {
      return drawnOpacity > 0 ? opacityOnNeighbourLinks / drawnOpacity : 1;
    }

    double weightError() {
      return weightPairs > 0 ? weightErrorSum / weightPairs : 0;
    }

    void add(Tally other) {
      for (int rank = 0; rank < NEIGHBOUR_RANKS; rank++) {
        checked[rank] += other.checked[rank];
        drawn[rank] += other.drawn[rank];
      }
      missBeyondMaxOffset += other.missBeyondMaxOffset;
      missNotPicked += other.missNotPicked;
      missFaded += other.missFaded;
      drawnOpacity += other.drawnOpacity;
      opacityOnNeighbourLinks += other.opacityOnNeighbourLinks;
      weightErrorSum += other.weightErrorSum;
      weightPairs += other.weightPairs;
    }
  }
}
