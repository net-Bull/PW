-- Fase 3: catena di hash dei documenti trasmessi, versioni correttive (rettifiche),
-- riclassificazione tardiva con traccia prima/dopo (ADR-0006, ADR-0007).

-- Rettifica: nuova versione di un adempimento già trasmesso. È una rettifica se motivo_rettifica
-- è valorizzato; derivata_da_id punta alla versione trasmessa che corregge.
ALTER TABLE versione_documento ADD COLUMN motivo_rettifica VARCHAR(2000);

-- Istante, letto dall'orologio del server, in cui la trasmissione è stata registrata nel sistema
-- (trasmessa_il è invece l'istante di invio sul portale dichiarato dall'approvatore e può precederlo).
-- Fissa l'ordine della catena di hash.
ALTER TABLE versione_documento ADD COLUMN trasmissione_registrata_il TIMESTAMP WITH TIME ZONE;

-- Un documento trasmesso è sempre sigillato: impronta, anello precedente e registrazione presenti.
ALTER TABLE versione_documento ADD CONSTRAINT ck_versione_sigillata CHECK (stato <> 'TRASMESSO'
    OR (impronta_sha256 IS NOT NULL AND impronta_precedente IS NOT NULL AND trasmissione_registrata_il IS NOT NULL));

-- La catena non si biforca: ogni impronta è l'anello precedente di al più un documento
-- (due trasmissioni concorrenti sullo stesso incidente non possono agganciarsi allo stesso anello).
CREATE UNIQUE INDEX uq_versione_impronta ON versione_documento (impronta_sha256);
CREATE UNIQUE INDEX uq_versione_impronta_precedente ON versione_documento (impronta_precedente);

-- Riclassificazione: oltre a significatività e voci, si conservano le categorie IS prima/dopo
-- e la valutazione che ha motivato il cambio di significatività (se c'è stata).
ALTER TABLE storico_riclassificazione ADD COLUMN categorie_is_prima VARCHAR(100) DEFAULT '' NOT NULL;
ALTER TABLE storico_riclassificazione ADD COLUMN categorie_is_dopo VARCHAR(100) DEFAULT '' NOT NULL;
ALTER TABLE storico_riclassificazione ADD COLUMN valutazione_id BIGINT REFERENCES valutazione_significativita (id);
ALTER TABLE storico_riclassificazione ADD COLUMN stato_incidente_prima VARCHAR(20) DEFAULT 'SIGNIFICATIVO' NOT NULL;
ALTER TABLE storico_riclassificazione ADD COLUMN stato_incidente_dopo VARCHAR(20) DEFAULT 'SIGNIFICATIVO' NOT NULL;

CREATE INDEX ix_riclassificazione_incidente ON storico_riclassificazione (incidente_id);
