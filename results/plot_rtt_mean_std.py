#!/usr/bin/env python3
"""
Bar chart of RTT mean with standard deviation error bars for 5 scenarios.

Order:
1) plain
2) aes-128
3) aes-192
4) aes-256
5) bacnet-sc-tls
"""

from __future__ import annotations

import argparse
import math
import shutil
import subprocess
import tempfile
from dataclasses import dataclass
from pathlib import Path


ORDER = [
    ("Plaintext", "results_rtt_bacnet-plain.txt"),
    ("128 bit key", "results_rtt_aes-128.txt"),
    ("192 bit key", "results_rtt_aes-192.txt"),
    ("256 bit key", "results_rtt_aes-256.txt"),
    ("BACnet/SC", "results_rtt_bacnet-sc-tls.txt"),
]

BAR_COLORS = ["#1f77b4", "#ff7f0e", "#2ca02c", "#d6ca27", "#793C00"]


@dataclass
class Summary:
    count: int
    mean_us: float
    std_us: float


class RunningStats:
    """Streaming mean/std with Welford algorithm."""

    def __init__(self) -> None:
        self.n = 0
        self.mean = 0.0
        self.m2 = 0.0

    def add(self, x: float) -> None:
        self.n += 1
        delta = x - self.mean
        self.mean += delta / self.n
        delta2 = x - self.mean
        self.m2 += delta * delta2

    @property
    def std(self) -> float:
        if self.n < 2:
            return 0.0
        return math.sqrt(self.m2 / (self.n - 1))


def parse_args() -> argparse.Namespace:
    default_results = Path(__file__).resolve().parent
    default_output = default_results / "rtt_mean_std.pdf"

    parser = argparse.ArgumentParser(
        description="Generate RTT mean/std bar chart in PDF."
    )
    parser.add_argument(
        "--results-dir",
        default=str(default_results),
        help="Directory containing RTT files (default: script directory).",
    )
    parser.add_argument(
        "--output",
        default=str(default_output),
        help="Output file path (default: rtt_mean_std.pdf).",
    )
    parser.add_argument(
        "--title",
        default="",
        help="Chart title (default: no title).",
    )
    parser.add_argument(
        "--dpi",
        type=int,
        default=220,
        help="DPI when matplotlib backend is used (default: 220).",
    )
    return parser.parse_args()


def load_stats(path: Path) -> Summary:
    rs = RunningStats()
    with path.open("r", encoding="utf-8", errors="ignore") as f:
        for line_no, raw in enumerate(f, start=1):
            text = raw.strip()
            if not text:
                continue
            try:
                value = float(text)
            except ValueError as exc:
                raise ValueError(
                    f"Non-numeric value at {path}:{line_no}: {text!r}"
                ) from exc
            rs.add(value)

    if rs.n == 0:
        raise ValueError(f"No RTT samples found in {path}")
    return Summary(count=rs.n, mean_us=rs.mean, std_us=rs.std)


def render_matplotlib(
    labels: list[str],
    means_us: list[float],
    stds_us: list[float],
    output_path: Path,
    title: str,
    dpi: int,
) -> None:
    import numpy as np
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    x = np.arange(len(labels))
    means = np.array(means_us, dtype=float)
    stds = np.array(stds_us, dtype=float)

    fig, ax = plt.subplots(figsize=(9, 5.5))

    error_kw = dict(elinewidth=1.0, ecolor="red", capsize=2.0)
    ax.bar(
        x,
        means,
        yerr=stds,
        color=BAR_COLORS,
        error_kw=error_kw,
        width=0.72,
        zorder=2,
    )
    ax.errorbar(
        x,
        means,
        yerr=stds,
        fmt="o",
        color="red",
        mfc="white",
        zorder=3,
        ecolor="red",
        elinewidth=1.8,
        capsize=5,
        markersize=5,
    )

    if title.strip():
        ax.set_title(title, fontsize=21, pad=10)
    ax.set_ylabel("Avg Time (us)", fontsize=22)
    ax.set_xlabel("")
    ax.set_xticks(x)
    ax.set_xticklabels(labels, fontsize=19)
    ax.tick_params(axis="y", labelsize=18)
    ax.grid(axis="y", alpha=0.25, linestyle="--", zorder=0)
    ax.set_axisbelow(True)

    y_top = float(np.max(means + stds))
    ax.set_ylim(0.0, y_top * 1.22 if y_top > 0 else 1.0)
    ax.set_xlim(-0.6, len(labels) - 0.4)

    for xm, mean, std in zip(x, means, stds):
        y_text = mean + std + (0.018 * y_top)
        ax.text(xm, y_text, f"{mean:.1f}", ha="center", va="bottom", fontsize=21)

    fig.tight_layout(pad=1.0)
    fig.savefig(output_path, format="pdf", bbox_inches="tight", dpi=dpi)
    plt.close(fig)


