package com.mystudies;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import com.motivewave.platform.sdk.common.DataContext;
import com.motivewave.platform.sdk.common.DataSeries;
import com.motivewave.platform.sdk.common.Defaults;
import com.motivewave.platform.sdk.common.DrawContext;
import com.motivewave.platform.sdk.common.Enums;
import com.motivewave.platform.sdk.common.FontInfo;
import com.motivewave.platform.sdk.common.Instrument;
import com.motivewave.platform.sdk.common.NVP;
import com.motivewave.platform.sdk.common.Tick;
import com.motivewave.platform.sdk.common.TickOperation;
import com.motivewave.platform.sdk.common.Util;
import com.motivewave.platform.sdk.common.X11Colors;
import com.motivewave.platform.sdk.common.desc.BooleanDescriptor;
import com.motivewave.platform.sdk.common.desc.DiscreteDescriptor;
import com.motivewave.platform.sdk.common.desc.DoubleDescriptor;
import com.motivewave.platform.sdk.common.desc.EnabledDependency;
import com.motivewave.platform.sdk.common.desc.FontDescriptor;
import com.motivewave.platform.sdk.common.desc.IndicatorDescriptor;
import com.motivewave.platform.sdk.common.desc.IntegerDescriptor;
import com.motivewave.platform.sdk.common.desc.PathDescriptor;
import com.motivewave.platform.sdk.common.desc.ShadeDescriptor;
import com.motivewave.platform.sdk.common.desc.ValueDescriptor;
import com.motivewave.platform.sdk.draw.Figure;
import com.motivewave.platform.sdk.study.Study;
import com.motivewave.platform.sdk.study.StudyHeader;

/**
 * VWAP Suite: Daily / Weekly / Monthly VWAP with +-1/+-2 std dev bands and floating right-edge labels.
 * Calculation follows MotiveWave's built-in VWAP.java: tick based VWAP (sum(price*vol)/sum(vol), restarted each period)
 * and the Sierra Chart style volume weighted deviation of bar HLC3 from the VWAP.
 * The band deviation is evaluated with running sums (algebraically identical to the original per-bar loop, but O(1) per bar).
 */
@StudyHeader(
    namespace="com.custom",
    id="CUSTOM_VWAP_SUITE",
    name="VWAP Suite (D/W/M)",
    desc="Daily, Weekly and Monthly VWAP with standard deviation bands and floating labels",
    label="VWAP Suite",
    menu="Custom",
    overlay=true,
    requiresVolume=true,
    requiresBarUpdates=true,
    studyOverlay=true)
public class CustomVWAPSuite extends Study
{
  // Per period: VWAP, +1, -1, +2, -2
  enum Values { D_VWAP, D_T1, D_B1, D_T2, D_B2, W_VWAP, W_T1, W_B1, W_T2, W_B2, M_VWAP, M_T1, M_B1, M_T2, M_B2 }

  static final Values[][] VAL = {
    { Values.D_VWAP, Values.D_T1, Values.D_B1, Values.D_T2, Values.D_B2 },
    { Values.W_VWAP, Values.W_T1, Values.W_B1, Values.W_T2, Values.W_B2 },
    { Values.M_VWAP, Values.M_T1, Values.M_B1, Values.M_T2, Values.M_B2 }
  };
  static final String[] ID = { "D", "W", "M" };
  static final String[] NAME = { "Daily", "Weekly", "Monthly" };
  static final String[] PFX = { "d", "w", "m" };

  static final String RTH="rth", USE_HISTORICAL_BARS="useBars", LOOKBACK="lookback", MATCH_ORIGINAL="matchOriginal";
  static final String SHOW_LABELS="showLabels", SHOW_BAND_LABELS="showBandLabels", SHOW_PRICE="showPrice", LABEL_FONT="labelFont", LABEL_OFFSET="labelOffset";
  static final String VAL_RTH="R", VAL_EXT="E", VAL_CHART="C";

  // Setting keys, per period p: k(p,"PATH") main line, "IND" axis tag, "PATH1"/"PATH2" bands, "STD1"/"STD2", etc.
  static String k(int p, String s) { return ID[p] + "_" + s; }

