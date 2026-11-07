"""
Rende in SVG e PNG i blocchi Mermaid di un file Markdown, per le figure della relazione (docs/figure/).

Requisiti, una tantum:
    npm install --prefix tools mermaid@11
    pip install playwright && python -m playwright install chromium

Uso:
    python tools/rendi_diagrammi.py docs/ER.md docs/figure/er.svg
    python tools/rendi_diagrammi.py docs/ARCHITETTURA.md docs/figure/architettura.svg
Con più blocchi nello stesso file escono architettura-1.svg, architettura-2.svg, ... nell'ordine del file.
"""
import pathlib
import re
import sys

from playwright.sync_api import sync_playwright

md, uscita = sys.argv[1], sys.argv[2]
blocchi = re.findall(r"```mermaid\n(.*?)```", pathlib.Path(md).read_text(encoding="utf-8"), re.S)
libreria = pathlib.Path(__file__).parent / "node_modules/mermaid/dist/mermaid.min.js"
if not libreria.exists():
    sys.exit("Manca mermaid: npm install --prefix tools mermaid@11")

with sync_playwright() as pw:
    browser = pw.chromium.launch()
    pagina = browser.new_page(viewport={"width": 1800, "height": 1200}, device_scale_factor=2)
    pagina.set_content("<html><body style='margin:0;background:#fff'><div id=d></div></body></html>")
    pagina.add_script_tag(path=str(libreria))
    for i, blocco in enumerate(blocchi):
        svg = pagina.evaluate("""async (sorgente) => {
            mermaid.initialize({startOnLoad: false, theme: 'neutral',
                fontFamily: 'Liberation Sans, Arial, sans-serif',
                er: {useMaxWidth: false}, flowchart: {useMaxWidth: false, htmlLabels: false},
                sequence: {useMaxWidth: false}, state: {useMaxWidth: false}});
            const r = await mermaid.render('g' + Math.floor(Math.random() * 1e9), sorgente);
            document.getElementById('d').innerHTML = r.svg;
            return r.svg; }""", blocco)
        nome = uscita if len(blocchi) == 1 else uscita.replace(".svg", f"-{i + 1}.svg")
        pathlib.Path(nome).write_text(svg, encoding="utf-8")
        pagina.locator("#d svg").screenshot(path=nome.replace(".svg", ".png"))
        print("scritto", nome)
    browser.close()
