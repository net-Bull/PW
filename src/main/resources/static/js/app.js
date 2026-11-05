/*
 * Comportamenti dell'interfaccia. Le pagine funzionano anche senza script: qui ci sono solo il conto alla
 * rovescia dei termini, la verifica del documento mentre si compila e il filtro delle voci della tassonomia.
 */
(function () {
    "use strict";

    // Orologio: si allinea all'ora del server al caricamento, così il conto alla rovescia usa lo stesso orologio
    // dei termini calcolati dal server.
    var adessoServer = Date.parse(document.body.getAttribute("data-adesso") || "");
    var scarto = isNaN(adessoServer) ? 0 : adessoServer - Date.now();

    function durata(ms) {
        var minuti = Math.floor(ms / 60000);
        var giorni = Math.floor(minuti / 1440), ore = Math.floor(minuti / 60) % 24, min = minuti % 60;
        if (giorni > 0) return giorni + " g " + ore + " h";
        if (ore > 0) return ore + " h " + min + " min";
        return min + " min";
    }

    function aggiornaConti() {
        var ora = Date.now() + scarto;
        document.querySelectorAll("[data-termine]").forEach(function (el) {
            var t = Date.parse(el.getAttribute("data-termine"));
            if (isNaN(t)) return;
            var d = t - ora;
            el.textContent = d < 0 ? "scaduto da " + durata(-d) : "mancano " + durata(d);
        });
    }
    if (document.querySelector("[data-termine]")) {
        aggiornaConti();
        setInterval(aggiornaConti, 30000);
    }

    // Verifica del documento mentre si compila: invia il modulo all'interfaccia (senza salvare) e mostra i
    // campi mancanti per l'adempimento. Il token CSRF viaggia nell'intestazione.
    var editor = document.getElementById("editor");
    var pannello = document.getElementById("esito-verifica");
    if (editor && pannello && editor.getAttribute("data-verifica")) {
        var tokenNome = document.querySelector("meta[name=_csrf_header]");
        var token = document.querySelector("meta[name=_csrf]");
        var attesa = null;

        function mostra(esito) {
            var mancanti = {};
            (esito.violazioni || []).forEach(function (v) { mancanti[v.campo] = true; });
            editor.querySelectorAll("[data-campo]").forEach(function (c) {
                c.classList.toggle("manca", !!mancanti[c.getAttribute("data-campo")]);
            });
            pannello.innerHTML = "";
            var stato = document.createElement("p");
            if (esito.completo) {
                stato.className = "completo";
                stato.textContent = "Completo per l'adempimento.";
                pannello.appendChild(stato);
                return;
            }
            stato.className = "incompleto";
            stato.textContent = "Mancano " + esito.violazioni.length + (esito.violazioni.length === 1 ? " elemento" : " elementi") + ":";
            pannello.appendChild(stato);
            var ul = document.createElement("ul");
            esito.violazioni.forEach(function (v) {
                var li = document.createElement("li");
                li.textContent = v.etichetta + (v.messaggio && v.messaggio.indexOf("Campo obbligatorio") !== 0 ? ": " + v.messaggio : "");
                ul.appendChild(li);
            });
            pannello.appendChild(ul);
        }

        function verifica() {
            var corpo = new URLSearchParams(new FormData(editor));
            var intestazioni = { "Content-Type": "application/x-www-form-urlencoded" };
            if (tokenNome && token) intestazioni[tokenNome.content] = token.content;
            fetch(editor.getAttribute("data-verifica"), { method: "POST", body: corpo, headers: intestazioni, credentials: "same-origin" })
                .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, j: j }; }); })
                .then(function (r) {
                    if (r.ok) { mostra(r.j); }
                    else {
                        pannello.innerHTML = "";
                        var p = document.createElement("p");
                        p.className = "incompleto";
                        p.textContent = r.j.errore || "Verifica non riuscita";
                        pannello.appendChild(p);
                    }
                })
                .catch(function () { /* la verifica completa resta quella del salvataggio */ });
        }

        // La ricerca fra le voci non cambia il documento: non fa partire la verifica.
        var daIgnorare = function (e) { return e.target && e.target.type === "search"; };
        editor.addEventListener("input", function (e) {
            if (daIgnorare(e)) return;
            clearTimeout(attesa);
            attesa = setTimeout(verifica, 500);
        });
        editor.addEventListener("change", function (e) {
            if (daIgnorare(e)) return;
            clearTimeout(attesa);
            attesa = setTimeout(verifica, 150);
        });
    }

    // Filtro e conteggio delle voci della tassonomia.
    document.querySelectorAll(".voci").forEach(function (box) {
        var filtro = box.querySelector("input[type=search]");
        var conta = box.querySelector("[data-conta]");
        var correnti = box.querySelector(".scelte-correnti");
        function aggiorna() {
            var scelte = box.querySelectorAll(".voci-elenco input:checked");
            if (conta) conta.textContent = scelte.length;
            if (correnti) {
                correnti.innerHTML = "";
                scelte.forEach(function (c) {
                    var el = document.createElement("code");
                    el.textContent = c.value;
                    correnti.appendChild(el);
                });
            }
        }
        box.addEventListener("change", function (e) { if (e.target.type !== "search") aggiorna(); });
        if (filtro) {
            filtro.addEventListener("input", function () {
                var q = filtro.value.trim().toLowerCase();
                box.querySelectorAll("details").forEach(function (d) {
                    var visibili = 0;
                    d.querySelectorAll(".scelta").forEach(function (s) {
                        var ok = !q || s.textContent.toLowerCase().indexOf(q) >= 0;
                        s.hidden = !ok;
                        if (ok) visibili++;
                    });
                    d.querySelectorAll(".predicato").forEach(function (p) {
                        var el = p.nextElementSibling, alcuna = false;
                        while (el && el.classList.contains("scelta")) { if (!el.hidden) alcuna = true; el = el.nextElementSibling; }
                        p.hidden = !alcuna;
                    });
                    d.hidden = visibili === 0;
                    if (q) d.open = visibili > 0;
                });
            });
        }
        aggiorna();
    });

    // Riclassificazione: il blocco della nuova valutazione si mostra solo se richiesto.
    var interruttore = document.getElementById("nuova-valutazione");
    var blocco = document.getElementById("blocco-valutazione");
    if (interruttore && blocco) {
        var allinea = function () { blocco.hidden = !interruttore.checked; };
        interruttore.addEventListener("change", allinea);
        allinea();
    }
})();