def render_gnuplot(
    labels: list[str],
    means_us: list[float],
    stds_us: list[float],
    output_path: Path,
    title: str,
) -> None:
    if shutil.which("gnuplot") is None:
        raise RuntimeError("Neither matplotlib nor gnuplot is available.")

    with tempfile.TemporaryDirectory(prefix="rtt_pdf_") as tmp_dir_raw:
        tmp_dir = Path(tmp_dir_raw)
        data_path = tmp_dir / "rtt.tsv"
        gp_path = tmp_dir / "plot.gp"

        with data_path.open("w", encoding="utf-8") as f:
            for idx, (mean, std) in enumerate(zip(means_us, stds_us)):
                f.write(f"{idx}\t{mean}\t{std}\n")

        max_y = max((m + s) for m, s in zip(means_us, stds_us))
        label_offset = max_y * 0.04 if max_y > 0 else 0.05
        xtics_items = ", ".join(
            f"'{label.replace(chr(10), r'\n')}' {idx}" for idx, label in enumerate(labels)
        )
        x_right = len(labels) - 0.3

        bar_cmds = []
        for idx, color in enumerate(BAR_COLORS[: len(labels)]):
            bar_cmds.append(
                f"'{data_path}' every ::{idx}::{idx} using 1:2 with boxes lc rgb '{color}' notitle"
            )
        bars_plot = ", \\\n     ".join(bar_cmds)
        title_line = (
            f"set title '{title}' font ',18'"
            if title.strip()
            else "unset title"
        )

        # Centered layout, PDF output, and visible error bars.
        gp_script = f"""set terminal pdfcairo enhanced color size 9in,5.5in font 'Helvetica,16'
set output '{output_path}'
set datafile separator '\\t'
{title_line}
set ylabel 'Avg Time (us)' font ',23'
set xlabel ''
set key off
set grid ytics lc rgb '#dddddd'
set border linewidth 1.0
set style fill solid 0.9 border rgb '#333333'
set boxwidth 0.72
set xtics rotate by 0 font ',19'
set ytics font ',18'
set xtics ({xtics_items})
set lmargin 9
set rmargin 2
set bmargin 4.5
set tmargin 2.5
set xrange [-0.7:{x_right}]
plot {bars_plot}, \
     '{data_path}' using 1:2:3 with yerrorbars lc rgb 'red' pt 7 ps 0.4 lw 1.5 notitle, \
     '{data_path}' using 1:($2+$3+{label_offset}):(sprintf('%.1f',$2)) with labels center font ',20' tc rgb '#222222' notitle
"""
        gp_path.write_text(gp_script, encoding="utf-8")
        subprocess.run(
            ["gnuplot", str(gp_path)],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )


def main() -> int:
    args = parse_args()
    results_dir = Path(args.results_dir).expanduser().resolve()
    output_path = Path(args.output).expanduser().resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)

    labels: list[str] = []
    means_us: list[float] = []
    stds_us: list[float] = []

    print("case,count,mean_us,std_us,file")
    for label, file_name in ORDER:
        fpath = results_dir / file_name
        if not fpath.exists():
            raise FileNotFoundError(f"Missing RTT file: {fpath}")
        stats = load_stats(fpath)
        labels.append(label)
        means_us.append(stats.mean_us)
        stds_us.append(stats.std_us)
        print(f"{label},{stats.count},{stats.mean_us:.6f},{stats.std_us:.6f},{fpath}")

    try:
        render_matplotlib(labels, means_us, stds_us, output_path, args.title, args.dpi)
    except ModuleNotFoundError:
        render_gnuplot(labels, means_us, stds_us, output_path, args.title)

    print(f"\nGrafico salvato come {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
