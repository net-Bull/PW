-- Fase 4: descrizione di tabelle e colonne nel DDL stesso (COMMENT ON, supportato da PostgreSQL e da H2).
-- Il data dictionary docs/DATA-DICTIONARY.md e l'ER docs/ER.md sono generati da questi commenti e dallo schema
-- (SchemaDocumentazioneTest): così descrizioni e struttura non possono divergere.
-- Nessuna modifica a tabelle o dati.

-- tassonomia_versione -------------------------------------------------------------------------------
COMMENT ON TABLE tassonomia_versione IS 'Versione della Tassonomia Cyber ACN caricata a dati, con la fonte e l''impronta del documento ufficiale acquisito. Una sola versione è vigente; le classificazioni restano legate alla versione con cui sono state fatte (ADR-0001, ADR-0002).';
COMMENT ON COLUMN tassonomia_versione.id IS 'Identificativo.';
COMMENT ON COLUMN tassonomia_versione.codice IS 'Codice della versione nel sistema, es. TC-ACN-2.0.';
COMMENT ON COLUMN tassonomia_versione.versione_documento IS 'Versione dichiarata nel documento ACN.';
COMMENT ON COLUMN tassonomia_versione.data_acquisizione IS 'Data in cui il documento ufficiale è stato scaricato e verificato.';
COMMENT ON COLUMN tassonomia_versione.fonte_url IS 'Pagina ufficiale ACN da cui è stato acquisito il documento.';
COMMENT ON COLUMN tassonomia_versione.fonte_strutturata IS 'Fonte strutturata usata per l''albero (JSON MISP acn/machinetag).';
COMMENT ON COLUMN tassonomia_versione.sha256_documento IS 'Impronta SHA-256 del PDF ufficiale allegato (docs/tassonomia).';
COMMENT ON COLUMN tassonomia_versione.vigente IS 'Vero per la versione usata dalle nuove bozze.';

-- tassonomia_voce -----------------------------------------------------------------------------------
COMMENT ON TABLE tassonomia_voce IS 'Nodo dell''albero della tassonomia: macrocategoria, predicato, sotto-predicato (solo sotto Involved Asset) e valore. Solo i valori classificano un incidente.';
COMMENT ON COLUMN tassonomia_voce.id IS 'Identificativo.';
COMMENT ON COLUMN tassonomia_voce.versione_id IS 'Versione della tassonomia a cui appartiene la voce.';
COMMENT ON COLUMN tassonomia_voce.padre_id IS 'Voce di livello superiore; vuota per le macrocategorie.';
COMMENT ON COLUMN tassonomia_voce.livello IS 'MACROCATEGORIA, PREDICATO, SOTTOPREDICATO o VALORE.';
COMMENT ON COLUMN tassonomia_voce.codice IS 'Codice ufficiale del documento ACN (es. TT:MA_RA), univoco nella versione.';
COMMENT ON COLUMN tassonomia_voce.slug_misp IS 'Etichetta del machinetag MISP, per l''interoperabilità; non univoca.';
COMMENT ON COLUMN tassonomia_voce.nome IS 'Nome della voce come nel documento ufficiale.';
COMMENT ON COLUMN tassonomia_voce.descrizione IS 'Definizione della voce, se presente nella fonte.';
COMMENT ON COLUMN tassonomia_voce.sezione IS 'Sezione del documento ACN in cui compare il codice.';
COMMENT ON COLUMN tassonomia_voce.ordine IS 'Posizione fra le voci sorelle, per presentare l''albero nell''ordine del documento.';

