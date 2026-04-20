"""Report writers for Stage 2d/Phase 3 runs.

Layout (per-run directory):
  reports/run-<ISO-ts>/
    summary.md   - human-readable: CLI args, ramp plan, per-level stats,
                   final snapshot, reason-for-stop
    summary.html - same content + four time-series charts (Chart.js via CDN)
    ticks.csv    - machine-readable: one row per reporter tick

All three files are written at the END of the run so we don't have to
deal with partial-file-on-crash concerns — the collector already holds
the tick history in memory.

Future improvement: per-op time series. Currently TickRecord only holds
aggregate p50/p95/ops-per-sec, so per-op trends across the ramp aren't
visible. Adding per-op arrays to TickRecord would enable charts like
"uploads slowed at level 5 but other ops stayed fine."
"""

from __future__ import annotations

import csv
import datetime as _dt
import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

from .metrics import Snapshot, iter_snapshot_rows
from .ramp import Level


@dataclass
class TickRecord:
    """One reporter tick: what was printed to stdout + a bit more. The
    `user_count` reflects the level that was active AT the time of the
    tick, so per-level aggregation is possible downstream."""
    t_elapsed_s: float
    user_count: int
    window_ops: int
    window_ok: int
    window_fail: int
    ops_per_s: float
    p50_ms: float
    p95_ms: float
    error_rate: float
    # Per-method split (see lib/metrics.py MethodStats). Latency aggregates
    # are only meaningful separated because POST upload p95 is pinned to
    # SO_TIMEOUT by the server, unrelated to real processing time.
    get_ops: int = 0
    get_p50_ms: float = 0.0
    get_p95_ms: float = 0.0
    get_error_rate: float = 0.0
    post_ops: int = 0
    post_p50_ms: float = 0.0
    post_p95_ms: float = 0.0
    post_error_rate: float = 0.0


@dataclass
class LevelResult:
    """Summary for one ramp level. Captured at the end of the level
    (before stepping up) so the snapshot reflects steady state."""
    user_count: int
    planned_duration_s: float
    actual_duration_s: float
    total_ops: int
    total_fail: int
    ops_per_s: float
    p50_ms: float
    p95_ms: float
    error_rate: float
    # Per-method split
    get_ops: int = 0
    get_p50_ms: float = 0.0
    get_p95_ms: float = 0.0
    post_ops: int = 0
    post_p50_ms: float = 0.0
    post_p95_ms: float = 0.0


@dataclass
class RunRecord:
    started_iso: str
    ended_iso: str
    base_url: str
    cli_args: str
    ramp_plan: list[Level]
    ticks: list[TickRecord] = field(default_factory=list)
    levels: list[LevelResult] = field(default_factory=list)
    hold_seconds_requested: float = 0.0
    stop_reason: Optional[str] = None
    final_snapshot: Optional[Snapshot] = None


# ---------- helpers ----------

def make_run_dir(base: Path, now: Optional[_dt.datetime] = None) -> Path:
    now = now or _dt.datetime.now(_dt.timezone.utc)
    ts = now.strftime("%Y%m%dT%H%M%SZ")
    path = base / f"run-{ts}"
    path.mkdir(parents=True, exist_ok=True)
    return path


def write_ticks_csv(path: Path, ticks: list[TickRecord]) -> None:
    with path.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow([
            "t_elapsed_s", "user_count", "window_ops", "window_ok", "window_fail",
            "ops_per_s", "p50_ms", "p95_ms", "error_rate",
            # Per-method split
            "get_ops", "get_p50_ms", "get_p95_ms", "get_error_rate",
            "post_ops", "post_p50_ms", "post_p95_ms", "post_error_rate",
        ])
        for t in ticks:
            w.writerow([
                f"{t.t_elapsed_s:.1f}", t.user_count, t.window_ops, t.window_ok, t.window_fail,
                f"{t.ops_per_s:.2f}", f"{t.p50_ms:.1f}", f"{t.p95_ms:.1f}",
                f"{t.error_rate:.4f}",
                t.get_ops, f"{t.get_p50_ms:.1f}", f"{t.get_p95_ms:.1f}",
                f"{t.get_error_rate:.4f}",
                t.post_ops, f"{t.post_p50_ms:.1f}", f"{t.post_p95_ms:.1f}",
                f"{t.post_error_rate:.4f}",
            ])


