#!/usr/bin/env python3
"""
Genera la migrazione Flyway che carica una versione della Tassonomia Cyber ACN (TC-ACN).

Fonti (tutte versionate nel repository, in docs/tassonomia/):
  - JSON MISP ufficiale (indicato dal PDF ACN): predicati, valori, slug, descrizioni;
  - codici ufficiali dei valori estratti dal PDF (es. "TT:MA_RA"), uno per riga con la sezione;
  - struttura presente solo nel PDF: macrocategorie e sotto-predicati di Involved Asset.

Il JSON e il PDF elencano i valori nello stesso ordine: lo script li allinea per posizione
dentro ciascun predicato e si ferma se conteggi o ordine dei predicati non coincidono.

Uso:
  python3 tools/genera_migrazione_tassonomia.py \
      --json docs/tassonomia/acn-machinetag-v2.0.json \
      --codici docs/tassonomia/codici-pdf-v2.0.txt \
      --struttura docs/tassonomia/struttura-pdf-v2.0.json \
      --versione 2.0 --data-acquisizione 2026-10-21 \
      --pdf-url URL --pdf-sha256 HASH --json-url URL \
      --out src/main/resources/db/migration/V3__tassonomia_acn_v2_0.sql

Una nuova versione della tassonomia si carica rigenerando con i nuovi file e un nuovo
numero di migrazione: il codice applicativo non cambia.
"""
import argparse
import json
import re
from collections import OrderedDict

PRED_INVOLVED_ASSET = "AC:IN"


def sql(s):
    if s is None:
        return "NULL"
    return "'" + str(s).replace("'", "''") + "'"


def normalizza(s):
    return re.sub(r"[^a-z0-9]+", " ", s.lower()).strip()


def nome_breve(expanded, prefisso):
    """Toglie dal nome MISP il nome del sotto-predicato (es. 'Hardware Network Device Router' -> 'Router')."""
    parole, pref = expanded.split(), normalizza(prefisso).split()
    if [normalizza(p) for p in parole[:len(pref)]] == pref:
        resto = " ".join(parole[len(pref):]).strip()
        return resto or "Other"
    return expanded.strip()