-- soglia_set ----------------------------------------------------------------------------------------
COMMENT ON TABLE soglia_set IS 'Insieme versionato delle soglie del Reg. di esecuzione (UE) 2024/2690 per il soggetto, con data di validità: una valutazione usa le soglie vigenti alla data della conoscenza.';
COMMENT ON COLUMN soglia_set.id IS 'Identificativo.';
COMMENT ON COLUMN soglia_set.versione IS 'Codice della versione delle soglie, riportato in ogni valutazione.';
COMMENT ON COLUMN soglia_set.valida_dal IS 'Data da cui le soglie si applicano.';
COMMENT ON COLUMN soglia_set.fonte IS 'Riferimento normativo delle soglie.';
COMMENT ON COLUMN soglia_set.fatturato_annuo IS 'Fatturato annuo del soggetto (euro), base della soglia economica relativa.';
COMMENT ON COLUMN soglia_set.minuti_indisponibilita IS 'Durata oltre la quale l''indisponibilità completa di un servizio gestito è significativa (art. 10, lett. a).';
COMMENT ON COLUMN soglia_set.minuti_degrado IS 'Durata oltre la quale la disponibilità limitata è significativa se supera la quota di utenti (art. 10, lett. b).';
COMMENT ON COLUMN soglia_set.quota_utenti IS 'Quota degli utenti del servizio (0,05 = 5%) oltre la quale scattano le regole R2 e R4.';
COMMENT ON COLUMN soglia_set.tetto_utenti IS 'Soglia assoluta di utenti (art. 10, lett. b e d): vale la minore fra questa e la quota degli utenti del servizio.';
COMMENT ON COLUMN soglia_set.perdita_assoluta IS 'Soglia assoluta della perdita finanziaria diretta (euro, art. 3, par. 1, lett. a).';
COMMENT ON COLUMN soglia_set.quota_fatturato IS 'Quota del fatturato annuo per la soglia economica relativa; vale la minore fra le due.';
COMMENT ON COLUMN soglia_set.finestra_ricorrenza_mesi IS 'Finestra, in mesi, in cui si contano gli incidenti ricorrenti (art. 4).';
COMMENT ON COLUMN soglia_set.min_incidenti_ricorrenza IS 'Numero minimo di incidenti con la stessa causa apparente nella finestra (art. 4).';

-- soggetto ------------------------------------------------------------------------------------------
COMMENT ON TABLE soggetto IS 'Soggetto NIS che notifica (qui l''MSSP fittizio CyberWall S.r.l.), con i dati che ne motivano la qualifica e il referente CSIRT.';
COMMENT ON COLUMN soggetto.id IS 'Identificativo.';
COMMENT ON COLUMN soggetto.denominazione IS 'Ragione sociale.';
COMMENT ON COLUMN soggetto.codice_fiscale IS 'Codice fiscale (fittizio nel contesto del progetto).';
COMMENT ON COLUMN soggetto.tipo IS 'ESSENZIALE o IMPORTANTE (art. 6 D.Lgs. 138/2024): decide se si applica la categoria IS-4.';
COMMENT ON COLUMN soggetto.settore IS 'Settore e allegato del D.Lgs. 138/2024.';
COMMENT ON COLUMN soggetto.motivazione_tipo IS 'Motivazione della qualifica con i riferimenti normativi.';
COMMENT ON COLUMN soggetto.referente_nome IS 'Nome del referente CSIRT, interlocutore del CSIRT Italia (misura GV.RR-02).';
COMMENT ON COLUMN soggetto.referente_email IS 'E-mail del referente CSIRT, proposta nelle bozze.';
COMMENT ON COLUMN soggetto.referente_telefono IS 'Telefono del referente CSIRT, proposto nelle bozze.';
COMMENT ON COLUMN soggetto.dipendenti IS 'Effettivi, per la dimensione d''impresa (Racc. 2003/361/CE, richiamata dall''art. 3, c. 2).';
COMMENT ON COLUMN soggetto.totale_bilancio IS 'Totale di bilancio annuo (euro), per la dimensione d''impresa.';
COMMENT ON COLUMN soggetto.iscrizione_elenco_nis_il IS 'Data di inserimento nell''elenco dei soggetti NIS, da cui decorre l''obbligo di notifica (FAQ ACN ISB.G.3).';

