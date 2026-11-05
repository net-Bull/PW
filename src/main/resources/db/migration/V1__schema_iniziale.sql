-- Schema iniziale: tassonomia ACN versionata e soglie di significatività versionate.
-- Le entità JPA sono validate contro questo DDL (spring.jpa.hibernate.ddl-auto=validate),
-- così l'ER diagram generato dal DDL coincide con il modello applicativo.

CREATE TABLE tassonomia_versione (
    id                  BIGINT        PRIMARY KEY,
    codice              VARCHAR(50)   NOT NULL UNIQUE,
    versione_documento  VARCHAR(20)   NOT NULL,
    data_acquisizione   DATE          NOT NULL,
    fonte_url           VARCHAR(500)  NOT NULL,
    fonte_strutturata   VARCHAR(500)  NOT NULL,
    sha256_documento    VARCHAR(64)   NOT NULL,
    vigente             BOOLEAN       NOT NULL DEFAULT FALSE
);

-- Albero della Tassonomia Cyber ACN: macrocategoria > predicato > (sotto-predicato) > valore.
-- Il sotto-predicato esiste solo sotto "Involved Asset".
CREATE TABLE tassonomia_voce (
    id               BIGINT         PRIMARY KEY,
    versione_id      BIGINT         NOT NULL REFERENCES tassonomia_versione (id),
    padre_id         BIGINT         REFERENCES tassonomia_voce (id),
    livello          VARCHAR(20)    NOT NULL,
    codice           VARCHAR(40)    NOT NULL,
    slug_misp        VARCHAR(120),
    nome             VARCHAR(255)   NOT NULL,
    descrizione      VARCHAR(2000),
    sezione          VARCHAR(20),
    ordine           INTEGER        NOT NULL,
    CONSTRAINT uq_voce_codice_versione UNIQUE (versione_id, codice),
    CONSTRAINT ck_voce_livello CHECK (livello IN ('MACROCATEGORIA', 'PREDICATO', 'SOTTOPREDICATO', 'VALORE'))
);

CREATE INDEX ix_voce_versione_livello ON tassonomia_voce (versione_id, livello);

CREATE TABLE soglia_set (
    id                        BIGINT         PRIMARY KEY,
    versione                  VARCHAR(50)    NOT NULL UNIQUE,
    valida_dal                DATE           NOT NULL,
    fonte                     VARCHAR(500)   NOT NULL,
    utenti_ue                 INTEGER        NOT NULL,
    fatturato_annuo           NUMERIC(15, 2) NOT NULL,
    minuti_indisponibilita    INTEGER        NOT NULL,
    minuti_degrado            INTEGER        NOT NULL,
    quota_utenti              NUMERIC(5, 4)  NOT NULL,
    tetto_utenti              INTEGER        NOT NULL,
    perdita_assoluta          NUMERIC(15, 2) NOT NULL,
    quota_fatturato           NUMERIC(5, 4)  NOT NULL,
    finestra_ricorrenza_mesi  INTEGER        NOT NULL,
    min_incidenti_ricorrenza  INTEGER        NOT NULL
);