def main():
    ap = argparse.ArgumentParser()
    for a in ("json", "codici", "struttura", "versione", "data_acquisizione",
              "pdf_url", "pdf_sha256", "json_url", "out"):
        ap.add_argument("--" + a.replace("_", "-"), dest=a, required=True)
    ap.add_argument("--id-versione", type=int, default=1)
    args = ap.parse_args()

    misp = json.load(open(args.json, encoding="utf-8"))
    struttura = json.load(open(args.struttura, encoding="utf-8"))
    codici = [l.split() for l in open(args.codici, encoding="utf-8") if l.strip()]

    # Codici PDF raggruppati per predicato (prefisso prima di "_", es. "TT:MA")
    pdf = OrderedDict()
    for sezione, codice in codici:
        pdf.setdefault(codice.split("_", 1)[0], []).append((sezione, codice))

    predicati = OrderedDict((p["value"], p) for p in misp["predicates"])
    valori = OrderedDict((v["predicate"], v["entry"]) for v in misp["values"])
    if list(predicati) != list(valori):
        raise SystemExit("Ordine dei predicati diverso fra 'predicates' e 'values' nel JSON")
    if len(pdf) != len(predicati):
        raise SystemExit(f"Predicati: PDF {len(pdf)} / JSON {len(predicati)}")

    sotto = OrderedDict((s["codice"], s["nome"]) for s in struttura["sottopredicati_involved_asset"])
    macro = OrderedDict((m["codice"], m) for m in struttura["macrocategorie"])

    righe, prossimo_id = [], [0]
    contatori = {"MACROCATEGORIA": 0, "PREDICATO": 0, "SOTTOPREDICATO": 0, "VALORE": 0}

    def inserisci(padre, livello, codice, slug, nome, descr, sezione, ordine):
        prossimo_id[0] += 1
        contatori[livello] += 1
        righe.append(f"({prossimo_id[0]}, {args.id_versione}, {padre if padre else 'NULL'}, "
                     f"{sql(livello)}, {sql(codice)}, {sql(slug)}, {sql(nome)}, {sql(descr)}, "
                     f"{sql(sezione)}, {ordine})")
        return prossimo_id[0]

    id_macro = {}
    for i, (cm, m) in enumerate(macro.items(), 1):
        id_macro[cm] = inserisci(None, "MACROCATEGORIA", cm, None, m["nome"], None, m["sezione"], i)

    ordine_in_macro = {}
    for (cod_pred, voci_pdf), (slug_pred, pred) in zip(pdf.items(), predicati.items()):
        entries = valori[slug_pred]
        if len(voci_pdf) != len(entries):
            raise SystemExit(f"{cod_pred}/{slug_pred}: PDF {len(voci_pdf)} valori, JSON {len(entries)}")
        cm = cod_pred.split(":")[0]
        ordine_in_macro[cm] = ordine_in_macro.get(cm, 0) + 1
        sez_pred = ".".join(voci_pdf[0][0].split(".")[:3])
        id_pred = inserisci(id_macro[cm], "PREDICATO", cod_pred, slug_pred, pred["expanded"], None,
                            sez_pred, ordine_in_macro[cm])

        id_sotto, ordine_figli = {}, {}
        for (sezione, codice), e in zip(voci_pdf, entries):
            padre, nome = id_pred, e["expanded"].strip()
            if cod_pred == PRED_INVOLVED_ASSET:
                cs = codice.split("_")[1]
                if cs not in sotto:
                    raise SystemExit(f"Sotto-predicato sconosciuto: {cs} ({codice})")
                if cs not in id_sotto:
                    ordine_figli[id_pred] = ordine_figli.get(id_pred, 0) + 1
                    id_sotto[cs] = inserisci(id_pred, "SOTTOPREDICATO", f"{cod_pred}_{cs}", None,
                                             sotto[cs], None, sez_pred, ordine_figli[id_pred])
                padre, nome = id_sotto[cs], nome_breve(nome, sotto[cs])
            ordine_figli[padre] = ordine_figli.get(padre, 0) + 1
            inserisci(padre, "VALORE", codice, e["value"], nome, e.get("description"),
                      sezione, ordine_figli[padre])

    intestazione = f"""-- GENERATO da tools/genera_migrazione_tassonomia.py: non modificare a mano.
-- Tassonomia Cyber ACN (TC-ACN) versione {args.versione}, acquisita il {args.data_acquisizione}.
-- PDF ufficiale: {args.pdf_url}
-- SHA-256 PDF:  {args.pdf_sha256}
-- JSON MISP:    {args.json_url}
-- Conteggi attesi: {contatori['MACROCATEGORIA']} macrocategorie, {contatori['PREDICATO']} predicati,
--                  {contatori['SOTTOPREDICATO']} sotto-predicati, {contatori['VALORE']} valori.

INSERT INTO tassonomia_versione (id, codice, versione_documento, data_acquisizione, fonte_url,
                                 fonte_strutturata, sha256_documento, vigente)
VALUES ({args.id_versione}, {sql('TC-ACN-' + args.versione)}, {sql(args.versione)}, DATE {sql(args.data_acquisizione)},
        {sql(args.pdf_url)}, {sql(args.json_url)}, {sql(args.pdf_sha256)}, TRUE);

INSERT INTO tassonomia_voce (id, versione_id, padre_id, livello, codice, slug_misp, nome, descrizione, sezione, ordine)
VALUES
"""
    with open(args.out, "w", encoding="utf-8") as f:
        f.write(intestazione + ",\n".join(righe) + ";\n")
    print("Scritto", args.out, contatori)


if __name__ == "__main__":
    main()