  @Override
  public void initialize(Defaults defaults)
  {
    var sd = createSD();
    List<NVP> rthOptions = new ArrayList<>();
    rthOptions.add(new NVP("RTH (9:30 ET)", VAL_RTH));
    rthOptions.add(new NVP("Extended (18:00 ET)", VAL_EXT));
    rthOptions.add(new NVP("Chart Setting", VAL_CHART));

    var tab = sd.addTab("General");
    var grp = tab.addGroup("", false);
    grp.addRow(new DiscreteDescriptor(RTH, "Session", VAL_EXT, rthOptions));
    grp.addRow(new BooleanDescriptor(USE_HISTORICAL_BARS, "Use Historical Bars", true, false));
    grp.addRow(new BooleanDescriptor(MATCH_ORIGINAL, "Match MotiveWave VWAP Bands (skip 1st bar of period)", true, false));
    grp.addRow(new IntegerDescriptor(LOOKBACK, "History Lookback (days)", 90, 35, 3650, 1));

    tab = sd.addTab("Labels");
    grp = tab.addGroup("", false);
    grp.addRow(new BooleanDescriptor(SHOW_LABELS, "Show Labels", true, false));
    grp.addRow(new BooleanDescriptor(SHOW_BAND_LABELS, "Label Std Dev Bands", true, false));
    List<NVP> priceOptions = new ArrayList<>();
    priceOptions.add(new NVP("All Levels", "A"));
    priceOptions.add(new NVP("VWAP Lines Only", "V"));
    priceOptions.add(new NVP("None", "N"));
    grp.addRow(new DiscreteDescriptor(SHOW_PRICE, "Show Price In Label", "A", priceOptions));
    grp.addRow(new FontDescriptor(LABEL_FONT, "Label Font", defaults.getFont().deriveFont(Font.BOLD, 11f)));
    grp.addRow(new IntegerDescriptor(LABEL_OFFSET, "Label Offset From Right Edge (px)", 8, 0, 500, 1));

    Color[] colors = { defaults.getBlue(), defaults.getRed(), defaults.getOrange() };

    for(int p = 0; p < 3; p++) {
      Color c = colors[p];
      tab = sd.addTab(NAME[p]);

      grp = tab.addGroup("VWAP", false);
      var pdesc = new PathDescriptor(k(p,"PATH"), "Line", c, 1.0f, null, true, true, true);
      pdesc.setContinuous(false);
      grp.addRow(pdesc);
      grp.addRow(new IndicatorDescriptor(k(p,"IND"), "Indicator", c, X11Colors.WHITE, false, false, true));

      grp = tab.addGroup("Band 1", false);
      pdesc = new PathDescriptor(k(p,"PATH1"), "Line", c, 1.0f, null, true, false, true);
      pdesc.setContinuous(false);
      grp.addRow(pdesc);
      grp.addRow(new DoubleDescriptor(k(p,"STD1"), "Std Dev", 1.0, 0.1, 999, 0.1));
      grp.addRow(new IndicatorDescriptor(k(p,"TOP_IND1"), "Top Indicator", c, X11Colors.WHITE, false, false, true));
      grp.addRow(new IndicatorDescriptor(k(p,"BOT_IND1"), "Bottom Indicator", c, X11Colors.WHITE, false, false, true));
      grp.addRow(new ShadeDescriptor(k(p,"FILL1"), "Fill Color", k(p,"PATH1"), k(p,"PATH1"), Enums.ShadeType.BOTH, defaults.getFillColor(), false, true));

      grp = tab.addGroup("Band 2", false);
      pdesc = new PathDescriptor(k(p,"PATH2"), "Line", c, 1.0f, null, true, false, true);
      pdesc.setContinuous(false);
      grp.addRow(pdesc);
      grp.addRow(new DoubleDescriptor(k(p,"STD2"), "Std Dev", 2.0, 0.1, 999, 0.1));
      grp.addRow(new IndicatorDescriptor(k(p,"TOP_IND2"), "Top Indicator", c, X11Colors.WHITE, false, false, true));
      grp.addRow(new IndicatorDescriptor(k(p,"BOT_IND2"), "Bottom Indicator", c, X11Colors.WHITE, false, false, true));
      grp.addRow(new ShadeDescriptor(k(p,"FILL2"), "Fill Color",
          new String[][] { { VAL[p][1].name(), VAL[p][3].name() }, { VAL[p][2].name(), VAL[p][4].name() } }, Util.getAlphaFill(c), false, true));

      sd.addDependency(new EnabledDependency(k(p,"PATH1"), k(p,"PATH2"), k(p,"STD1"), k(p,"TOP_IND1"), k(p,"BOT_IND1"), k(p,"FILL1")));
      var dep = new EnabledDependency(k(p,"PATH2"), k(p,"STD2"), k(p,"TOP_IND2"), k(p,"BOT_IND2"), k(p,"FILL2"));
      dep.setSource2(k(p,"PATH1"));
      sd.addDependency(dep);
    }

    sd.addQuickSettings(RTH, k(0,"PATH"), k(1,"PATH"), k(2,"PATH"));

    var desc = createRD();
    desc.setLabelSettings(RTH);
    for(int p = 0; p < 3; p++) {
      var v = VAL[p];
      desc.exportValue(new ValueDescriptor(v[0], NAME[p] + " VWAP", new String[] { RTH }));
      desc.exportValue(new ValueDescriptor(v[1], NAME[p] + " VWAP Top 1", new String[] { k(p,"STD1") }));
      desc.exportValue(new ValueDescriptor(v[2], NAME[p] + " VWAP Bottom 1", new String[] { k(p,"STD1") }));
      desc.exportValue(new ValueDescriptor(v[3], NAME[p] + " VWAP Top 2", new String[] { k(p,"STD2") }));
      desc.exportValue(new ValueDescriptor(v[4], NAME[p] + " VWAP Bottom 2", new String[] { k(p,"STD2") }));
      desc.declarePath(v[0], k(p,"PATH"));
      desc.declareIndicator(v[0], k(p,"IND"));
      desc.declarePath(v[1], k(p,"PATH1"));
      desc.declareIndicator(v[1], k(p,"TOP_IND1"));
      desc.declarePath(v[2], k(p,"PATH1"));
      desc.declareIndicator(v[2], k(p,"BOT_IND1"));
      desc.declarePath(v[3], k(p,"PATH2"));
      desc.declareIndicator(v[3], k(p,"TOP_IND2"));
      desc.declarePath(v[4], k(p,"PATH2"));
      desc.declareIndicator(v[4], k(p,"BOT_IND2"));
    }
    desc.setRangeKeys(Values.D_VWAP, Values.W_VWAP, Values.M_VWAP);
  }

