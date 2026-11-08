package com.example.mlops;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class FprPlot {

    record Point(int m, double measured, double theory) {}

    static final int WIDTH = 900;
    static final int HEIGHT = 560;
    static final int LEFT = 90;
    static final int RIGHT = 170;
    static final int TOP = 60;
    static final int BOTTOM = 80;
    static final double PLOT_W = WIDTH - LEFT - RIGHT;
    static final double PLOT_H = HEIGHT - TOP - BOTTOM;
    static final String[] COLORS = {"#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b"};

    // Maps data values to pixel positions: x is linear, y is logarithmic
    record Axes(int xMin, int xMax, int minLog, int maxLog) {
        double px(double m) {
            return LEFT + (m - xMin) / (xMax - xMin) * PLOT_W;
        }

        double py(double percent) {
            return TOP + (maxLog - Math.log10(percent)) / (maxLog - minLog) * PLOT_H;
        }
    }

    public static void main(String[] args) throws IOException {
        ExperimentConfig cfg = new ExperimentConfig("experiments/experiment-config.properties");
        Path input = Path.of(cfg.getString("fpr.study.output"));
        Path output = Path.of(cfg.getString("fpr.study.plot"));
        int labBits = cfg.getInt("bloom.bits");

        Map<Integer, List<Point>> series = new TreeMap<>();
        int n = 0;
        List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {          // line 0 is the header
            if (lines.get(i).isBlank()) {
                continue;
            }
            String[] c = lines.get(i).split(",");
            n = Integer.parseInt(c[2]);
            series.computeIfAbsent(Integer.parseInt(c[1]), k -> new ArrayList<>())
                    .add(new Point(Integer.parseInt(c[0]), Double.parseDouble(c[5]), Double.parseDouble(c[6])));
        }
        for (List<Point> points : series.values()) {
            points.sort(Comparator.comparingInt(Point::m));
        }

        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        Files.writeString(output, buildSvg(series, n, labBits), StandardCharsets.UTF_8);
        System.out.println("Saved " + output);
    }

    static String buildSvg(Map<Integer, List<Point>> series, int n, int labBits) {
        // Data ranges (a measured value of 0 cannot be drawn on a log scale)
        int xMin = Integer.MAX_VALUE;
        int xMax = Integer.MIN_VALUE;
        double vMin = Double.MAX_VALUE;
        double vMax = 0;
        for (List<Point> points : series.values()) {
            for (Point p : points) {
                xMin = Math.min(xMin, p.m());
                xMax = Math.max(xMax, p.m());
                vMin = Math.min(vMin, p.theory());
                vMax = Math.max(vMax, Math.max(p.measured(), p.theory()));
                if (p.measured() > 0) {
                    vMin = Math.min(vMin, p.measured());
                }
            }
        }
        int minLog = (int) Math.floor(Math.log10(vMin));
        int maxLog = (int) Math.ceil(Math.log10(vMax));
        if (maxLog <= minLog) {
            maxLog = minLog + 1;
        }
        int pad = Math.max(1, (xMax - xMin) / 20);
        Axes axes = new Axes(xMin - pad, xMax + pad, minLog, maxLog);

        StringBuilder s = new StringBuilder();
        s.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" "
                        + "viewBox=\"0 0 %d %d\" font-family=\"sans-serif\" font-size=\"12\">%n",
                WIDTH, HEIGHT, WIDTH, HEIGHT));
        s.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");

        // Title and axis labels
        s.append(String.format(Locale.ROOT,
                "<text x=\"%d\" y=\"30\" font-size=\"16\" text-anchor=\"middle\">"
                        + "False-positive rate vs bit-array size (n = %,d inserted IDs)</text>\n",
                WIDTH / 2, n));
        s.append(text(LEFT + PLOT_W / 2, HEIGHT - 25, "middle", "Bit-array size m (bits)"));
        s.append(String.format(Locale.ROOT,
                "<text x=\"22\" y=\"%.1f\" text-anchor=\"middle\" transform=\"rotate(-90 22 %.1f)\">"
                        + "False-positive rate (%%, log scale)</text>\n",
                TOP + PLOT_H / 2, TOP + PLOT_H / 2));

        // Horizontal grid lines, one per power of ten
        for (int e = axes.minLog(); e <= axes.maxLog(); e++) {
            double y = axes.py(Math.pow(10, e));
            s.append(line(LEFT, y, LEFT + PLOT_W, y, "#dddddd", 1, null));
            s.append(text(LEFT - 8, y + 4, "end", percentLabel(e)));
        }

        // X ticks at each measured size
        TreeSet<Integer> ticks = new TreeSet<>();
        for (List<Point> points : series.values()) {
            for (Point p : points) {
                ticks.add(p.m());
            }
        }
        for (int m : ticks) {
            double x = axes.px(m);
            s.append(line(x, TOP + PLOT_H, x, TOP + PLOT_H + 5, "#333333", 1, null));
            s.append(text(x, TOP + PLOT_H + 20, "middle", Math.round(m / 1000.0) + "k"));
        }

        // Axes
        s.append(line(LEFT, TOP, LEFT, TOP + PLOT_H, "#333333", 1.5, null));
        s.append(line(LEFT, TOP + PLOT_H, LEFT + PLOT_W, TOP + PLOT_H, "#333333", 1.5, null));

        // Marker for the size chosen in the lab
        if (labBits >= axes.xMin() && labBits <= axes.xMax()) {
            double x = axes.px(labBits);
            s.append(line(x, TOP, x, TOP + PLOT_H, "#888888", 1.5, "5,4"));
            s.append(text(x + 5, TOP + 14, "start",
                    "lab choice: m = " + String.format(Locale.ROOT, "%,d", labBits)));
        }

        // One color per k: line = theory, dots = measured
        int index = 0;
        for (Map.Entry<Integer, List<Point>> entry : series.entrySet()) {
            String color = COLORS[index % COLORS.length];

            StringBuilder polyline = new StringBuilder();
            for (Point p : entry.getValue()) {
                polyline.append(String.format(Locale.ROOT, "%.1f,%.1f ", axes.px(p.m()), axes.py(p.theory())));
            }
            s.append(String.format(Locale.ROOT,
                    "<polyline fill=\"none\" stroke=\"%s\" stroke-width=\"2\" points=\"%s\"/>\n",
                    color, polyline.toString().trim()));

            for (Point p : entry.getValue()) {
                if (p.measured() > 0) {
                    s.append(String.format(Locale.ROOT,
                            "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"4\" fill=\"%s\" stroke=\"white\" stroke-width=\"1\"/>\n",
                            axes.px(p.m()), axes.py(p.measured()), color));
                }
            }

            double ly = TOP + 20 + index * 22;
            s.append(line(WIDTH - RIGHT + 15, ly, WIDTH - RIGHT + 45, ly, color, 2, null));
            s.append(String.format(Locale.ROOT,
                    "<circle cx=\"%d\" cy=\"%.1f\" r=\"4\" fill=\"%s\"/>\n", WIDTH - RIGHT + 30, ly, color));
            s.append(text(WIDTH - RIGHT + 52, ly + 4, "start", "k = " + entry.getKey()));
            index++;
        }
        double noteY = TOP + 20 + index * 22 + 14;
        s.append(text(WIDTH - RIGHT + 15, noteY, "start", "line = theory"));
        s.append(text(WIDTH - RIGHT + 15, noteY + 18, "start", "dots = measured"));

        s.append("</svg>\n");
        return s.toString();
    }

    static String line(double x1, double y1, double x2, double y2, String color, double width, String dash) {
        return String.format(Locale.ROOT,
                "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"%s\" stroke-width=\"%.1f\"%s/>\n",
                x1, y1, x2, y2, color, width, dash == null ? "" : " stroke-dasharray=\"" + dash + "\"");
    }

    static String text(double x, double y, String anchor, String content) {
        return String.format(Locale.ROOT,
                "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"%s\">%s</text>\n", x, y, anchor, content);
    }

    static String percentLabel(int exponent) {
        return exponent >= 0
                ? String.valueOf((long) Math.pow(10, exponent))
                : "0." + "0".repeat(-exponent - 1) + "1";
    }
}