def write_summary_md(path: Path, rec: RunRecord) -> None:
    lines: list[str] = []
    lines.append(f"# Load test run — {rec.started_iso}")
    lines.append("")
    lines.append(f"**Server**: `{rec.base_url}`")
    lines.append(f"**CLI**: `{rec.cli_args}`")
    lines.append(f"**Started**: {rec.started_iso}")
    lines.append(f"**Ended**:   {rec.ended_iso}")
    lines.append("")
    lines.append("## Ramp plan")
    lines.append("")
    parts = [f"`{l.user_count}u@{int(l.duration_seconds)}s`" for l in rec.ramp_plan]
    lines.append(" → ".join(parts))
    if rec.hold_seconds_requested:
        lines.append("")
        lines.append(f"Requested peak hold: **{int(rec.hold_seconds_requested)}s**")
    lines.append("")

    if rec.stop_reason:
        lines.append("## Stop condition triggered")
        lines.append("")
        lines.append(f"`{rec.stop_reason}`")
        lines.append("")
    else:
        lines.append("## Outcome")
        lines.append("")
        lines.append("Completed without triggering stop condition.")
        lines.append("")

    # Per-level steady-state table
    if rec.levels:
        lines.append("## Per-level results")
        lines.append("")
        lines.append("Latency split: GET is real server performance; POST is pinned to SO_TIMEOUT by the server's upload read loop (see CLAUDE.md).")
        lines.append("")
        lines.append("| users | dur (s) | ops | fail | ops/s | GET p50 | GET p95 | POST p50 | POST p95 | err % |")
        lines.append("|------:|--------:|----:|-----:|------:|--------:|--------:|---------:|---------:|------:|")
        for lvl in rec.levels:
            lines.append(
                f"| {lvl.user_count} | "
                f"{int(lvl.actual_duration_s)} | "
                f"{lvl.total_ops} | {lvl.total_fail} | "
                f"{lvl.ops_per_s:.1f} | "
                f"{int(lvl.get_p50_ms)} | {int(lvl.get_p95_ms)} | "
                f"{int(lvl.post_p50_ms)} | {int(lvl.post_p95_ms)} | "
                f"{lvl.error_rate*100:.2f} |"
            )
        lines.append("")

    # Final snapshot
    if rec.final_snapshot:
        snap = rec.final_snapshot
        lines.append("## Final window snapshot")
        lines.append("")
        lines.append(f"Window length: {int(snap.window_seconds)}s")
        lines.append(f"Total ops: {snap.total_ops}  (ok={snap.total_ok}, fail={snap.total_fail})")
        lines.append(f"Error rate: {snap.error_rate*100:.2f}%")
        lines.append("")
        # Per-method summary
        if snap.by_method:
            lines.append("### By HTTP method")
            lines.append("")
            lines.append("| method | ops | fail | p50 ms | p95 ms | err % |")
            lines.append("|:-|---:|---:|---:|---:|---:|")
            for m in sorted(snap.by_method):
                ms = snap.by_method[m]
                lines.append(
                    f"| **{m}** | {ms.total_ops} | {ms.total_fail} | "
                    f"{int(ms.p50_ms)} | {int(ms.p95_ms)} | {ms.error_rate*100:.2f} |"
                )
            lines.append("")
        if snap.per_op:
            lines.append("### Per-op")
            lines.append("")
            lines.append("| op | method | total | fail | p50 ms | p95 ms | err % |")
            lines.append("|---|:-:|---:|---:|---:|---:|---:|")
            for op_name in sorted(snap.per_op):
                s = snap.per_op[op_name]
                lines.append(
                    f"| `{op_name}` | {s.http_method} | {s.total} | {s.fail_count} | "
                    f"{int(s.percentile(0.50))} | {int(s.percentile(0.95))} | "
                    f"{s.error_rate*100:.2f} |"
                )
            lines.append("")

    # Tick footnote
    lines.append(f"See `ticks.csv` for {len(rec.ticks)} raw reporter samples.")
    lines.append("")
    path.write_text("\n".join(lines))