  @Override
  public void clearState()
  {
    super.clearState();
    calculated = false; // force a recalculation
    engine = null;
  }

  @Override
  public void onTick(DataContext ctx, Tick tick)
  {
    var e = engine;
    if (calcInProgress || e == null) return;
    e.onTick(tick);
  }

  @Override
  protected void calculateValues(DataContext ctx)
  {
    var series = ctx.getDataSeries();
    if (series.size() == 0 || calcInProgress || calculated) return;
    calculated = true;
    clearFigures();
    addFigure(new LevelLabels());
    Util.schedule(() -> calculate(ctx));
  }

  private void calculate(DataContext ctx)
  {
    var series = ctx.getDataSeries();
    if (series.size() == 0) return;
    calcInProgress = true;
    try {
      var settings = getSettings();
      var instr = series.getInstrument();

      boolean rth = ctx.isRTH();
      String rthData = settings.getString(RTH);
      if (Util.compare(rthData, VAL_RTH)) rth = true;
      else if (Util.compare(rthData, VAL_EXT)) rth = false;

      var eng = new Engine(series, instr, rth);
      eng.skipFirst = settings.getBoolean(MATCH_ORIGINAL, true);
      for(int p = 0; p < 3; p++) {
        var path = settings.getPath(k(p,"PATH"));
        eng.on[p] = path != null && path.isEnabled();
        var p1 = settings.getPath(k(p,"PATH1"));
        eng.band1[p] = eng.on[p] && p1 != null && p1.isEnabled();
        var p2 = settings.getPath(k(p,"PATH2"));
        eng.band2[p] = eng.band1[p] && p2 != null && p2.isEnabled();
        eng.std1[p] = settings.getDouble(k(p,"STD1"));
        eng.std2[p] = settings.getDouble(k(p,"STD2"));
      }

      // Replay ticks from the start of the month that contains (now - lookback). Monthly VWAP needs the full month.
      int lookback = settings.getInteger(LOOKBACK, 90);
      long from = instr.getStartOfMonth(ctx.getCurrentTime() - lookback * Util.MILLIS_IN_DAY, rth);
      long first = series.getStartTime(0);
      long start = Math.max(from, first);
      int ind = series.findIndex(start);
      eng.ind = Math.max(ind, 0);

      instr.forEachTick(start, ctx.getCurrentTime() + 5*Util.MILLIS_IN_MINUTE, rth, settings.getBoolean(USE_HISTORICAL_BARS, true), eng);
      eng.flush(eng.ind);
      engine = eng;
    }
    finally {
      calcInProgress = false;
    }
    notifyRedraw();
  }

