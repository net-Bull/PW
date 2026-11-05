-- Soglie di significatività per l'MSSP fittizio del progetto.
-- Parametri del contesto: 9.000 utenti nell'UE, fatturato annuo 7.000.000 EUR.
-- Soglie calcolate: utenti = min(5% x 9.000; 1.000.000) = 450; economica = min(500.000; 5% x 7.000.000) = 350.000.
-- Fonte: Reg. di esecuzione (UE) 2024/2690, artt. 3, 4 e 10 (testo su EUR-Lex).

INSERT INTO soglia_set (id, versione, valida_dal, fonte, utenti_ue, fatturato_annuo,
                        minuti_indisponibilita, minuti_degrado, quota_utenti, tetto_utenti,
                        perdita_assoluta, quota_fatturato, finestra_ricorrenza_mesi, min_incidenti_ricorrenza)
VALUES (1, 'MSSP-2024-2690-v1', DATE '2024-10-18',
        'Reg. di esecuzione (UE) 2024/2690, artt. 3, 4, 10',
        9000, 7000000.00,
        30, 60, 0.0500, 1000000,
        500000.00, 0.0500, 6, 2);