# ---------- HTML ----------

# Chart.js from CDN. If we ever need offline viewing, inline the lib.
# Using a pinned version (not @latest) so the file renders the same way
# a year from now as today.
_CHART_JS_CDN = "https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"


def _build_chart_data(rec: RunRecord) -> dict:
    """Shape the RunRecord into the arrays the HTML template feeds
    to Chart.js. Kept separate so it's easy to unit-test later."""
    ticks_json = [
        {
            "t": round(t.t_elapsed_s, 1),
            "users": t.user_count,
            "ops_per_s": round(t.ops_per_s, 3),
            "p50_ms": round(t.p50_ms, 1),
            "p95_ms": round(t.p95_ms, 1),
            "error_rate": round(t.error_rate * 100, 3),  # percent, for chart axis
            # Per-method split (see OpResult.http_method)
            "get_ops": t.get_ops,
            "get_p50_ms": round(t.get_p50_ms, 1),
            "get_p95_ms": round(t.get_p95_ms, 1),
            "post_ops": t.post_ops,
            "post_p50_ms": round(t.post_p50_ms, 1),
            "post_p95_ms": round(t.post_p95_ms, 1),
        }
        for t in rec.ticks
    ]

    # Cumulative level boundaries (in elapsed seconds) for vertical markers
    boundaries: list[dict] = []
    t_accum = 0.0
    for lvl in rec.ramp_plan:
        t_accum += lvl.duration_seconds
        boundaries.append({"t": round(t_accum, 1), "label": f"→{lvl.user_count}u"})

    # Per-op bars from the final snapshot
    per_op: list[dict] = []
    final_by_method: list[dict] = []
    if rec.final_snapshot:
        for op_name in sorted(rec.final_snapshot.per_op):
            s = rec.final_snapshot.per_op[op_name]
            per_op.append({
                "op": op_name,
                "method": s.http_method,
                "total": s.total,
                "fail": s.fail_count,
                "p50_ms": round(s.percentile(0.50), 1),
                "p95_ms": round(s.percentile(0.95), 1),
                "error_rate": round(s.error_rate * 100, 3),
            })
        for m in sorted(rec.final_snapshot.by_method):
            ms = rec.final_snapshot.by_method[m]
            final_by_method.append({
                "method": m,
                "total_ops": ms.total_ops,
                "total_fail": ms.total_fail,
                "p50_ms": round(ms.p50_ms, 1),
                "p95_ms": round(ms.p95_ms, 1),
                "error_rate": round(ms.error_rate * 100, 3),
            })

    # Per-level table for the HTML
    levels_json = [
        {
            "users": l.user_count,
            "planned_s": round(l.planned_duration_s, 1),
            "actual_s": round(l.actual_duration_s, 1),
            "total_ops": l.total_ops,
            "total_fail": l.total_fail,
            "ops_per_s": round(l.ops_per_s, 2),
            "p50_ms": round(l.p50_ms, 1),
            "p95_ms": round(l.p95_ms, 1),
            "error_rate": round(l.error_rate * 100, 3),
            "get_ops": l.get_ops,
            "get_p50_ms": round(l.get_p50_ms, 1),
            "get_p95_ms": round(l.get_p95_ms, 1),
            "post_ops": l.post_ops,
            "post_p50_ms": round(l.post_p50_ms, 1),
            "post_p95_ms": round(l.post_p95_ms, 1),
        }
        for l in rec.levels
    ]

    return {
        "started": rec.started_iso,
        "ended": rec.ended_iso,
        "base_url": rec.base_url,
        "cli_args": rec.cli_args,
        "ramp_plan": [f"{l.user_count}u@{int(l.duration_seconds)}s" for l in rec.ramp_plan],
        "hold_seconds_requested": int(rec.hold_seconds_requested),
        "stop_reason": rec.stop_reason,
        "ticks": ticks_json,
        "boundaries": boundaries,
        "per_op": per_op,
        "final_by_method": final_by_method,
        "levels": levels_json,
    }


