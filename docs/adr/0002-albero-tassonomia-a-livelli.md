# ADR-0002: Tassonomia ACN come albero con livello esplicito

- **Stato**: accettata

## Contesto
La Tassonomia Cyber ACN v2.0 ha 4 macrocategorie, 21 predicati, 19 sotto-predicati (solo sotto
Involved Asset) e 219 valori. La traccia chiede di caricarla a dati e di far coincidere i conteggi
con il documento ufficiale.

## Alternative valutate
1. **Due tabelle (categoria, sottocategoria)** – semplice, ma costringe a scegliere quali livelli
   ufficiali perdere (macrocategorie o sotto-predicati). Scartata.
2. **Una tabella per livello (4 tabelle)** – fedele ma rigida: Involved Asset ha un livello in più
   degli altri predicati, quindi i valori avrebbero due possibili tabelle padre. Scartata.
3. **Albero unico con colonna `livello` e riferimento al padre** – rappresenta profondità diverse
   nello stesso modello; il conteggio è una sola `GROUP BY livello`.

## Decisione
Alternativa 3, con vincolo `CHECK` sui livelli ammessi e codice ufficiale univoco per versione.
Le regole strutturali (es. un sotto-predicato solo sotto Involved Asset) sono verificate da test.

## Conseguenze
- Un incidente si classifica referenziando solo voci di livello `VALORE`: va imposto lato server.
- Le query sull'albero richiedono join ricorsive o un percorso precalcolato, se servisse.
