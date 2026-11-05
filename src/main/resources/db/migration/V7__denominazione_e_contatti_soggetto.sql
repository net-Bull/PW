-- Nuova denominazione e nuovi contatti del soggetto fittizio del contesto.
-- Le migrazioni già applicate (V2, V4) non si modificano: Flyway ne verifica il checksum (ADR-0001).
-- I documenti già trasmessi conservano la denominazione dell'intestazione sigillata all'invio (ADR-0006).
UPDATE soggetto
SET denominazione = 'CyberWall S.r.l.',
    referente_email = 'csirt@cyberwall.example',
    referente_telefono = '+39 089 84 80 90'
WHERE codice_fiscale = '00000000000';