def _html_escape(s: str) -> str:
    return (
        s.replace("&", "&amp;")
         .replace("<", "&lt;")
         .replace(">", "&gt;")
         .replace("\"", "&quot;")
    )


def write_summary_html(path: Path, rec: RunRecord) -> None:
    """Write a self-contained HTML report with 4 time-series charts and
    a per-op bar chart. Data is embedded as JSON; Chart.js is pulled from
    CDN at view time."""
    data = _build_chart_data(rec)
    # json.dumps with ensure_ascii=True so non-ASCII chars can't break parsing
    data_json = json.dumps(data, ensure_ascii=True)

    stop_banner = ""
    if rec.stop_reason:
        stop_banner = (
            f'<div class="banner stop"><strong>Stop condition triggered:</strong> '
            f'{_html_escape(rec.stop_reason)}</div>'
        )
    else:
        stop_banner = '<div class="banner ok">Completed without triggering stop condition.</div>'

    hold_line = ""
    if rec.hold_seconds_requested:
        hold_line = f'<p><strong>Requested peak hold:</strong> {int(rec.hold_seconds_requested)}s</p>'

    html = _HTML_TEMPLATE.format(
        title=_html_escape(f"Load test run — {rec.started_iso}"),
        started=_html_escape(rec.started_iso),
        ended=_html_escape(rec.ended_iso),
        base_url=_html_escape(rec.base_url),
        cli_args=_html_escape(rec.cli_args),
        ramp_plan=_html_escape(" → ".join(data["ramp_plan"])),
        hold_line=hold_line,
        stop_banner=stop_banner,
        data_json=data_json,
        chart_js_cdn=_CHART_JS_CDN,
        n_ticks=len(rec.ticks),
    )
    path.write_text(html)


