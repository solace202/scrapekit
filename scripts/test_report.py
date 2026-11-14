#!/usr/bin/env python3
"""Summarises Maven Surefire results (target/surefire-reports/*.xml).

Usage:
  python3 scripts/test_report.py               # plain text
  python3 scripts/test_report.py --markdown    # GitHub Actions job summary
  python3 scripts/test_report.py --html out.html
"""
import argparse
import datetime as dt
import html
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPORTS = Path(__file__).resolve().parent.parent / "target" / "surefire-reports"


def load():
    suites = []
    for f in sorted(REPORTS.glob("TEST-*.xml")):
        root = ET.parse(f).getroot()
        cases = []
        for c in root.findall("testcase"):
            status = "passed"
            if c.find("failure") is not None:
                status = "failed"
            elif c.find("error") is not None:
                status = "error"
            elif c.find("skipped") is not None:
                status = "skipped"
            cases.append({"name": c.get("name"), "time": float(c.get("time") or 0), "status": status})
        name = root.get("name", f.stem)
        kind = "integration" if f.stem.endswith("IT") else "unit"
        suites.append({"name": name, "display": name.split(".")[-1] if "." in name and " " not in name else name,
                       "kind": kind,
                       "time": float(root.get("time") or 0), "cases": cases})
    return suites


def totals(suites):
    cases = [c for s in suites for c in s["cases"]]
    return {
        "tests": len(cases),
        "passed": sum(c["status"] == "passed" for c in cases),
        "failed": sum(c["status"] in ("failed", "error") for c in cases),
        "skipped": sum(c["status"] == "skipped" for c in cases),
        "time": sum(s["time"] for s in suites),
    }


def markdown(suites):
    t = totals(suites)
    icon = "✅" if t["failed"] == 0 else "❌"
    out = [f"### {icon} {t['passed']}/{t['tests']} tests passed in {t['time']:.1f}s", "",
           "| Suite | Type | Tests | Result |", "| --- | --- | --- | --- |"]
    for s in suites:
        bad = sum(c["status"] in ("failed", "error") for c in s["cases"])
        out.append(f"| {s['display']} | {s['kind']} | {len(s['cases'])} | {'❌ ' + str(bad) + ' failed' if bad else '✅'} |")
    return "\n".join(out)


def text(suites):
    t = totals(suites)
    lines = [f"{t['passed']}/{t['tests']} passed, {t['failed']} failed, {t['skipped']} skipped ({t['time']:.1f}s)"]
    for s in suites:
        for c in s["cases"]:
            lines.append(f"  [{c['status']:7}] {s['display']} > {c['name']}")
    return "\n".join(lines)


def render_html(suites):
    t = totals(suites)
    ok = t["failed"] == 0
    rows = []
    for s in suites:
        rows.append(f'<tr class="suite"><td colspan="2">{html.escape(s["display"])}'
                    f'<span class="kind {s["kind"]}">{s["kind"]}</span></td>'
                    f'<td class="num">{s["time"]:.2f}s</td></tr>')
        for c in s["cases"]:
            mark = {"passed": "✔", "failed": "✘", "error": "✘", "skipped": "–"}[c["status"]]
            rows.append(f'<tr class="case {c["status"]}"><td class="mark">{mark}</td>'
                        f'<td>{html.escape(c["name"])}</td><td class="num">{c["time"]:.2f}s</td></tr>')
    now = dt.datetime.now().strftime("%Y-%m-%d %H:%M")
    return f"""<!doctype html><html><head><meta charset="utf-8"><title>TenantAI test report</title>
<style>
body{{font:14px/1.45 -apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;margin:0;background:#f6f8fa;color:#1f2328}}
header{{background:#24292f;color:#fff;padding:18px 28px;display:flex;align-items:center;gap:16px}}
header h1{{font-size:18px;margin:0;font-weight:600}} header .sub{{color:#9da7b3;font-size:13px}}
.badge{{margin-left:auto;padding:6px 14px;border-radius:20px;font-weight:600;background:{'#1f883d' if ok else '#cf222e'}}}
.cards{{display:flex;gap:14px;padding:18px 28px 6px}}
.card{{background:#fff;border:1px solid #d0d7de;border-radius:8px;padding:12px 18px;min-width:120px}}
.card b{{display:block;font-size:24px}} .card span{{color:#59636e;font-size:12px;text-transform:uppercase;letter-spacing:.04em}}
table{{border-collapse:collapse;margin:12px 28px 28px;width:calc(100% - 56px);background:#fff;border:1px solid #d0d7de;border-radius:8px;overflow:hidden}}
td{{padding:5px 12px;border-top:1px solid #eaeef2}} tr.suite td{{background:#f6f8fa;font-weight:600;padding-top:9px}}
.mark{{width:22px;text-align:center}} .passed .mark{{color:#1f883d}} .failed .mark,.error .mark{{color:#cf222e}}
.num{{text-align:right;color:#59636e;width:70px;font-variant-numeric:tabular-nums}}
.kind{{font-size:11px;font-weight:500;margin-left:10px;padding:1px 8px;border-radius:10px;background:#ddf4ff;color:#0969da}}
.kind.integration{{background:#fbefff;color:#8250df}}
</style></head><body>
<header><div><h1>TenantAI · mvn verify</h1><div class="sub">JUnit 5 · Testcontainers (postgres:16-alpine, redis:7-alpine) · stubbed LLM · {now}</div></div>
<div class="badge">{'BUILD SUCCESS' if ok else 'BUILD FAILURE'}</div></header>
<div class="cards"><div class="card"><b>{t['tests']}</b><span>tests</span></div>
<div class="card"><b style="color:#1f883d">{t['passed']}</b><span>passed</span></div>
<div class="card"><b style="color:{'#cf222e' if t['failed'] else '#59636e'}">{t['failed']}</b><span>failed</span></div>
<div class="card"><b>{len(suites)}</b><span>suites</span></div>
<div class="card"><b>{t['time']:.1f}s</b><span>test time</span></div></div>
<table>{''.join(rows)}</table></body></html>"""


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--markdown", action="store_true")
    p.add_argument("--html")
    a = p.parse_args()
    suites = load()
    if not suites:
        print("No surefire reports found in", REPORTS)
        return 1
    if a.html:
        Path(a.html).write_text(render_html(suites), encoding="utf-8")
        print("Wrote", a.html)
    elif a.markdown:
        print(markdown(suites))
    else:
        print(text(suites))
    return 1 if totals(suites)["failed"] else 0


if __name__ == "__main__":
    sys.exit(main())