  private volatile boolean calcInProgress = false, calculated = false;
  private volatile Engine engine;

  /** Tick processor: maintains VWAP + band accumulators for Daily, Weekly and Monthly in a single pass. */
  private class Engine implements TickOperation
  {
    final DataSeries series;
    final Instrument instr;
    final boolean rth;
    final boolean[] on = new boolean[3], band1 = new boolean[3], band2 = new boolean[3];
    final double[] std1 = new double[3], std2 = new double[3];
    final long[] end = new long[3];                         // end of the current period
    final double[] totalPrice = new double[3], totalVol = new double[3];  // tick based VWAP sums
    final double[] bv = new double[3], bvp = new double[3], bvp2 = new double[3]; // completed bar sums for bands
    final double[] curBarVol = new double[3];               // tick volume seen in the current bar (per period)
    final int[] firstBar = new int[3];                      // index of the first bar of the current period
    boolean skipFirst;
    int ind;

    Engine(DataSeries series, Instrument instr, boolean rth)
    {
      this.series = series;
      this.instr = instr;
      this.rth = rth;
    }

    @Override
    public void onTick(Tick tick)
    {
      long t = tick.getTime();
      // Advance the bar index (filling any bars that had no ticks)
      while (ind < series.size()-1 && t >= series.getEndTime(ind)) {
        flush(ind);
        finishBar(ind);
        ind++;
      }

      float vol = tick.getVolumeAsFloat();
      for(int p = 0; p < 3; p++) {
        if (!on[p]) continue;
        if (t >= end[p]) newPeriod(p, t);
        totalPrice[p] += tick.getPrice() * vol;
        totalVol[p] += vol;
        curBarVol[p] += vol;
      }
      if (ind >= series.size()-1) flush(ind); // live bar: update on every tick
    }

    void newPeriod(int p, long t)
    {
      long s, e;
      if (p == 0) { s = instr.getStartOfDay(t, rth); e = instr.getEndOfDay(t, rth); }
      else if (p == 1) { s = instr.getStartOfWeek(t, rth); e = instr.getEndOfWeek(t, rth); }
      else { s = instr.getStartOfMonth(t, rth); e = instr.getEndOfMonth(t, rth); }
      if (e <= t) e = t + Util.MILLIS_IN_MINUTE; // safety
      end[p] = e;
      totalPrice[p] = totalVol[p] = 0;
      bv[p] = bvp[p] = bvp2[p] = 0;
      curBarVol[p] = 0;
      firstBar[p] = ind;
      for(int k = 0; k < 5; k++) series.setPathBreak(ind, VAL[p][k], true);
    }

    double hlc3(int i) { return (series.getHigh(i) + series.getLow(i) + series.getClose(i)) / 3.0; }

    void finishBar(int i)
    {
      for(int p = 0; p < 3; p++) {
        if (on[p] && curBarVol[p] > 0 && !(skipFirst && i == firstBar[p])) {
          double vol = series.getVolumeAsFloat(i), pr = hlc3(i);
          bv[p] += vol;
          bvp[p] += vol*pr;
          bvp2[p] += vol*pr*pr;
        }
        curBarVol[p] = 0;
      }
    }