# Template kept at module scope so python doesn't re-build it each call.
# Using .format() placeholders; CSS/JS braces are escaped as {{/}}.
_HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="{chart_js_cdn}"></script>
<style>
  body {{
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    margin: 0 auto; padding: 2rem; max-width: 1400px; color: #222;
    background: #fafafa;
  }}
  h1 {{ font-size: 1.4rem; margin-bottom: 0.5rem; }}
  h2 {{ font-size: 1.1rem; margin-top: 2rem; margin-bottom: 0.5rem; }}
  .meta {{ font-size: 0.85rem; color: #555; }}
  .meta p {{ margin: 0.15rem 0; }}
  .meta code {{ background: #eee; padding: 1px 4px; border-radius: 2px; }}
  .banner {{ padding: 0.6rem 1rem; border-radius: 4px; margin: 1rem 0; }}
  .banner.ok {{ background: #e8f5e9; border-left: 4px solid #4caf50; }}
  .banner.stop {{ background: #ffebee; border-left: 4px solid #f44336; }}
  .charts {{
    display: grid; grid-template-columns: 1fr 1fr; gap: 1.5rem;
    margin-top: 1rem;
  }}
  .chart-card {{
    background: white; padding: 1rem; border-radius: 6px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.08);
  }}
  .chart-card h3 {{ margin: 0 0 0.5rem 0; font-size: 0.95rem; color: #444; }}
  .chart-card canvas {{ max-height: 260px; }}
  .per-op {{ grid-column: span 2; }}
  table {{ border-collapse: collapse; font-size: 0.85rem; width: 100%; }}
  th, td {{ padding: 0.3rem 0.6rem; text-align: right; border-bottom: 1px solid #e5e5e5; }}
  th:first-child, td:first-child {{ text-align: left; }}
  th {{ background: #f0f0f0; font-weight: 600; }}
  .footnote {{ margin-top: 2rem; font-size: 0.8rem; color: #888; }}
  .note {{ font-size: 0.85rem; color: #666; font-style: italic; margin: 0.25rem 0 0.75rem 0; }}
</style>
</head>
<body>
<h1>{title}</h1>
<div class="meta">
  <p><strong>Server:</strong> <code>{base_url}</code></p>
  <p><strong>CLI:</strong> <code>{cli_args}</code></p>
  <p><strong>Started:</strong> {started}</p>
  <p><strong>Ended:</strong> {ended}</p>
  <p><strong>Ramp plan:</strong> {ramp_plan}</p>
  {hold_line}
</div>

{stop_banner}

<h2>Time series</h2>
<p class="note">GET and POST latencies split. Server's POST read loop pins upload latency at SO_TIMEOUT; GET reflects real server processing.</p>
<div class="charts">
  <div class="chart-card"><h3>Active users (ramp curve)</h3><canvas id="c_users"></canvas></div>
  <div class="chart-card"><h3>Throughput (ops/s, 30s window)</h3><canvas id="c_ops"></canvas></div>
  <div class="chart-card"><h3>GET latency (ms) — real perf</h3><canvas id="c_lat_get"></canvas></div>
  <div class="chart-card"><h3>POST latency (ms) — pinned to SO_TIMEOUT</h3><canvas id="c_lat_post"></canvas></div>
  <div class="chart-card"><h3>Error rate (%)</h3><canvas id="c_err"></canvas></div>
</div>

<h2>Final window — by HTTP method</h2>
<table>
  <thead><tr>
    <th>method</th><th>total ops</th><th>fail</th><th>p50 ms</th><th>p95 ms</th><th>err %</th>
  </tr></thead>
  <tbody id="by_method_tbody"></tbody>
</table>

<h2>Per-level results</h2>
<table>
  <thead><tr>
    <th>users</th><th>dur (s)</th><th>ops</th><th>fail</th>
    <th>ops/s</th><th>GET p50</th><th>GET p95</th><th>POST p50</th><th>POST p95</th><th>err %</th>
  </tr></thead>
  <tbody id="levels_tbody"></tbody>
</table>

<h2>Per-op breakdown (final window)</h2>
<div class="charts">
  <div class="chart-card per-op"><canvas id="c_per_op"></canvas></div>
</div>

<p class="footnote">Generated from {n_ticks} reporter samples. See ticks.csv for raw data.</p>

<script>
const DATA = {data_json};

// Pull level boundaries as a set of vertical annotations (faked via
// second chart dataset) — simpler than using chartjs-plugin-annotation.
function boundaryPoints(boundaries, yMax) {{
  // Render as a sequence of [x,0],[x,yMax] points per boundary, with NaN
  // separators so Chart.js breaks the line segment between them.
  const out = [];
  for (const b of boundaries) {{
    out.push({{x: b.t, y: 0}});
    out.push({{x: b.t, y: yMax}});
    out.push({{x: b.t, y: NaN}});
  }}
  return out;
}}

function makeTimeSeries(ctx, label, color, yData, yAxisLabel, boundaries, yMaxHint) {{
  const xs = DATA.ticks.map(t => t.t);
  const points = xs.map((x, i) => ({{x, y: yData[i]}}));
  const yMax = Math.max(yMaxHint || 0, ...yData.filter(v => !isNaN(v))) * 1.1 || 1;
  const bPoints = boundaries ? boundaryPoints(DATA.boundaries, yMax) : [];
  return new Chart(ctx, {{
    type: 'line',
    data: {{
      datasets: [
        {{
          label: label,
          data: points,
          borderColor: color,
          backgroundColor: color + '33',
          pointRadius: 1.5,
          tension: 0.2,
          fill: true,
        }},
        {{
          label: 'level boundary',
          data: bPoints,
          borderColor: 'rgba(150,150,150,0.5)',
          borderDash: [4, 4],
          pointRadius: 0,
          borderWidth: 1,
          showLine: true,
          fill: false,
        }}
      ]
    }},
    options: {{
      responsive: true,
      parsing: false,
      animation: false,
      plugins: {{ legend: {{ display: false }} }},
      scales: {{
        x: {{ type: 'linear', title: {{ display: true, text: 'elapsed (s)' }} }},
        y: {{ title: {{ display: true, text: yAxisLabel }}, beginAtZero: true }}
      }}
    }}
  }});
}}

// Active users step chart
makeTimeSeries(
  document.getElementById('c_users'),
  'users', '#3f51b5',
  DATA.ticks.map(t => t.users),
  'active users',
  true,
);

// ops/s
makeTimeSeries(
  document.getElementById('c_ops'),
  'ops/s', '#009688',
  DATA.ticks.map(t => t.ops_per_s),
  'ops / second (30s window)',
  true,
);

// Two latency charts: one for GET (real perf), one for POST (timeout-pinned).
// Shared helper.
function makeLatChart(canvasId, p50Accessor, p95Accessor) {{
  const ctx = document.getElementById(canvasId);
  const xs = DATA.ticks.map(t => t.t);
  const yMax = Math.max(1, ...DATA.ticks.map(p95Accessor)) * 1.1;
  new Chart(ctx, {{
    type: 'line',
    data: {{
      datasets: [
        {{
          label: 'p50',
          data: xs.map((x,i) => ({{x, y: p50Accessor(DATA.ticks[i])}})),
          borderColor: '#00796b', tension: 0.2, pointRadius: 1.5, fill: false,
        }},
        {{
          label: 'p95',
          data: xs.map((x,i) => ({{x, y: p95Accessor(DATA.ticks[i])}})),
          borderColor: '#c62828', tension: 0.2, pointRadius: 1.5, fill: false,
        }},
        {{
          label: 'level boundary',
          data: boundaryPoints(DATA.boundaries, yMax),
          borderColor: 'rgba(150,150,150,0.5)',
          borderDash: [4, 4], pointRadius: 0, borderWidth: 1, fill: false,
        }}
      ]
    }},
    options: {{
      responsive: true, parsing: false, animation: false,
      plugins: {{ legend: {{ display: true, position: 'top' }} }},
      scales: {{
        x: {{ type: 'linear', title: {{ display: true, text: 'elapsed (s)' }} }},
        y: {{ title: {{ display: true, text: 'ms' }}, beginAtZero: true }}
      }}
    }}
  }});
}}

makeLatChart('c_lat_get',
  t => t.get_p50_ms,
  t => t.get_p95_ms);
makeLatChart('c_lat_post',
  t => t.post_p50_ms,
  t => t.post_p95_ms);

// Error rate % with 5% threshold line
(function() {{
  const ctx = document.getElementById('c_err');
  const xs = DATA.ticks.map(t => t.t);
  const errs = DATA.ticks.map(t => t.error_rate);
  const yMax = Math.max(6, ...errs) * 1.1;
  new Chart(ctx, {{
    type: 'line',
    data: {{
      datasets: [
        {{
          label: 'error %',
          data: xs.map((x,i) => ({{x, y: errs[i]}})),
          borderColor: '#e65100', backgroundColor: '#ff9800' + '33',
          tension: 0.2, pointRadius: 1.5, fill: true,
        }},
        {{
          label: 'stop threshold (5%)',
          data: [
            {{x: xs[0] || 0, y: 5}},
            {{x: xs[xs.length - 1] || 1, y: 5}}
          ],
          borderColor: 'rgba(244,67,54,0.6)',
          borderDash: [6, 4], pointRadius: 0, borderWidth: 1.5, fill: false,
        }},
        {{
          label: 'level boundary',
          data: boundaryPoints(DATA.boundaries, yMax),
          borderColor: 'rgba(150,150,150,0.5)',
          borderDash: [4, 4], pointRadius: 0, borderWidth: 1, fill: false,
        }}
      ]
    }},
    options: {{
      responsive: true, parsing: false, animation: false,
      plugins: {{ legend: {{ display: true, position: 'top' }} }},
      scales: {{
        x: {{ type: 'linear', title: {{ display: true, text: 'elapsed (s)' }} }},
        y: {{ title: {{ display: true, text: '% errors' }}, beginAtZero: true }}
      }}
    }}
  }});
}})();

// Per-op bar chart
(function() {{
  const ctx = document.getElementById('c_per_op');
  const labels = DATA.per_op.map(p => p.op);
  new Chart(ctx, {{
    type: 'bar',
    data: {{
      labels: labels,
      datasets: [
        {{ label: 'total', data: DATA.per_op.map(p => p.total), backgroundColor: '#3f51b5' }},
        {{ label: 'failed', data: DATA.per_op.map(p => p.fail), backgroundColor: '#c62828' }},
        {{ label: 'p95 ms', data: DATA.per_op.map(p => p.p95_ms), backgroundColor: '#ff9800', yAxisID: 'y_ms' }}
      ]
    }},
    options: {{
      responsive: true, animation: false,
      plugins: {{ legend: {{ display: true, position: 'top' }} }},
      scales: {{
        y: {{ beginAtZero: true, title: {{ display: true, text: 'count' }} }},
        y_ms: {{
          beginAtZero: true, position: 'right',
          grid: {{ drawOnChartArea: false }},
          title: {{ display: true, text: 'p95 ms' }}
        }}
      }}
    }}
  }});
}})();

// Render per-level table with GET/POST split
(function() {{
  const tbody = document.getElementById('levels_tbody');
  for (const lvl of DATA.levels) {{
    const tr = document.createElement('tr');
    tr.innerHTML =
      '<td>' + lvl.users + '</td>' +
      '<td>' + Math.round(lvl.actual_s) + '</td>' +
      '<td>' + lvl.total_ops + '</td>' +
      '<td>' + lvl.total_fail + '</td>' +
      '<td>' + lvl.ops_per_s.toFixed(1) + '</td>' +
      '<td>' + Math.round(lvl.get_p50_ms) + '</td>' +
      '<td>' + Math.round(lvl.get_p95_ms) + '</td>' +
      '<td>' + Math.round(lvl.post_p50_ms) + '</td>' +
      '<td>' + Math.round(lvl.post_p95_ms) + '</td>' +
      '<td>' + lvl.error_rate.toFixed(2) + '</td>';
    tbody.appendChild(tr);
  }}
}})();

// Render by-method table (final window)
(function() {{
  const tbody = document.getElementById('by_method_tbody');
  for (const m of (DATA.final_by_method || [])) {{
    const tr = document.createElement('tr');
    tr.innerHTML =
      '<td><strong>' + m.method + '</strong></td>' +
      '<td>' + m.total_ops + '</td>' +
      '<td>' + m.total_fail + '</td>' +
      '<td>' + Math.round(m.p50_ms) + '</td>' +
      '<td>' + Math.round(m.p95_ms) + '</td>' +
      '<td>' + m.error_rate.toFixed(2) + '</td>';
    tbody.appendChild(tr);
  }}
}})();
</script>
</body>
</html>
"""
