#!/usr/bin/env python3
"""Captures the README screenshots from a running local instance (run the demo jobs first).

Requires: pip install playwright openpyxl && playwright install chromium   (or pass --chrome for an installed Chrome)
Usage:    python scripts/capture_screenshots.py [--base-url http://localhost:8080] [--chrome] [--only dashboard,excel]
Shots:    dashboard, job, excel (renders the real .xlsx export as a spreadsheet view), swagger
"""
import argparse
import html
import io
import re
import urllib.request
from pathlib import Path

import openpyxl
from openpyxl.utils import get_column_letter
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "screenshots"


def excel_page(xlsx_bytes, file_name, rows=24):
    """Renders the first rows of the exported workbook like a spreadsheet window (values and styles from the file)."""
    wb = openpyxl.load_workbook(io.BytesIO(xlsx_bytes))
    ws = wb.worksheets[0]
    ncols = ws.max_column
    widths = [max(60, int((ws.column_dimensions[get_column_letter(c)].width or 10) * 7.0)) for c in range(1, ncols + 1)]
    # keep long URL columns narrow so every column fits in the window
    widths = [min(w, 190) if ws.cell(2, i + 1).hyperlink is not None else w for i, w in enumerate(widths)]
    head = "".join(f'<th style="width:{w}px">{get_column_letter(i + 1)}</th>' for i, w in enumerate(widths))
    body = []
    for r in range(1, min(ws.max_row, rows) + 1):
        tds = []
        for c in range(1, ncols + 1):
            cell = ws.cell(r, c)
            v = cell.value
            cls = []
            if r == 1:
                cls.append("hdr")
                text = html.escape(str(v)) + '<span class="flt">▾</span>'
            else:
                if isinstance(v, float) and "0.00" in (cell.number_format or ""):
                    text = f"{v:,.2f}"
                    cls.append("num")
                elif isinstance(v, (int, float)):
                    text = str(int(v)) if float(v).is_integer() else str(v)
                    cls.append("ctr")
                else:
                    text = html.escape("" if v is None else str(v))
                    if cell.hyperlink is not None:
                        cls.append("lnk")
                    elif cell.alignment is not None and cell.alignment.horizontal == "center":
                        cls.append("ctr")
            sel = ' sel' if (r == 2 and c == 3) else ''
            tds.append(f'<td class="{" ".join(cls)}{sel}">{text}</td>')
        body.append(f'<tr><th class="rn">{r}</th>{"".join(tds)}</tr>')
    tabs = "".join(f'<span class="tab{" on" if i == 0 else ""}">{html.escape(s.title)}</span>'
                   for i, s in enumerate(wb.worksheets))
    price = ws.cell(2, 3).value
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
body{{margin:0;background:#e9edf2;font-family:"Segoe UI",Roboto,Arial,sans-serif;padding:22px}}
.win{{background:#fff;border-radius:8px;box-shadow:0 8px 30px rgba(0,0,0,.22);overflow:hidden;width:1236px}}
.title{{background:#107c41;color:#fff;padding:8px 14px;font-size:13px;display:flex;gap:10px;align-items:center}}
.title b{{font-weight:600}} .title .r{{margin-left:auto;opacity:.85}}
.ribbon{{background:#f3f3f3;border-bottom:1px solid #d4d4d4;padding:6px 14px;font-size:12.5px;color:#333;display:flex;gap:18px}}
.ribbon span.on{{color:#107c41;border-bottom:2px solid #107c41;padding-bottom:2px}}
.fx{{display:flex;border-bottom:1px solid #d4d4d4;font-size:12.5px}} .fx .nm{{width:90px;padding:4px 8px;border-right:1px solid #d4d4d4}}
.fx .f{{padding:4px 10px;color:#333}} .fx .i{{padding:4px 10px;color:#888;border-right:1px solid #d4d4d4;font-style:italic}}
.grid{{overflow:hidden;max-height:640px}} table{{border-collapse:collapse;table-layout:fixed;font-size:12.5px}}
th{{background:#f3f3f3;color:#555;font-weight:400;border:1px solid #dadada;height:21px;font-size:12px}}
th.rn{{width:38px}} td{{border:1px solid #e3e3e3;padding:0 6px;height:21px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:0}}
td.hdr{{background:#2e8b57;color:#fff;font-weight:600;position:relative}} .flt{{position:absolute;right:3px;top:2px;background:#fff;color:#444;
border:1px solid #bbb;font-size:9px;line-height:13px;padding:0 2px}}
td.num{{text-align:right}} td.ctr{{text-align:center}} td.lnk{{color:#0563c1;text-decoration:underline}}
td.sel{{outline:2px solid #107c41;outline-offset:-2px}}
.tabs{{background:#f3f3f3;border-top:1px solid #d4d4d4;padding:4px 10px;font-size:12px;display:flex;gap:4px}}
.tab{{padding:3px 12px;color:#444}} .tab.on{{background:#fff;color:#107c41;font-weight:600;border-bottom:2px solid #107c41}}
.status{{background:#107c41;color:#e8f5ee;font-size:11.5px;padding:3px 14px;display:flex;gap:20px}}
</style></head><body><div class="win">
<div class="title"><b>{html.escape(file_name)}</b><span>· exported by ScrapeKit</span><span class="r">{ws.max_row - 1:,} rows · frozen header · auto-filter</span></div>
<div class="ribbon"><span>File</span><span class="on">Home</span><span>Insert</span><span>Data</span><span>View</span></div>
<div class="fx"><span class="nm">C2</span><span class="i">fx</span><span class="f">{price}</span></div>
<div class="grid"><table><tr><th class="rn"></th>{head}</tr>{"".join(body)}</table></div>
<div class="tabs">{tabs}</div>
<div class="status"><span>Ready</span><span>Price column is numeric (format #,##0.00) · URLs are clickable</span></div>
</div></body></html>"""


def shot_html(page, content, path):
    page.set_content(content)
    page.wait_for_timeout(300)
    page.locator(".win").screenshot(path=str(path))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--chrome", action="store_true", help="use the installed Google Chrome")
    parser.add_argument("--only", default="", help="comma-separated subset: dashboard,job,excel,swagger")
    parser.add_argument("--job", default="1", help="job id for the job page and the Excel export")
    args = parser.parse_args()
    only = set(filter(None, args.only.split(",")))
    want = lambda n: not only or n in only  # noqa: E731
    OUT.mkdir(parents=True, exist_ok=True)
    host = args.base_url.rstrip("/")

    with sync_playwright() as p:
        browser = p.chromium.launch(channel="chrome" if args.chrome else None)
        page = browser.new_page(viewport={"width": 1360, "height": 860}, device_scale_factor=1)

        if want("dashboard"):
            page.goto(host + "/")
            page.wait_for_timeout(500)
            page.screenshot(path=str(OUT / "dashboard.png"), full_page=True)

        if want("job"):
            page.goto(f"{host}/jobs/{args.job}")
            page.wait_for_timeout(500)
            page.screenshot(path=str(OUT / "job-detail.png"), full_page=True)

        if want("excel"):
            with urllib.request.urlopen(f"{host}/api/jobs/{args.job}/export?format=xlsx", timeout=60) as r:
                data = r.read()
                cd = r.headers.get("Content-Disposition", "")
            name = (re.search(r'filename="?([^";]+)', cd) or [None, "export.xlsx"])[1]
            page.set_viewport_size({"width": 1300, "height": 900})
            shot_html(page, excel_page(data, name), OUT / "excel-export.png")

        if want("swagger"):
            page.set_viewport_size({"width": 1280, "height": 1100})
            page.goto(f"{host}/swagger-ui/index.html")
            page.wait_for_selector(".opblock", timeout=30_000)
            page.wait_for_timeout(600)
            page.screenshot(path=str(OUT / "swagger.png"))

        browser.close()
    print("Screenshots written to", OUT)


if __name__ == "__main__":
    main()