-- livello_servizio ----------------------------------------------------------------------------------
COMMENT ON TABLE livello_servizio IS 'Servizio critico del soggetto con i livelli di servizio attesi (SL) definiti in autonomia (misura DE.CM-01): base della categoria IS-3 e, per i servizi gestiti, delle regole R1-R4.';
COMMENT ON COLUMN livello_servizio.id IS 'Identificativo.';
COMMENT ON COLUMN livello_servizio.soggetto_id IS 'Soggetto che eroga il servizio.';
COMMENT ON COLUMN livello_servizio.servizio IS 'Codice del servizio (es. SOC_MDR), univoco per soggetto.';
COMMENT ON COLUMN livello_servizio.descrizione IS 'Descrizione del servizio.';
COMMENT ON COLUMN livello_servizio.max_indisponibilita_minuti IS 'Indisponibilità completa massima attesa, in minuti: oltre scatta IS-3.';
COMMENT ON COLUMN livello_servizio.max_degrado_minuti IS 'Degrado continuativo massimo atteso, in minuti: oltre scatta IS-3.';
COMMENT ON COLUMN livello_servizio.fonte IS 'Origine del livello di servizio atteso.';
COMMENT ON COLUMN livello_servizio.utenti_ue IS 'Utenti del servizio nell''Unione (art. 3, par. 3, del regolamento), base della quota del 5%.';
COMMENT ON COLUMN livello_servizio.servizio_gestito IS 'Vero per i servizi gestiti o di sicurezza gestiti, a cui si applicano le regole R1-R4 (art. 10).';

-- utente --------------------------------------------------------------------------------------------
COMMENT ON TABLE utente IS 'Utente dell''applicazione con il suo profilo (ADR-0009).';
COMMENT ON COLUMN utente.id IS 'Identificativo.';
COMMENT ON COLUMN utente.username IS 'Nome utente, usato negli autori dei documenti e nell''audit.';
COMMENT ON COLUMN utente.password_hash IS 'Hash della password (BCrypt con prefisso {bcrypt}).';
COMMENT ON COLUMN utente.nome_completo IS 'Nome e cognome o funzione.';
COMMENT ON COLUMN utente.ruolo IS 'OPERATORE (redige e invia in approvazione) o APPROVATORE (respinge e registra la trasmissione).';
COMMENT ON COLUMN utente.attivo IS 'Falso per un utente disabilitato.';

-- incidente -----------------------------------------------------------------------------------------
COMMENT ON TABLE incidente IS 'Incidente registrato, con il momento della rilevazione, quello della conoscenza da cui decorrono i termini e la conclusione della gestione (ADR-0003).';
COMMENT ON COLUMN incidente.id IS 'Identificativo.';
COMMENT ON COLUMN incidente.codice IS 'Codice leggibile, INC-anno-progressivo.';
COMMENT ON COLUMN incidente.soggetto_id IS 'Soggetto a cui è occorso l''incidente.';
COMMENT ON COLUMN incidente.titolo IS 'Titolo breve.';
COMMENT ON COLUMN incidente.descrizione IS 'Descrizione dell''evento osservato.';
COMMENT ON COLUMN incidente.stato IS 'IN_VALUTAZIONE, NON_SIGNIFICATIVO, SIGNIFICATIVO, DECLASSATO o CHIUSO (macchina a stati StatoIncidente).';
COMMENT ON COLUMN incidente.rilevato_il IS 'Primo evento rilevante osservato.';
COMMENT ON COLUMN incidente.registrato_il IS 'Istante di registrazione nel sistema.';
COMMENT ON COLUMN incidente.registrato_da IS 'Utente che ha registrato l''incidente.';
COMMENT ON COLUMN incidente.conoscenza_il IS 'Evidenza di incidente significativo: da qui le 24 h e le 72 h (art. 25, c. 5). Registrata una sola volta.';
COMMENT ON COLUMN incidente.conoscenza_motivazione IS 'Elementi oggettivi che fondano la conoscenza.';
COMMENT ON COLUMN incidente.conoscenza_registrata_il IS 'Istante in cui la conoscenza è stata registrata.';
COMMENT ON COLUMN incidente.conoscenza_registrata_da IS 'Utente che ha registrato la conoscenza.';
COMMENT ON COLUMN incidente.conclusione_gestione_il IS 'Conclusione della gestione, registrata una sola volta: fissa il termine della relazione finale se l''incidente era in corso (lett. e).';
COMMENT ON COLUMN incidente.versione IS 'Contatore per il controllo ottimistico delle modifiche concorrenti.';

