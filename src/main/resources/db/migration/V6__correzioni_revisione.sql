-- Correzioni emerse dalla revisione di fine fase 3.
-- Migrazione additiva: le righe esistenti restano valide e verificabili.

-- Quattro occhi: autori di ciascuna versione, cioè chi ne ha scritto contenuto o classificazione.
-- Chi compare fra gli autori non può registrarne la trasmissione (VersioneDocumento.trasmetti).
CREATE TABLE versione_autore (
    versione_documento_id  BIGINT       NOT NULL REFERENCES versione_documento (id),
    username               VARCHAR(50)  NOT NULL,
    PRIMARY KEY (versione_documento_id, username)
);

-- Per le versioni esistenti gli autori noti sono chi le ha create e chi le ha modificate per ultimo.
INSERT INTO versione_autore (versione_documento_id, username)
SELECT id, creata_da FROM versione_documento;
INSERT INTO versione_autore (versione_documento_id, username)
SELECT id, modificata_da FROM versione_documento WHERE modificata_da <> creata_da;

-- Intestazione sigillata: soggetto e incidente come erano al momento della trasmissione, inclusi
-- nell'impronta. Schema dell'impronta per riga: le versioni trasmesse prima di questa migrazione restano
-- verificabili con pw29-catena-v1; le nuove usano pw29-catena-v2 (ADR-0006).
ALTER TABLE versione_documento ADD COLUMN intestazione_json VARCHAR(4000);
ALTER TABLE versione_documento ADD COLUMN schema_impronta VARCHAR(20);
UPDATE versione_documento SET schema_impronta = 'pw29-catena-v1' WHERE stato = 'TRASMESSO';
ALTER TABLE versione_documento ADD CONSTRAINT ck_versione_schema_impronta CHECK (stato <> 'TRASMESSO'
    OR schema_impronta = 'pw29-catena-v1'
    OR (schema_impronta = 'pw29-catena-v2' AND intestazione_json IS NOT NULL));

-- Reg. (UE) 2024/2690, art. 10: la soglia di utenti si calcola sugli "utenti di tale servizio"
-- e i criteri dell'art. 10 valgono per i servizi gestiti e di sicurezza gestiti. Gli utenti si contano
-- come prevede l'art. 3, par. 3 (clienti con contratto e persone associate ai clienti business).
ALTER TABLE livello_servizio ADD COLUMN utenti_ue INTEGER;
ALTER TABLE livello_servizio ADD COLUMN servizio_gestito BOOLEAN;
UPDATE livello_servizio SET utenti_ue = 9000, servizio_gestito = TRUE  WHERE servizio = 'SOC_MDR';
UPDATE livello_servizio SET utenti_ue = 5400, servizio_gestito = TRUE  WHERE servizio = 'WAF_GESTITO';
UPDATE livello_servizio SET utenti_ue = 9000, servizio_gestito = FALSE WHERE servizio = 'PORTALE_TICKETING';
ALTER TABLE livello_servizio ALTER COLUMN utenti_ue SET NOT NULL;
ALTER TABLE livello_servizio ALTER COLUMN servizio_gestito SET NOT NULL;
ALTER TABLE livello_servizio ADD CONSTRAINT ck_livello_utenti CHECK (utenti_ue >= 0);

-- Il numero di utenti non è più un parametro unico del soggetto ma del singolo servizio.
ALTER TABLE soglia_set DROP COLUMN utenti_ue;

-- Dimensione del soggetto (Raccomandazione 2003/361/CE, richiamata dall'art. 3, c. 2, D.Lgs. 138/2024)
-- e data di inserimento nell'elenco NIS, da cui decorre l'obbligo di notifica (FAQ ACN ISB.G.3).
ALTER TABLE soggetto ADD COLUMN dipendenti INTEGER;
ALTER TABLE soggetto ADD COLUMN totale_bilancio NUMERIC(15, 2);
ALTER TABLE soggetto ADD COLUMN iscrizione_elenco_nis_il DATE;
UPDATE soggetto SET dipendenti = 85, totale_bilancio = 5200000.00, iscrizione_elenco_nis_il = DATE '2025-04-30',
    motivazione_tipo = 'Media impresa ai sensi della Raccomandazione 2003/361/CE: 85 dipendenti (ULA), fatturato 7,0 M EUR, '
        || 'totale di bilancio 5,2 M EUR; supera i massimali della piccola impresa (art. 3, c. 2, D.Lgs. 138/2024) e non quelli '
        || 'della media impresa. Fornitore di servizi di sicurezza gestiti (Allegato I, settore 9): soggetto importante '
        || '(art. 6 D.Lgs. 138/2024). Inserito nell''elenco dei soggetti NIS il 30/04/2025 (dati del contesto fittizio).'
WHERE codice_fiscale = '00000000000';
ALTER TABLE soggetto ALTER COLUMN dipendenti SET NOT NULL;
ALTER TABLE soggetto ALTER COLUMN totale_bilancio SET NOT NULL;
ALTER TABLE soggetto ALTER COLUMN iscrizione_elenco_nis_il SET NOT NULL;
