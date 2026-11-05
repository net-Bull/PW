-- Immutabilità imposta anche dal database (solo PostgreSQL, location db/vendor/{vendor}).
-- L'applicazione la impone già (metodi di dominio e callback JPA) e la catena di hash rileva le
-- manomissioni (ADR-0006); i trigger le impediscono anche a chi scrive in SQL con l'utente
-- applicativo. Restano possibili solo per chi può disattivare i trigger (proprietario delle tabelle
-- o superutente): per questo la rilevazione con la catena resta necessaria.

CREATE OR REPLACE FUNCTION pw29_blocca_versione_trasmessa() RETURNS trigger AS $$
BEGIN
    IF OLD.stato = 'TRASMESSO' THEN
        RAISE EXCEPTION 'Versione % trasmessa al CSIRT Italia: % non ammesso', OLD.id, TG_OP
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_versione_immutabile
    BEFORE UPDATE OR DELETE ON versione_documento
    FOR EACH ROW EXECUTE FUNCTION pw29_blocca_versione_trasmessa();

-- Classificazione e autori di una versione trasmessa: nessun inserimento, modifica o cancellazione.
CREATE OR REPLACE FUNCTION pw29_blocca_figli_versione_trasmessa() RETURNS trigger AS $$
DECLARE
    v_id BIGINT;
    v_stato VARCHAR(20);
BEGIN
    IF TG_OP = 'INSERT' THEN
        v_id := NEW.versione_documento_id;
    ELSE
        v_id := OLD.versione_documento_id;
    END IF;
    SELECT stato INTO v_stato FROM versione_documento WHERE id = v_id;
    IF v_stato = 'TRASMESSO' THEN
        RAISE EXCEPTION 'Versione % trasmessa al CSIRT Italia: % su % non ammesso', v_id, TG_OP, TG_TABLE_NAME
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_classificazione_immutabile
    BEFORE INSERT OR UPDATE OR DELETE ON classificazione
    FOR EACH ROW EXECUTE FUNCTION pw29_blocca_figli_versione_trasmessa();

CREATE TRIGGER tr_versione_autore_immutabile
    BEFORE INSERT OR UPDATE OR DELETE ON versione_autore
    FOR EACH ROW EXECUTE FUNCTION pw29_blocca_figli_versione_trasmessa();

-- Registro di audit: solo inserimenti.
CREATE OR REPLACE FUNCTION pw29_audit_solo_inserimento() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Registro di audit: % non ammesso', TG_OP USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tr_audit_solo_inserimento
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION pw29_audit_solo_inserimento();