    void flush(int i)
    {
      for(int p = 0; p < 3; p++) {
        if (!on[p] || totalVol[p] == 0) continue;
        double vwap = totalPrice[p] / totalVol[p];
        series.setDouble(i, VAL[p][0], vwap);
        if (!band1[p]) continue;

        // Sierra Chart style: sum(vol*(hlc3 - vwap)^2)/sum(vol) over the bars of the period, expanded into running sums
        double sv = bv[p], svp = bvp[p], svp2 = bvp2[p];
        if (curBarVol[p] > 0 && !(skipFirst && i == firstBar[p])) {
          double vol = series.getVolumeAsFloat(i), pr = hlc3(i);
          sv += vol; svp += vol*pr; svp2 += vol*pr*pr;
        }
        double dev = 0;
        if (sv > 0) dev = Math.sqrt(Math.max(0, (svp2 - 2*vwap*svp + vwap*vwap*sv) / sv));
        series.setDouble(i, VAL[p][1], vwap + dev*std1[p]);
        series.setDouble(i, VAL[p][2], vwap - dev*std1[p]);
        if (band2[p]) {
          series.setDouble(i, VAL[p][3], vwap + dev*std2[p]);
          series.setDouble(i, VAL[p][4], vwap - dev*std2[p]);
        }
      }
    }
  }

  /** Draws the labels at the right edge of the chart, stacking them so they never overlap. */
  private class LevelLabels extends Figure
  {
    @Override
    public boolean isVisible(DrawContext ctx) { return true; }

    @Override
    public void layout(DrawContext ctx) { }

    @Override
    public void draw(Graphics2D g, DrawContext ctx)
    {
      var s = ctx.getSettings();
      if (!s.getBoolean(SHOW_LABELS, true)) return;
      var series = ctx.getDataContext().getDataSeries();
      int last = series.size() - 1;
      if (last < 0) return;
      boolean bands = s.getBoolean(SHOW_BAND_LABELS, true);
      String priceMode = s.getString(SHOW_PRICE, "A");
      var instr = series.getInstrument();

      FontInfo fi = s.getFont(LABEL_FONT);
      Font font = fi != null ? fi.getFont() : g.getFont();
      g.setFont(font);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      FontMetrics fm = g.getFontMetrics();
      Rectangle2D b = ctx.getBounds();

      List<double[]> ys = new ArrayList<>(); // {trueY, labelY, idx}
      List<String> texts = new ArrayList<>();
      List<Color> cols = new ArrayList<>();
      for(int p = 0; p < 3; p++) {
        for(int kk = 0; kk < 5; kk++) {
          if (kk > 0 && !bands) continue;
          String pk = kk == 0 ? k(p,"PATH") : kk <= 2 ? k(p,"PATH1") : k(p,"PATH2");
          var path = s.getPath(pk);
          if (path == null || !path.isEnabled()) continue;
          Double v = series.getDouble(last, VAL[p][kk]);
          if (v == null) continue;
          double y = ctx.translateValueD(v);
          if (y < b.getMinY() || y > b.getMaxY()) continue;
          String txt;
          if (kk == 0) txt = PFX[p] + "VWAP";
          else {
            double std = s.getDouble(kk <= 2 ? k(p,"STD1") : k(p,"STD2"));
            txt = PFX[p] + (kk % 2 == 1 ? "+" : "-") + fmt(std);
          }
          if (Util.compare(priceMode, "A") || (kk == 0 && Util.compare(priceMode, "V"))) txt += " " + instr.format(instr.round(v));
          ys.add(new double[] { y, y, texts.size() });
          texts.add(txt);
          cols.add(path.getColor());
        }
      }
      if (ys.isEmpty()) return;

      ys.sort((a, c) -> Double.compare(a[0], c[0]));
      double h = fm.getHeight() - 2;
      for(int i = 1; i < ys.size(); i++) {
        if (ys.get(i)[1] - ys.get(i-1)[1] < h) ys.get(i)[1] = ys.get(i-1)[1] + h;
      }

      double right = b.getMaxX() - s.getInteger(LABEL_OFFSET, 8);
      for(var item : ys) {
        int idx = (int)item[2];
        String txt = texts.get(idx);
        g.setColor(cols.get(idx));
        int w = fm.stringWidth(txt);
        double x = right - w;
        // small leader from the true level to the (possibly shifted) label
        g.drawLine((int)(x - 8), (int)item[0], (int)(x - 2), (int)item[1]);
        g.drawString(txt, (float)x, (float)(item[1] + fm.getAscent()/2.0 - 1));
      }
    }

    private String fmt(double d) { return d == Math.rint(d) ? String.valueOf((int)d) : String.valueOf(d); }
  }
}