-- valutazione_significativita -----------------------------------------------------------------------
COMMENT ON TABLE valutazione_significativita IS 'Storico delle valutazioni di significatività: input, soglie usate ed esito restano anche dopo una riclassificazione.';
COMMENT ON COLUMN valutazione_significativita.id IS 'Identificativo.';
COMMENT ON COLUMN valutazione_significativita.incidente_id IS 'Incidente valutato.';
COMMENT ON COLUMN valutazione_significativita.eseguita_il IS 'Istante della valutazione.';
COMMENT ON COLUMN valutazione_significativita.eseguita_da IS 'Utente che ha eseguito la valutazione.';
COMMENT ON COLUMN valutazione_significativita.soglia_set_id IS 'Insieme di soglie vigente alla data della conoscenza.';
COMMENT ON COLUMN valutazione_significativita.servizio IS 'Servizio coinvolto, di cui si usano livelli di servizio e utenti.';
COMMENT ON COLUMN valutazione_significativita.input_json IS 'Grandezze inserite (minuti, utenti, perdite, compromissioni) in JSON.';
COMMENT ON COLUMN valutazione_significativita.significativo IS 'Esito: vero se scatta almeno una regola R o una categoria IS (ADR-0004).';
COMMENT ON COLUMN valutazione_significativita.regole_attivate IS 'Regole R1-R8 del regolamento soddisfatte, separate da virgola.';
COMMENT ON COLUMN valutazione_significativita.categorie_is IS 'Categorie IS-1..IS-4 dell''ACN soddisfatte, separate da virgola.';

-- adempimento ---------------------------------------------------------------------------------------
COMMENT ON TABLE adempimento IS 'Adempimento verso il CSIRT Italia (pre-notifica, notifica, relazione intermedia, mensile o finale). Le scadenze non sono memorizzate: si calcolano dai fatti.';
COMMENT ON COLUMN adempimento.id IS 'Identificativo.';
COMMENT ON COLUMN adempimento.incidente_id IS 'Incidente a cui si riferisce.';
COMMENT ON COLUMN adempimento.tipo IS 'PRE_NOTIFICA, NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_MENSILE o RELAZIONE_FINALE.';
COMMENT ON COLUMN adempimento.progressivo IS 'Numero d''ordine fra gli adempimenti dello stesso tipo (intermedie e mensili).';
COMMENT ON COLUMN adempimento.stato IS 'DA_REDIGERE, IN_LAVORAZIONE o TRASMESSO, derivato dalle versioni.';
COMMENT ON COLUMN adempimento.richiesto_il IS 'Ricezione della richiesta del CSIRT (solo relazione intermedia).';
COMMENT ON COLUMN adempimento.scadenza_richiesta IS 'Termine indicato dal CSIRT nella richiesta, se presente.';
COMMENT ON COLUMN adempimento.riferimento_richiesta IS 'Riferimento della richiesta del CSIRT.';
COMMENT ON COLUMN adempimento.trasmesso_il IS 'Istante del primo invio; resta anche dopo una rettifica.';

-- versione_documento --------------------------------------------------------------------------------
COMMENT ON TABLE versione_documento IS 'Versione del documento di un adempimento: bozza, in approvazione o trasmessa. Una versione trasmessa è immutabile e sigillata nella catena di hash dell''incidente (ADR-0006, ADR-0007).';
COMMENT ON COLUMN versione_documento.id IS 'Identificativo.';
COMMENT ON COLUMN versione_documento.adempimento_id IS 'Adempimento a cui appartiene la versione.';
COMMENT ON COLUMN versione_documento.numero IS 'Numero della versione nell''adempimento.';
COMMENT ON COLUMN versione_documento.stato IS 'BOZZA, IN_APPROVAZIONE o TRASMESSO (macchina a stati StatoDocumento).';
COMMENT ON COLUMN versione_documento.contenuto_json IS 'Contenuto del documento in JSON canonico (ContenutoDocumento); i campi previsti dipendono dall''adempimento.';
COMMENT ON COLUMN versione_documento.tassonomia_versione_id IS 'Versione della tassonomia vigente alla creazione della versione.';
COMMENT ON COLUMN versione_documento.derivata_da_id IS 'Versione trasmessa da cui sono stati ripresi i dati, o che la rettifica corregge.';
COMMENT ON COLUMN versione_documento.creata_il IS 'Istante di creazione.';
COMMENT ON COLUMN versione_documento.creata_da IS 'Utente che ha creato la versione.';
COMMENT ON COLUMN versione_documento.modificata_il IS 'Ultima modifica.';
COMMENT ON COLUMN versione_documento.modificata_da IS 'Autore dell''ultima modifica.';
COMMENT ON COLUMN versione_documento.inviata_approvazione_il IS 'Ultimo invio in approvazione.';
COMMENT ON COLUMN versione_documento.inviata_approvazione_da IS 'Chi ha inviato in approvazione: non può registrarne la trasmissione.';
COMMENT ON COLUMN versione_documento.nota_respingimento IS 'Nota dell''approvatore all''ultimo respingimento.';
COMMENT ON COLUMN versione_documento.approvata_da IS 'Approvatore che ha registrato la trasmissione.';
COMMENT ON COLUMN versione_documento.trasmessa_il IS 'Istante di invio sul portale ACN dichiarato dall''approvatore.';
COMMENT ON COLUMN versione_documento.riferimento_trasmissione IS 'Riferimento ricevuto dal portale ACN.';
COMMENT ON COLUMN versione_documento.impronta_sha256 IS 'Impronta SHA-256 del documento trasmesso, anello della catena.';
COMMENT ON COLUMN versione_documento.impronta_precedente IS 'Impronta dell''anello precedente (o genesi dal codice incidente): univoca, la catena non si biforca.';
COMMENT ON COLUMN versione_documento.versione IS 'Contatore per il controllo ottimistico delle modifiche concorrenti.';
COMMENT ON COLUMN versione_documento.motivo_rettifica IS 'Motivo della rettifica; valorizzato solo per le versioni che correggono un documento trasmesso.';
COMMENT ON COLUMN versione_documento.trasmissione_registrata_il IS 'Istante, dall''orologio del server, in cui la trasmissione è stata registrata: fissa l''ordine della catena.';
COMMENT ON COLUMN versione_documento.intestazione_json IS 'Soggetto e incidente come erano all''invio, sigillati nell''impronta (copia conforme).';
COMMENT ON COLUMN versione_documento.schema_impronta IS 'Schema dell''input dell''impronta (pw29-catena-v1 o pw29-catena-v2), per verificare ogni anello con le sue regole.';

-- versione_autore -----------------------------------------------------------------------------------
COMMENT ON TABLE versione_autore IS 'Autori di una versione, cioè chi ne ha scritto contenuto o classificazione: nessuno di loro può registrarne la trasmissione (quattro occhi, ADR-0009).';
COMMENT ON COLUMN versione_autore.versione_documento_id IS 'Versione del documento.';
COMMENT ON COLUMN versione_autore.username IS 'Utente autore.';

-- classificazione -----------------------------------------------------------------------------------
COMMENT ON TABLE classificazione IS 'Voci della tassonomia (solo valori) associate a una versione del documento: ogni invio conserva la propria classificazione.';
COMMENT ON COLUMN classificazione.versione_documento_id IS 'Versione del documento classificata.';
COMMENT ON COLUMN classificazione.voce_id IS 'Valore della tassonomia.';

-- storico_riclassificazione -------------------------------------------------------------------------
COMMENT ON TABLE storico_riclassificazione IS 'Traccia di ogni riclassificazione: classificazione prima e dopo, momento, motivazione, documento trasmesso con la classificazione precedente e documento che la comunica (traccia PW 29, punto 3; ADR-0007).';
COMMENT ON COLUMN storico_riclassificazione.id IS 'Identificativo.';
COMMENT ON COLUMN storico_riclassificazione.incidente_id IS 'Incidente riclassificato.';
COMMENT ON COLUMN storico_riclassificazione.documento_prima_id IS 'Ultimo documento trasmesso con la classificazione precedente; vuoto per il ritorno a non significativo prima di ogni invio.';
COMMENT ON COLUMN storico_riclassificazione.documento_dopo_id IS 'Primo documento trasmesso che comunica la riclassificazione; vuoto finché non parte.';
COMMENT ON COLUMN storico_riclassificazione.significativo_prima IS 'Significatività prima del cambiamento.';
COMMENT ON COLUMN storico_riclassificazione.significativo_dopo IS 'Significatività dopo il cambiamento.';
COMMENT ON COLUMN storico_riclassificazione.voci_prima IS 'Codici delle voci prima, separati da virgola.';
COMMENT ON COLUMN storico_riclassificazione.voci_dopo IS 'Codici delle voci dopo, separati da virgola.';
COMMENT ON COLUMN storico_riclassificazione.motivazione IS 'Motivazione del cambiamento.';
COMMENT ON COLUMN storico_riclassificazione.registrata_il IS 'Momento della riclassificazione.';
COMMENT ON COLUMN storico_riclassificazione.registrata_da IS 'Utente che l''ha registrata.';
COMMENT ON COLUMN storico_riclassificazione.categorie_is_prima IS 'Categorie IS prima, separate da virgola.';
COMMENT ON COLUMN storico_riclassificazione.categorie_is_dopo IS 'Categorie IS dopo, separate da virgola.';
COMMENT ON COLUMN storico_riclassificazione.valutazione_id IS 'Nuova valutazione che ha motivato il cambio di significatività, se c''è stata.';
COMMENT ON COLUMN storico_riclassificazione.stato_incidente_prima IS 'Stato dell''incidente prima.';
COMMENT ON COLUMN storico_riclassificazione.stato_incidente_dopo IS 'Stato dell''incidente dopo (es. DECLASSATO).';

-- audit_log -----------------------------------------------------------------------------------------
COMMENT ON TABLE audit_log IS 'Registro delle operazioni: solo inserimenti; su PostgreSQL un trigger impedisce modifiche e cancellazioni.';
COMMENT ON COLUMN audit_log.id IS 'Identificativo.';
COMMENT ON COLUMN audit_log.istante IS 'Istante dell''operazione.';
COMMENT ON COLUMN audit_log.username IS 'Utente che ha eseguito l''operazione (o "sistema").';
COMMENT ON COLUMN audit_log.azione IS 'Codice dell''operazione (es. TRASMISSIONE, RICLASSIFICAZIONE, ACCESSO_FALLITO).';
COMMENT ON COLUMN audit_log.entita IS 'Tipo di entità coinvolta.';
COMMENT ON COLUMN audit_log.entita_id IS 'Identificativo dell''entità coinvolta.';
COMMENT ON COLUMN audit_log.dettaglio IS 'Descrizione leggibile dell''operazione.';
